import dev.androidagent.probe.*;
import org.json.*;
public class ModelSelectionTest {
 static JSONObject model(String name,String def,boolean isDefault,String...efforts)throws Exception{
  JSONArray supported=new JSONArray();for(String effort:efforts)supported.put(new JSONObject().put("reasoningEffort",effort));
  return new JSONObject().put("model",name).put("isDefault",isDefault).put("defaultReasoningEffort",def).put("supportedReasoningEfforts",supported);
 }
 public static void main(String[]args)throws Exception{
  JSONArray catalog=new JSONArray().put(model("model-a","medium",true,"low","medium","high")).put(model("model-b","low",false,"low","high"));
  JSONObject chosen=ModelSelection.resolve(catalog,"model-b","high");
  if(!chosen.getString("model").equals("model-b")||!chosen.getString("effort").equals("high"))throw new AssertionError();
  JSONObject reset=ModelSelection.resolve(catalog,"","");if(!reset.getString("model").equals("model-a")||!reset.getString("effort").equals("medium"))throw new AssertionError();
  for(String[] bad:new String[][]{{"missing",""},{"model-b","medium"}}){try{ModelSelection.resolve(catalog,bad[0],bad[1]);throw new AssertionError();}catch(java.io.IOException expected){}}
  catalog.getJSONObject(1).put("hidden",true);try{ModelSelection.resolve(catalog,"model-b","");throw new AssertionError();}catch(java.io.IOException expected){}
  System.out.println("PASS catalog default reset, model-specific effort validation, unavailable/hidden model rejection");
 }
}
