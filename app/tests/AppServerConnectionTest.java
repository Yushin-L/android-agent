import dev.androidagent.probe.AppServerConnection;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AppServerConnectionTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger events=new AtomicInteger(), closed=new AtomicInteger();
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try(AppServerConnection rpc=new AppServerConnection(new ProcessBuilder("python3","tests/connection_server.py").start(),new AppServerConnection.Listener() {
            public void notification(String m,JSONObject p) {if(m.equals("turn/completed")&&p.optString("threadId").equals("thread-a")) events.incrementAndGet();}
            public JSONObject request(String m,JSONObject p) throws Exception {
                if(!m.equals("item/tool/call")||!p.getString("turnId").equals("turn-a")) throw new AssertionError();
                return new JSONObject().put("success",true);
            }
            public void disconnected() {closed.incrementAndGet();}
        })) {
            Future<JSONObject> a=workers.submit(() -> rpc.call("parallel",new JSONObject().put("owner","a"),3000));
            Future<JSONObject> b=workers.submit(() -> rpc.call("parallel",new JSONObject().put("owner","b"),3000));
            if(!a.get().getString("owner").equals("a")||!b.get().getString("owner").equals("b")) throw new AssertionError("response crossed callers");
            if(!rpc.call("tool",new JSONObject(),3000).getBoolean("success")||events.get()!=1) throw new AssertionError("early server request deadlocked");
            try {rpc.call("failure",new JSONObject(),3000);throw new AssertionError();}
            catch(java.io.IOException expected) {if(!expected.getMessage().equals("RPC_ERROR_-32603")) throw new AssertionError("sensitive error leaked");}
            try {rpc.call("exit",new JSONObject(),3000);throw new AssertionError();}
            catch(java.io.IOException expected) {}
        } finally {workers.shutdownNow();}
        if(closed.get()!=1) throw new AssertionError("disconnect not exactly once");
        System.out.println("PASS concurrent out-of-order RPC, early server request, scoped notification, sanitized errors, process exit");
    }
}
