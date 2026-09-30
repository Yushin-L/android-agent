package dev.androidagent.probe;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import org.json.*;

/** Only workspace/session mappings and UI state. Transcript ownership stays with Codex. */
public final class WorkspaceStore {
    private final Path file;
    private JSONObject data;

    public WorkspaceStore(Path file) throws Exception {
        this.file = file;
        data = Files.exists(file) ? new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
            : new JSONObject().put("version", 1).put("workspaces", new JSONArray());
        if (data.getInt("version") != 1) throw new IOException("UNSUPPORTED_WORKSPACE_VERSION");
        data.getJSONArray("workspaces");
    }
    public synchronized JSONArray list() throws Exception {
        return new JSONArray(data.getJSONArray("workspaces").toString());
    }
    public synchronized JSONObject get(String id) throws Exception {
        return new JSONObject(find(id).toString());
    }
    public synchronized String create(String name) throws Exception {
        String title = name.trim();
        if (title.isEmpty() || title.length() > 120) throw new IOException("INVALID_WORKSPACE_NAME");
        String id = UUID.randomUUID().toString();
        mutate(() -> data.getJSONArray("workspaces").put(new JSONObject().put("id", id)
            .put("name", title).put("sessions", new JSONArray()).put("activeSession", "")
            .put("createdAt", System.currentTimeMillis())));
        return id;
    }
    /** Delete GUI metadata only; Codex owns and retains the underlying conversation history. */
    public synchronized void delete(String id) throws Exception {
        find(id);
        mutate(() -> {
            JSONArray old=data.getJSONArray("workspaces"), remaining=new JSONArray();
            for(int i=0;i<old.length();i++)if(!id.equals(old.getJSONObject(i).getString("id")))remaining.put(old.getJSONObject(i));
            data.put("workspaces",remaining);
        });
    }
    public synchronized void attach(String workspace, String session) throws Exception {
        if (session == null || session.isEmpty()) throw new IOException("INVALID_SESSION_ID");
        JSONArray all = data.getJSONArray("workspaces");
        for (int i=0; i<all.length(); i++) {
            JSONObject w=all.getJSONObject(i);
            if (!w.getString("id").equals(workspace) && session(w,session)!=null)
                throw new IOException("SESSION_ALREADY_OWNED");
        }
        JSONObject w=find(workspace);
        mutate(() -> {
            if (session(w,session)==null) w.getJSONArray("sessions").put(new JSONObject()
                .put("id",session).put("createdAt",System.currentTimeMillis()).put("draft", "").put("scrollY",0));
            w.put("activeSession",session);
        });
    }
    public synchronized void setModel(String workspace,String model,String effort) throws Exception {
        JSONObject w=find(workspace);
        if(model==null||effort==null||model.length()>200||effort.length()>40||(model.isEmpty()&&!effort.isEmpty()))throw new IOException("INVALID_MODEL_SELECTION");
        mutate(() -> w.put("model",model).put("effort",effort));
    }
    public synchronized void select(String workspace,String session) throws Exception {
        JSONObject w=find(workspace);
        if (session(w,session)==null) throw new IOException("SESSION_NOT_IN_WORKSPACE");
        mutate(() -> w.put("activeSession",session));
    }
    public synchronized void saveUi(String workspace,String session,String draft,int scrollY) throws Exception {
        JSONObject s=session(find(workspace),session);
        if(s==null) throw new IOException("SESSION_NOT_IN_WORKSPACE");
        if(draft.length()>8000) throw new IOException("DRAFT_TOO_LONG");
        mutate(() -> s.put("draft",draft).put("scrollY",Math.max(0,scrollY)));
    }
    public synchronized JSONArray attachments(String workspace,String id) throws Exception {
        JSONObject s=session(find(workspace),id);if(s==null)throw new IOException("SESSION_NOT_IN_WORKSPACE");
        return new JSONArray(s.optJSONArray("attachments")==null?"[]":s.getJSONArray("attachments").toString());
    }
    public synchronized void attachments(String workspace,String id,JSONArray values)throws Exception{
        JSONObject s=session(find(workspace),id);if(s==null)throw new IOException("SESSION_NOT_IN_WORKSPACE");
        if(values.length()>10)throw new IOException("ATTACHMENT_LIMIT");mutate(()->s.put("attachments",new JSONArray(values.toString())));
    }
    public synchronized void appendAttachment(String workspace,String id,JSONObject item)throws Exception{JSONArray a=attachments(workspace,id);a.put(item);attachments(workspace,id,a);}
    public synchronized void markDeletedPath(String workspace,String path)throws Exception{
        JSONObject w=find(workspace);JSONArray old=w.optJSONArray("deletedFiles");JSONArray values=old==null?new JSONArray():new JSONArray(old.toString());
        for(int i=0;i<values.length();i++)if(path.equals(values.getString(i)))return;
        values.put(path);mutate(()->w.put("deletedFiles",values));
    }
    public synchronized boolean deletedPath(String workspace,String path)throws Exception{
        JSONArray values=find(workspace).optJSONArray("deletedFiles");if(values==null)return false;
        for(int i=0;i<values.length();i++){String p=values.getString(i);if(path.equals(p)||path.startsWith(p+"/"))return true;}return false;
    }
    public synchronized void removeAttachmentPath(String workspace,String path)throws Exception{
        JSONObject w=get(workspace);JSONArray sessions=w.getJSONArray("sessions");
        for(int i=0;i<sessions.length();i++){String id=sessions.getJSONObject(i).getString("id");JSONArray entries=attachments(workspace,id),keep=new JSONArray();
            for(int j=0;j<entries.length();j++){JSONObject item=entries.getJSONObject(j);String p=item.optString("path");if(!p.equals(path)&&!p.startsWith(path+"/"))keep.put(item);}
            if(keep.length()!=entries.length())attachments(workspace,id,keep);
        }
    }
    public synchronized void consumeAttachments(String workspace,String id,JSONArray sent)throws Exception{
        java.util.Set<String> paths=new java.util.HashSet<>();for(int i=0;i<sent.length();i++)paths.add(sent.getJSONObject(i).getString("path"));
        JSONArray current=attachments(workspace,id),remaining=new JSONArray();for(int i=0;i<current.length();i++)if(!paths.contains(current.getJSONObject(i).getString("path")))remaining.put(current.getJSONObject(i));attachments(workspace,id,remaining);
    }
    public synchronized String owner(String id)throws Exception{
        JSONArray all=data.getJSONArray("workspaces");for(int i=0;i<all.length();i++){JSONObject w=all.getJSONObject(i);if(session(w,id)!=null)return w.getString("id");}throw new IOException("SESSION_NOT_IN_WORKSPACE");
    }
    private JSONObject find(String id) throws Exception {
        JSONArray a=data.getJSONArray("workspaces");
        for(int i=0;i<a.length();i++) if(id.equals(a.getJSONObject(i).getString("id"))) return a.getJSONObject(i);
        throw new IOException("WORKSPACE_NOT_FOUND");
    }
    private static JSONObject session(JSONObject w,String id) throws Exception {
        JSONArray a=w.getJSONArray("sessions");
        for(int i=0;i<a.length();i++) if(id.equals(a.getJSONObject(i).getString("id"))) return a.getJSONObject(i);
        return null;
    }
    private interface Change { void apply() throws Exception; }
    private void mutate(Change change) throws Exception {
        String before=data.toString();
        Path temp=null;
        try {
            change.apply();
            Path parent=file.toAbsolutePath().getParent(); Files.createDirectories(parent);
            temp=Files.createTempFile(parent,"workspace-",".tmp");
            try(FileOutputStream out=new FileOutputStream(temp.toFile())) {
                out.write(data.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
            }
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception e) { data=new JSONObject(before); throw e; }
        finally { if(temp!=null) Files.deleteIfExists(temp); }
    }
}
