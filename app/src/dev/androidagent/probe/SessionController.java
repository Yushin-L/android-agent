package dev.androidagent.probe;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Session execution and visible transcript cache; no transcript is written by the app. */
public final class SessionController {
    public interface Transport { JSONObject call(String method,JSONObject params) throws Exception; }
    public interface Changed { void changed(); }
    public static final class Session {
        public final String id;
        public String turn="", status="ready", error="";
        public boolean starting, running, loaded;
        private final LinkedHashMap<String,JSONObject> items=new LinkedHashMap<>();
        private final Set<String> calls=new HashSet<>();
        private Session(String id) {this.id=id;}
        public JSONArray snapshot() throws Exception {return new JSONArray(items.values());}
    }
    private final Transport transport;
    private final Changed changed;
    private final Map<String,Session> sessions=new HashMap<>();
    private final String cwd;
    private final Map<String,String> directories=new HashMap<>();
    public synchronized void directory(String id,String path){directories.put(id,path);}
    private synchronized String directory(String id){return directories.getOrDefault(id,cwd);}
    public SessionController(Transport transport,String cwd,Changed changed) {this.transport=transport;this.cwd=cwd;this.changed=changed;}
    public synchronized Session session(String id) {return sessions.computeIfAbsent(id,Session::new);}
    private JSONObject parameters(String path) throws Exception {
        return new JSONObject().put("cwd",path).put("sandbox","read-only").put("approvalPolicy","untrusted")
            .put("developerInstructions","You are an assistant running on the user's Android phone. Reply naturally in the user's language. "
                +"Use android_battery_status for fresh battery state, never invent phone observations. Use available workspace tools for local files and downloads. Paths are relative to the current workspace. Unsupported binary formats require a suitable parser; do not invent their contents. "
                +"When the user asks to delegate, use available subagent tools and summarize their results in the main conversation. "
                +"Do not expose internal tool receipts. Do not execute shell commands. Modify files only via available workspace tools. Files saved in this workspace can be opened, shared or exported by the user from the app. Never claim a download or write succeeded without its tool result.");
    }
    public String create() throws Exception {
        return create(cwd);
    }
    public String create(String path) throws Exception {
        JSONArray tools=WorkspaceFiles.specs();tools.put(BatteryTool.spec());
        JSONObject p=parameters(path).put("ephemeral",false).put("dynamicTools",tools);
        JSONObject thread=transport.call("thread/start",p).getJSONObject("thread");
        String id=thread.getString("id");directory(id,path);
        synchronized(this) {Session s=session(id);hydrate(s,thread);s.loaded=true;}
        changed.changed();return id;
    }
    public void load(String id) throws Exception {
        synchronized(this) {Session s=session(id);if(s.loaded||s.running||s.starting)return;}
        JSONObject thread=transport.call("thread/resume",parameters(directory(id)).put("threadId",id)).getJSONObject("thread");
        if(!id.equals(thread.getString("id")))throw new IOException("SESSION_MISMATCH");
        synchronized(this) {Session s=session(id);if(!s.running&&!s.starting){hydrate(s,thread);s.loaded=true;}}
        changed.changed();
    }
    private void hydrate(Session s,JSONObject thread) throws Exception {
        s.items.clear();JSONArray turns=thread.optJSONArray("turns");
        if(turns==null)return;
        for(int i=0;i<turns.length();i++) {
            JSONObject turn=turns.getJSONObject(i);JSONArray items=turn.optJSONArray("items");
            if(items!=null)for(int j=0;j<items.length();j++)put(s,items.getJSONObject(j));
        }
    }
    private void put(Session s,JSONObject item) throws Exception {
        String type=item.optString("type");
        if(type.equals("userMessage")||type.equals("agentMessage")||type.equals("dynamicToolCall")||type.equals("mcpToolCall"))
            s.items.put(item.getString("id"),new JSONObject(item.toString()));
    }
    public void send(String id,String text) throws Exception {send(id,text,new JSONObject());}
    public void send(String id,String text,JSONObject selection) throws Exception {
        send(id,text,selection,new JSONArray());
    }
    public void send(String id,String text,JSONObject selection,JSONArray attachments) throws Exception {
        if((text.trim().isEmpty()&&attachments.length()==0)||text.length()>8000)throw new IOException("INVALID_INPUT");
        if(text.trim().equals("/new")||text.trim().equals("/resume")||text.trim().equals("/model"))throw new IOException("LOCAL_COMMAND_ONLY");
        load(id);
        synchronized(this) {
            Session s=session(id);
            if(s.running||s.starting)throw new IOException("SESSION_BUSY");
            long active=sessions.values().stream().filter(v->v.running||v.starting).count();
            if(active>=2)throw new IOException("CONCURRENT_TURN_LIMIT");
            s.starting=true;s.turn="";s.error="";s.status="starting";s.calls.clear();
        }
        changed.changed();
        try {
            JSONArray content=new JSONArray();if(!text.isEmpty())content.put(new JSONObject().put("type","text").put("text",text));
            for(int i=0;i<attachments.length();i++){JSONObject a=attachments.getJSONObject(i);content.put(new JSONObject().put("type","text").put("text","Attached workspace file: "+a.getString("path")));if(a.optString("mime").startsWith("image/"))content.put(new JSONObject().put("type","localImage").put("path",a.getString("absolutePath")));}
            JSONObject request=new JSONObject().put("threadId",id).put("cwd",directory(id)).put("input",content);
            if(selection.has("model"))request.put("model",selection.getString("model"));
            if(selection.has("effort"))request.put("effort",selection.getString("effort"));
            JSONObject turn=transport.call("turn/start",request).getJSONObject("turn");
            synchronized(this) {
                Session s=session(id);String tid=turn.getString("id");
                if(!s.turn.isEmpty()&&!s.turn.equals(tid))throw new IOException("TURN_MISMATCH");
                s.turn=tid;
                // A fast completion notification may arrive before the RPC response.
                if(s.starting){s.starting=false;s.running=true;s.status="running";}
            }
        } catch(Exception e) {
            synchronized(this){Session s=session(id);s.starting=false;s.running=false;s.status="error";s.error="응답을 시작하지 못했습니다. 다시 시도해 주세요.";}
            throw e;
        } finally {changed.changed();}
    }
    public void stop(String id) throws Exception {
        String turn;
        synchronized(this){Session s=session(id);if(!s.running||s.turn.isEmpty())return;turn=s.turn;s.status="stopping";}
        changed.changed();
        try {transport.call("turn/interrupt",new JSONObject().put("threadId",id).put("turnId",turn));}
        catch(Exception e){synchronized(this){Session s=session(id);if(s.running)s.status="running";}changed.changed();throw e;}
    }
    public void notification(String method,JSONObject params) throws Exception {
        synchronized(this) {
            Session s=sessions.get(params.optString("threadId"));if(s==null)return;
            JSONObject turn=params.optJSONObject("turn");
            String incoming=turn!=null?turn.optString("id"):params.optString("turnId");
            if(incoming.isEmpty()||(!s.starting&&!s.running))return;
            if(s.turn.isEmpty()&&s.starting)s.turn=incoming;
            if(!s.turn.equals(incoming))return;
            if(method.equals("turn/started")){s.running=true;s.starting=false;s.status="running";}
            else if(method.equals("turn/completed")){
                s.running=false;s.starting=false;s.status=turn.optString("status","failed");
                if(s.status.equals("failed"))s.error="응답이 완료되지 않았습니다. 다시 시도할 수 있습니다.";
            } else if(method.equals("item/started")||method.equals("item/completed")) {
                put(s,params.getJSONObject("item"));
            } else if(method.equals("item/agentMessage/delta")) {
                String key=params.getString("itemId");JSONObject item=s.items.get(key);
                if(item==null){item=new JSONObject().put("id",key).put("type","agentMessage").put("text","");s.items.put(key,item);}
                String text=item.optString("text")+params.getString("delta");
                if(text.length()>1_000_000)throw new IOException("MESSAGE_LIMIT");item.put("text",text);
            }
        }
        changed.changed();
    }
    public synchronized void validateTool(JSONObject p)throws Exception{
        Session s=sessions.get(p.optString("threadId"));if(s==null||(!s.starting&&!s.running)||s.status.equals("stopping"))throw new IOException("INACTIVE_TOOL_SESSION");
        String turn=p.getString("turnId");if(s.turn.isEmpty()&&s.starting)s.turn=turn;
        if(!s.turn.equals(turn)||s.calls.size()>=32||!s.calls.add(p.getString("callId"))||!p.isNull("namespace"))throw new IOException("INVALID_TOOL_CALL");
    }
    public synchronized boolean toolCancelled(String id,String turn){Session s=sessions.get(id);return s==null||(!s.starting&&!s.running)||!s.turn.equals(turn)||s.status.equals("stopping");}
    public JSONObject battery(JSONObject p,BatteryTool.Reader reader) throws Exception {
        synchronized(this) {
            Session s=sessions.get(p.optString("threadId"));
            if(s==null||(!s.starting&&!s.running))throw new IOException("INACTIVE_TOOL_SESSION");
            String turn=p.getString("turnId");
            if(s.turn.isEmpty()&&s.starting)s.turn=turn;
            if(!s.turn.equals(turn)||s.calls.size()>=32||!s.calls.add(p.getString("callId")))throw new IOException("INVALID_TOOL_CALL");
            BatteryTool tool=new BatteryTool(reader);tool.threadId=s.id;tool.turnId=s.turn;
            return tool.handle("item/tool/call",p);
        }
    }
    public synchronized void disconnected() {
        for(Session s:sessions.values()) {s.loaded=false;if(s.running||s.starting){s.running=false;s.starting=false;s.status="disconnected";s.error="연결이 끊겼습니다. 대화를 다시 열어 기록을 확인해 주세요.";}}
        changed.changed();
    }
    public synchronized JSONArray items(String id) throws Exception {return session(id).snapshot();}
    public synchronized String status(String id) {return session(id).status;}
    public synchronized boolean busy(String id) {Session s=session(id);return s.running||s.starting;}
}
