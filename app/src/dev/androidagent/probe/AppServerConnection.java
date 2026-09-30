package dev.androidagent.probe;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** A persistent JSONL connection. Reading never waits for UI approval or an RPC response. */
public final class AppServerConnection implements AutoCloseable {
    public interface Listener {
        void notification(String method, JSONObject params);
        JSONObject request(String method, JSONObject params) throws Exception;
        void disconnected();
    }
    private final Process process;
    private final Writer writer;
    private final Listener listener;
    private final AtomicLong sequence=new AtomicLong();
    private final ConcurrentMap<Long,CompletableFuture<JSONObject>> pending=new ConcurrentHashMap<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ExecutorService requests=new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(64), runnable -> {Thread t=new Thread(runnable,"codex-request");t.setDaemon(true);return t;},
        new ThreadPoolExecutor.AbortPolicy());
    public AppServerConnection(Process process,Listener listener) {
        this.process=process;this.listener=listener;
        writer=new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8);
        Thread stdout=new Thread(this::read,"codex-jsonl");stdout.setDaemon(true);stdout.start();
        Thread stderr=new Thread(() -> {
            // Drain without retaining potentially sensitive OAuth URLs or user text.
            try(InputStream in=process.getErrorStream()) {byte[] bytes=new byte[4096];while(in.read(bytes)!=-1) {}}
            catch(IOException ignored) {}
        },"codex-stderr");stderr.setDaemon(true);stderr.start();
    }
    public JSONObject call(String method,JSONObject params,long timeoutMs) throws Exception {
        long id=sequence.incrementAndGet();CompletableFuture<JSONObject> response=new CompletableFuture<>();
        pending.put(id,response);
        try {
            send(new JSONObject().put("id",id).put("method",method).put("params",params));
            return response.get(timeoutMs,TimeUnit.MILLISECONDS);
        } catch(ExecutionException e) {
            if(e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw e;
        } finally {pending.remove(id);}
    }
    public void notify(String method,JSONObject params) throws Exception {
        send(new JSONObject().put("method",method).put("params",params));
    }
    private synchronized void send(JSONObject value) throws Exception {
        if(closed.get()) throw new IOException("CONNECTION_CLOSED");
        writer.write(value.toString());writer.write('\n');writer.flush();
    }
    private void read() {
        try(Reader reader=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)) {
            StringBuilder line=new StringBuilder();char[] buffer=new char[8192];int count;
            while((count=reader.read(buffer))!=-1) for(int i=0;i<count;i++) {
                char ch=buffer[i];
                if(ch=='\n') {if(line.length()>0) dispatch(new JSONObject(line.toString()));line.setLength(0);}
                else {line.append(ch);if(line.length()>8_000_000) throw new IOException("RPC_MESSAGE_TOO_LARGE");}
            }
        } catch(Exception ignored) { /* The UI receives a sanitized disconnect state. */ }
        finally {close();}
    }
    private void dispatch(JSONObject message) throws Exception {
        if(!message.has("method")) {
            CompletableFuture<JSONObject> future=pending.get(message.optLong("id",-1));
            if(future==null) return; // Late responses after timeout cannot affect another caller.
            if(message.has("error")) future.completeExceptionally(new IOException("RPC_ERROR_"+message.getJSONObject("error").optInt("code")));
            else future.complete(message.getJSONObject("result"));
            return;
        }
        String method=message.getString("method");JSONObject params=message.optJSONObject("params");
        final JSONObject safeParams=params==null?new JSONObject():params;
        if(!message.has("id")) {listener.notification(method,safeParams);return;}
        final Object id=message.get("id");
        try {
            requests.execute(() -> {
                try {send(new JSONObject().put("id",id).put("result",listener.request(method,safeParams)));}
                catch(Exception e) {reject(id,-32602);}
            });
        } catch(RejectedExecutionException full) {reject(id,-32000);}
    }
    private void reject(Object id,int code) {
        try {send(new JSONObject().put("id",id).put("error",new JSONObject().put("code",code).put("message","Request unavailable")));}
        catch(Exception ignored) {}
    }
    public boolean isClosed() {return closed.get();}
    @Override public void close() {
        if(!closed.compareAndSet(false,true)) return;
        process.destroy();requests.shutdownNow();
        for(CompletableFuture<JSONObject> future:pending.values()) future.completeExceptionally(new IOException("CONNECTION_CLOSED"));
        pending.clear();
        listener.disconnected();
    }
}
