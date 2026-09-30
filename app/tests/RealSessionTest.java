import dev.androidagent.probe.*;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Offline Linux control; never reads host credentials or calls a real model. */
public class RealSessionTest {
 static class Runtime implements AutoCloseable {
  AppServerConnection connection;SessionController controller;
  Runtime(Path root,Path home,String binary)throws Exception{
   ProcessBuilder builder=new ProcessBuilder(binary,"app-server","--listen","stdio://").directory(root.toFile());
   builder.environment().clear();builder.environment().put("HOME",root.toString());builder.environment().put("CODEX_HOME",home.toString());builder.environment().put("PATH","/usr/bin:/bin");
   AtomicReference<SessionController> state=new AtomicReference<>();
   connection=new AppServerConnection(builder.start(),new AppServerConnection.Listener(){
    public void notification(String method,JSONObject params){try{if(state.get()!=null)state.get().notification(method,params);}catch(Exception e){throw new RuntimeException(e);}}
    public JSONObject request(String method,JSONObject params)throws Exception{throw new java.io.IOException("unexpected offline tool");}
    public void disconnected(){if(state.get()!=null)state.get().disconnected();}
   });
   connection.call("initialize",new JSONObject().put("clientInfo",new JSONObject().put("name","android_agent_offline_test").put("version","0.7.0")).put("capabilities",new JSONObject().put("experimentalApi",true)),20000);
   connection.notify("initialized",new JSONObject());
   controller=new SessionController((method,params)->connection.call(method,params,20000),root.toString(),()->{});state.set(controller);
  }
  public void close(){connection.close();}
 }
 public static void main(String[]args)throws Exception{
  String binary=System.getenv("CODEX_CONTROL_BINARY");if(binary==null){System.out.println("SKIP real Linux control: CODEX_CONTROL_BINARY not set");return;}
  Path root=Files.createTempDirectory("agent-real-test-"),home=Files.createDirectory(root.resolve("home"));
  Files.write(home.resolve("config.toml"),("model_provider = \"offline\"\nweb_search = \"disabled\"\n[model_providers.offline]\nname = \"Offline test\"\nbase_url = \"http://127.0.0.1:9/v1\"\nwire_api = \"responses\"\nrequires_openai_auth = false\nrequest_max_retries = 0\nstream_max_retries = 0\n[features]\nshell_tool = false\n[analytics]\nenabled = false\n[feedback]\nenabled = false\n").getBytes(StandardCharsets.UTF_8));
  String id;Path workspace=Files.createDirectory(root.resolve("gui-workspace"));
  Path photo=workspace.resolve("pixel.png");Files.write(photo,java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="));
  try(Runtime runtime=new Runtime(root,home,binary)){
   if(!runtime.connection.call("account/read",new JSONObject(),10000).isNull("account"))throw new AssertionError("account not isolated");
   id=runtime.controller.create(workspace.toString());JSONArray models=runtime.connection.call("model/list",new JSONObject().put("limit",100),10000).getJSONArray("data");runtime.controller.send(id,"offline test message",ModelSelection.resolve(models,"",""),new JSONArray().put(new JSONObject().put("path","pixel.png").put("mime","image/png").put("absolutePath",photo.toString())));
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
   boolean interrupted=false;
   while(runtime.controller.busy(id)&&System.nanoTime()<deadline){
    if(!interrupted&&runtime.controller.items(id).length()>0){runtime.controller.stop(id);interrupted=true;}
    Thread.sleep(25);
   }
   if(runtime.controller.busy(id))throw new AssertionError("no terminal event");
   if(runtime.controller.items(id).length()==0)throw new AssertionError("user item not routed");
  }
  try(Runtime runtime=new Runtime(root,home,binary)){
   runtime.controller.directory(id,workspace.toString());runtime.controller.load(id);
   JSONObject thread=runtime.connection.call("thread/read",new JSONObject().put("threadId",id).put("includeTurns",false),10000).getJSONObject("thread");if(!thread.getString("cwd").equals(workspace.toString()))throw new AssertionError("cwd lost");
   JSONArray items=runtime.controller.items(id);
   if(!items.toString().contains("offline test message"))throw new AssertionError("Codex history not hydrated: "+items);
   if(runtime.controller.busy(id))throw new AssertionError("history became active turn");if(!items.toString().contains("pixel.png"))throw new AssertionError("attachment missing from history");
  }
  System.out.println("PASS actual Linux app-server create, interrupted offline turn, routed user item, restart/resume and transcript hydration; no Android or inference claim");
 }
}
