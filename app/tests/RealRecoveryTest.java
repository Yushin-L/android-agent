import dev.androidagent.probe.*;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.concurrent.TimeUnit;

/** Kill the real Linux child during an unresolved turn; read Codex before resume. */
public class RealRecoveryTest {
 public static void main(String[] args)throws Exception{
  String binary=System.getenv("CODEX_CONTROL_BINARY");if(binary==null){System.out.println("SKIP real crash test: no control binary");return;}
  Path root=Files.createTempDirectory("agent-crash-"),home=Files.createDirectory(root.resolve("home"));
  Path journalPath=root.resolve("runs.json");ExecutionJournal journal=new ExecutionJournal(journalPath);String id;
  try(ServerSocket silent=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
   Files.write(home.resolve("config.toml"),("model_provider = \"offline\"\nweb_search = \"disabled\"\n[model_providers.offline]\nname = \"Silent test server\"\nbase_url = \"http://127.0.0.1:"+silent.getLocalPort()+"/v1\"\nwire_api = \"responses\"\nrequires_openai_auth = false\nrequest_max_retries = 0\nstream_max_retries = 0\n[analytics]\nenabled = false\n[feedback]\nenabled = false\n").getBytes(StandardCharsets.UTF_8));
   try(RealSessionTest.Runtime runtime=new RealSessionTest.Runtime(root,home,binary)){
    id=runtime.controller.create();journal.begin("gui",id,"");runtime.controller.send(id,"crash recovery marker");
    journal.turn(id,new JSONObject().put("id",runtime.controller.turnId(id)).put("status","inProgress"));
    long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
    while(runtime.controller.items(id).length()==0&&System.nanoTime()<until)Thread.sleep(20);
    if(!runtime.controller.busy(id)||runtime.controller.items(id).length()==0)throw new AssertionError("test did not kill an active recorded turn");
    runtime.process.destroyForcibly();if(!runtime.process.waitFor(5,TimeUnit.SECONDS))throw new AssertionError("child survived");
   }
  }
  ExecutionJournal recovered=new ExecutionJournal(journalPath);recovered.recoverInterrupted();
  try(RealSessionTest.Runtime runtime=new RealSessionTest.Runtime(root,home,binary)){
   JSONObject thread=runtime.connection.call("thread/read",new JSONObject().put("threadId",id).put("includeTurns",true),10000).getJSONObject("thread");
   if(!thread.toString().contains("crash recovery marker"))throw new AssertionError("durable Codex input missing");
   String state=recovered.reconcile(id,thread);
   if(!state.equals("unknown")&&!state.equals("interrupted")&&!state.equals("failed"))throw new AssertionError("unfinished run reported completed: "+state);
   runtime.controller.load(id);
   if(runtime.controller.busy(id))throw new AssertionError("resume replayed the turn");
   JSONObject after=runtime.connection.call("thread/read",new JSONObject().put("threadId",id).put("includeTurns",true),10000).getJSONObject("thread");
   if(after.getJSONArray("turns").length()!=thread.getJSONArray("turns").length())throw new AssertionError("resume added a turn");
  }
  System.out.println("PASS actual Linux child kill during turn, read-before-resume, durable Codex input, unknown/interrupted recovery, no automatic turn replay; Android process death not tested");
 }
}
