package dev.androidagent.probe;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Single consumer, independent stdout/stderr drains, bounded queues; never logs credentials. */
public final class RpcClient implements AutoCloseable {
    private final Process process;
    private final Writer writer;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>(1024);
    private final ArrayDeque<JSONObject> pending = new ArrayDeque<>();
    private volatile boolean closed, overflow;
    public volatile boolean tlsHint, dnsHint, bubblewrapHint;
    public volatile String errorCategory = "UNCLASSIFIED";
    public interface RequestHandler { JSONObject handle(String method, JSONObject params) throws Exception; }
    public RequestHandler requestHandler;
    private int sequence;
    private static final String EOF = "<EOF>";
    public RpcClient(Process process) {
        this.process = process;
        writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        Thread stdout = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.length() > 2000000 || !lines.offer(line)) { overflow = true; process.destroy(); break; }
                }
            } catch (IOException ignored) { }
            finally { lines.offer(EOF); }
        }, "online-rpc-stdout");
        Thread stderr = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    tlsHint |= lower.contains("certificate") || lower.contains("tls");
                    dnsHint |= lower.contains("dns") || lower.contains("resolve host");
                    bubblewrapHint |= lower.contains("bubblewrap");
                }
            } catch (IOException ignored) { }
        }, "online-rpc-stderr");
        stdout.setDaemon(true); stderr.setDaemon(true); stdout.start(); stderr.start();
    }
    public synchronized void send(JSONObject obj) throws Exception {
        if (closed) throw new IOException("CLOSED");
        writer.write(obj.toString()+"\n"); writer.flush();
    }
    private JSONObject next(long deadline) throws Exception {
        while (!closed) {
            if (overflow) throw new IOException("QUEUE_OVERFLOW");
            long remaining = deadline-System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("RPC_TIMEOUT");
            String line = lines.poll(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(200)), TimeUnit.NANOSECONDS);
            if (EOF.equals(line)) throw new EOFException("PROCESS_EXITED");
            if (line == null) continue;
            JSONObject message = new JSONObject(line);
            if (message.has("method") && message.has("id")) {
                if(requestHandler!=null && "item/tool/call".equals(message.optString("method"))) {
                    try {
                        JSONObject result=requestHandler.handle(message.getString("method"),message.getJSONObject("params"));
                        send(new JSONObject().put("id",message.get("id")).put("result",result));
                    } catch(Exception e) {
                        send(new JSONObject().put("id",message.get("id")).put("error",new JSONObject().put("code",-32602).put("message","Tool request rejected")));
                        throw e;
                    }
                    continue;
                }
                send(new JSONObject().put("id", message.get("id")).put("error",
                    new JSONObject().put("code", -32601).put("message", "Interactive tools are not supported by this probe")));
                throw new IOException("UNEXPECTED_SERVER_REQUEST");
            }
            return message;
        }
        throw new IOException("CANCELLED");
    }
    public JSONObject call(String method, JSONObject params, long timeoutMs) throws Exception {
        int id = ++sequence;
        send(new JSONObject().put("id", id).put("method", method).put("params", params));
        long deadline = System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (true) {
            JSONObject message = next(deadline);
            if (!message.has("method") && message.optInt("id", -1) == id) {
                if (message.has("error")) {
                    // Error text can include login URLs/tokens; export only the numeric code.
                    JSONObject error=message.getJSONObject("error");
                    String detail=error.optString("message").toLowerCase(Locale.ROOT);
                    errorCategory=AuthDiagnostics.category(detail);
                    tlsHint |= detail.contains("certificate") || detail.contains("tls");
                    dnsHint |= detail.contains("dns") || detail.contains("resolve host") || detail.contains("name resolution");
                    throw new IOException("RPC_ERROR_"+error.optInt("code"));
                }
                return message.getJSONObject("result");
            }
            if (pending.size() >= 1024) throw new IOException("EVENT_OVERFLOW");
            pending.add(message);
        }
    }
    public JSONObject event(long deadlineNanos) throws Exception {
        return pending.isEmpty() ? next(deadlineNanos) : pending.remove();
    }
    public void close() {
        closed = true;
        process.destroy();
        try { if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly(); }
        catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
}
