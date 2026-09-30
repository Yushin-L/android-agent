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
