package dev.androidagent.probe;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Execution receipts only. Codex remains the transcript owner; nothing here is replayable. */
public final class ExecutionJournal {
    private final Path path;
    private JSONObject records;
    public ExecutionJournal(Path path)throws Exception{
        this.path=path;
        records=Files.exists(path)?new JSONObject(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)):new JSONObject();
    }
    private void save(JSONObject next)throws Exception{
        Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),"runs-",".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(next.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);records=next;
        }finally{Files.deleteIfExists(temp);}
    }
    public synchronized JSONObject get(String session)throws Exception{
        JSONObject r=records.optJSONObject(session);return r==null?null:new JSONObject(r.toString());
    }
    public synchronized JSONArray all()throws Exception{
        JSONArray result=new JSONArray();for(java.util.Iterator<String> keys=records.keys();keys.hasNext();)result.put(get(keys.next()));return result;
    }
    private void put(String id,JSONObject record)throws Exception{
        JSONObject next=new JSONObject(records.toString());record.put("updatedAt",System.currentTimeMillis());next.put(id,record);save(next);
    }
    public synchronized void begin(String workspace,String session,String baseline)throws Exception{
        JSONObject old=records.optJSONObject(session);
        if(old!=null&&active(old.optString("phase")))throw new IOException("RUN_ALREADY_ACTIVE");
        put(session,new JSONObject().put("workspace",workspace).put("session",session).put("baseline",baseline)
            .put("turn","").put("phase","starting").put("tools",new JSONObject()));
    }
    public static boolean active(String phase){return phase.equals("starting")||phase.equals("running");}
    public static boolean terminal(String phase){return phase.equals("completed")||phase.equals("failed")||phase.equals("interrupted");}
    public synchronized void turn(String session,JSONObject turn)throws Exception{
        JSONObject r=get(session);if(r==null||!active(r.optString("phase")))return;
        String id=turn.optString("id");if(id.isEmpty()||id.equals(r.optString("baseline"))||(!r.optString("turn").isEmpty()&&!id.equals(r.optString("turn"))))return;
        String status=turn.optString("status");r.put("turn",id).put("phase",terminal(status)?status:"running");put(session,r);
    }
    public synchronized void tool(String session,String turn,String id,String type,boolean completed)throws Exception{
        JSONObject r=get(session);if(r==null||!active(r.optString("phase"))||turn.isEmpty()||id.isEmpty())return;
        if(turn.equals(r.optString("baseline"))||(!r.optString("turn").isEmpty()&&!turn.equals(r.optString("turn"))))return;
        JSONObject tools=r.getJSONObject("tools");
        // Bounded metadata; never store command arguments, tool contents, tokens or prompts.
        if(tools.length()>=128&&!tools.has(id))return;
        JSONObject previous=tools.optJSONObject(id);if(previous!=null&&previous.optString("phase").equals("returned"))return;
        tools.put(id,new JSONObject().put("type",type).put("phase",completed?"returned":"started"));
        r.put("turn",turn);put(session,r);
    }
    public synchronized void uncertain(String session)throws Exception{
        JSONObject r=get(session);if(r!=null&&active(r.optString("phase"))){r.put("phase","unknown");put(session,r);}
    }
    public synchronized void recoverInterrupted()throws Exception{
        JSONObject next=new JSONObject(records.toString());boolean dirty=false;
        for(java.util.Iterator<String> keys=next.keys();keys.hasNext();){String id=keys.next();JSONObject r=next.getJSONObject(id);if(active(r.optString("phase"))){r.put("phase","unknown");dirty=true;}}
        if(dirty)save(next);
    }
    /** Reconcile only this request, never an older successful turn. No transport writes. */
    public synchronized String reconcile(String session,JSONObject thread)throws Exception{
        JSONObject r=get(session);if(r==null)return "";
        if(!session.equals(thread.optString("id")))throw new IOException("SESSION_MISMATCH");
        if(!r.optString("phase").equals("unknown"))return r.optString("phase");
        JSONArray turns=thread.optJSONArray("turns");JSONObject match=null;
        if(turns!=null){
            String expected=r.optString("turn"),baseline=r.optString("baseline");
            boolean after=baseline.isEmpty();
            for(int i=0;i<turns.length();i++){
                JSONObject candidate=turns.getJSONObject(i);String id=candidate.optString("id");
                if(!expected.isEmpty()){if(expected.equals(id)){match=candidate;break;}}
                else if(after&&!id.isEmpty()){match=candidate;break;}
                if(id.equals(baseline))after=true;
            }
        }
        if(match!=null&&terminal(match.optString("status"))){r.put("turn",match.getString("id")).put("phase",match.getString("status"));put(session,r);}
        return r.optString("phase");
    }
    public synchronized void remove(String session)throws Exception{JSONObject next=new JSONObject(records.toString());next.remove(session);save(next);}
}
