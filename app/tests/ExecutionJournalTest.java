import dev.androidagent.probe.*;
import org.json.*;
import java.nio.file.*;

public class ExecutionJournalTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static JSONObject turn(String id,String status)throws Exception{return new JSONObject().put("id",id).put("status",status);}
 static JSONObject thread(String session,JSONObject...turns)throws Exception{return new JSONObject().put("id",session).put("turns",new JSONArray(turns));}
 public static void main(String[]args)throws Exception{
  Path root=Files.createTempDirectory("run-journal-");Path file=root.resolve("journal.json");
  ExecutionJournal j=new ExecutionJournal(file);j.begin("gui","s","old");
  j.turn("s",turn("old","completed"));check(j.get("s").getString("phase").equals("starting"));
  j.turn("s",turn("t","inProgress"));j.tool("s","t","call","workspace_write_text",false);
  ExecutionJournal restarted=new ExecutionJournal(file);restarted.recoverInterrupted();check(restarted.get("s").getString("phase").equals("unknown"));
  check(restarted.reconcile("s",thread("s",turn("old","completed"))).equals("unknown"));
  check(restarted.reconcile("s",thread("s",turn("t","inProgress"))).equals("unknown"));
  check(restarted.reconcile("s",thread("s",turn("t","completed"))).equals("completed"));
  check(restarted.get("s").getJSONObject("tools").getJSONObject("call").getString("phase").equals("started")); // Completion is not exactly-once tool proof.
  restarted.begin("gui","s","t");restarted.turn("s",turn("t2","completed"));restarted.turn("s",turn("t2","inProgress"));
  check(restarted.get("s").getString("phase").equals("completed")); // completion before RPC acknowledgement
  restarted.begin("gui","s","t2");restarted.uncertain("s");
  check(restarted.reconcile("s",thread("s",turn("t2","completed"))).equals("unknown"));
  check(restarted.reconcile("s",thread("s",turn("t2","completed"),turn("t3","interrupted"))).equals("interrupted")); // accepted, ack lost
  restarted.begin("gui","s","t3");restarted.turn("s",turn("t4","inProgress"));restarted.turn("s",turn("stale","completed"));
  check(restarted.get("s").getString("phase").equals("running"));
  restarted.tool("s","t4","cmd","commandExecution",true);restarted.tool("s","t4","cmd","commandExecution",false);
  check(restarted.get("s").getJSONObject("tools").getJSONObject("cmd").getString("phase").equals("returned"));
  // Rename failure preserves the old in-memory view, and never authorizes an unrecorded send.
  Path blocked=root.resolve("blocked");ExecutionJournal broken=new ExecutionJournal(blocked);Files.createDirectory(blocked);Files.write(blocked.resolve("keep"),new byte[]{1});
  try{broken.begin("gui","s","");throw new AssertionError();}catch(java.io.IOException expected){}
  check(broken.get("s")==null);
  Files.write(root.resolve("corrupt"),"not-json".getBytes());try{new ExecutionJournal(root.resolve("corrupt"));throw new AssertionError();}catch(JSONException expected){}
  restarted.remove("s");check(new ExecutionJournal(file).all().length()==0);
  System.out.println("PASS durable execution metadata, crash recovery, lost ack, stale turn, early completion, atomic rollback, corrupt journal preservation, no replay");
 }
}
