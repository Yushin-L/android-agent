import dev.androidagent.probe.*;
import org.json.*;
import java.util.*;

public class SessionControllerTest {
 static class Wire implements SessionController.Transport {
  SessionController controller;int next=0;boolean instant;String stopped="";JSONObject last;List<String> prompts=new ArrayList<>();
  public JSONObject call(String method,JSONObject p)throws Exception {
   if(method.equals("thread/start"))return new JSONObject().put("thread",new JSONObject().put("id","s"+(++next)).put("turns",new JSONArray()));
   String id=p.getString("threadId");
   if(method.equals("thread/resume"))return new JSONObject().put("thread",new JSONObject().put("id",id).put("turns",new JSONArray().put(new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id","old").put("type","agentMessage").put("text","Codex history"))))));
   if(method.equals("turn/interrupt")){stopped=id+":"+p.getString("turnId");return new JSONObject();}
   last=new JSONObject(p.toString());prompts.add(p.getJSONArray("input").getJSONObject(0).getString("text"));
   JSONObject turn=new JSONObject().put("id","t-"+id).put("status",instant?"completed":"inProgress");
   controller.notification("turn/started",new JSONObject().put("threadId",id).put("turn",turn));
   if(instant)controller.notification("turn/completed",new JSONObject().put("threadId",id).put("turn",turn));
   return new JSONObject().put("turn",turn);
  }
 }
 interface Checked{void run()throws Exception;}
 static void reject(Checked c)throws Exception{try{c.run();throw new AssertionError();}catch(java.io.IOException expected){}}
 static void check(boolean c){if(!c)throw new AssertionError();}
 public static void main(String[]args)throws Exception{
  Wire w=new Wire();SessionController c=new SessionController(w,"/workspace",()->{});w.controller=c;
  String a=c.create(),b=c.create(),d=c.create();c.send(a,"alpha");c.send(b,"beta");
  reject(()->c.send(d,"third"));reject(()->c.send(a,"duplicate"));reject(()->c.send(d,"/new"));
  c.notification("item/agentMessage/delta",new JSONObject().put("threadId",a).put("turnId","t-"+a).put("itemId","msg").put("delta","A"));
  c.notification("item/agentMessage/delta",new JSONObject().put("threadId",b).put("turnId","stale-turn").put("itemId","msg").put("delta","WRONG"));
  c.notification("item/agentMessage/delta",new JSONObject().put("threadId","child-agent").put("turnId","t-x").put("itemId","msg").put("delta","HIDDEN"));
  check(c.items(a).getJSONObject(0).getString("text").equals("A"));check(c.items(b).length()==0);
  c.stop(a);check(w.stopped.equals(a+":t-"+a));check(c.busy(b));
  c.notification("turn/completed",new JSONObject().put("threadId",a).put("turn",new JSONObject().put("id","t-"+a).put("status","interrupted")));
  check(!c.busy(a));check(c.busy(b));
  w.instant=true;c.send(d,"fast",new JSONObject().put("model","model-b").put("effort","high"));check(w.last.getString("model").equals("model-b"));check(w.last.getString("effort").equals("high"));check(!c.busy(d));check(c.status(d).equals("completed"));
  c.send(d,"default reset",new JSONObject().put("model","model-a").put("effort","medium"));check(w.last.getString("model").equals("model-a"));reject(()->c.send(d,"/model"));
  JSONObject p=new JSONObject().put("threadId",b).put("turnId","t-"+b).put("callId","call").put("tool","android_battery_status").put("arguments",new JSONObject());
  check(c.battery(p,()->new JSONObject().put("batteryPercent",55).put("charging",false)).getBoolean("success"));
  reject(()->c.battery(p,()->new JSONObject()));
  c.disconnected();check(!c.busy(b));c.load(b);check(c.items(b).getJSONObject(0).getString("text").equals("Codex history"));
  System.out.println("PASS per-session streaming, stale/child isolation, selected-turn interruption, concurrency limit, early completion, tool deduplication, Codex hydration");
 }
}
