package dev.androidagent.probe;
import org.json.*;
import java.io.IOException;
import java.util.UUID;

/** One allowlisted read-only tool; a fresh receipt proves output reached the model. */
public final class BatteryTool implements RpcClient.RequestHandler {
 public interface Reader { JSONObject read() throws Exception; }
 public final Reader reader;
 public String threadId, turnId, observedTurnId;
 public int calls;
 public JSONObject snapshot;
 public String receipt;
 public BatteryTool(Reader reader){this.reader=reader;}
 public static JSONObject spec() throws Exception {
  return new JSONObject().put("type","function").put("name","android_battery_status")
   .put("description","Read this Android phone's battery percentage and charging state. Read-only, no arguments. Returns a one-time receipt that must be echoed with the values.")
   .put("inputSchema",new JSONObject().put("type","object").put("properties",new JSONObject()).put("required",new JSONArray()).put("additionalProperties",false));
 }
 public JSONObject handle(String method,JSONObject p) throws Exception {
  if(!"item/tool/call".equals(method)||!"android_battery_status".equals(p.optString("tool")) || !p.isNull("namespace"))throw new IOException("UNEXPECTED_TOOL");
  if(threadId==null||!threadId.equals(p.optString("threadId")))throw new IOException("TOOL_THREAD_MISMATCH");
  String incoming=p.getString("turnId");
  if(incoming.isEmpty()||(turnId!=null&&!turnId.equals(incoming)))throw new IOException("TOOL_TURN_MISMATCH");
  if(!(p.opt("arguments") instanceof JSONObject)||p.getJSONObject("arguments").length()!=0)throw new IOException("INVALID_TOOL_ARGUMENTS");
  if(calls!=0)throw new IOException("TOOL_CALL_LIMIT");
  snapshot=reader.read();receipt=UUID.randomUUID().toString();observedTurnId=incoming;calls++;
  JSONObject out=new JSONObject(snapshot.toString()).put("receipt",receipt);
  return new JSONObject().put("success",true).put("contentItems",new JSONArray().put(new JSONObject().put("type","inputText").put("text",out.toString())));
 }
 public boolean matches(String answer) throws Exception {
  if(calls!=1||turnId==null||!turnId.equals(observedTurnId))return false;
  JSONObject a=new JSONObject(answer);
  return a.length()==3 && receipt.equals(a.optString("receipt"))
   && a.opt("batteryPercent") instanceof Number && a.getDouble("batteryPercent")==snapshot.getInt("batteryPercent")
   && a.opt("charging") instanceof Boolean && a.getBoolean("charging")==snapshot.getBoolean("charging");
 }
}
