package dev.androidagent.probe;
import org.json.*;
import java.io.IOException;

/** Resolve catalog defaults explicitly, so resumed threads cannot retain an old override. */
public final class ModelSelection {
    public static JSONObject resolve(JSONArray models,String model,String effort) throws Exception {
        JSONObject selected=null;
        for(int i=0;i<models.length();i++){
            JSONObject entry=models.getJSONObject(i);
            if(entry.optBoolean("hidden"))continue;
            if(model.isEmpty()?entry.optBoolean("isDefault"):model.equals(entry.optString("model"))){selected=entry;break;}
        }
        if(selected==null)throw new IOException("MODEL_UNAVAILABLE");
        String resolvedEffort=effort.isEmpty()?selected.optString("defaultReasoningEffort"):effort;
        JSONArray supported=selected.optJSONArray("supportedReasoningEfforts");boolean valid=false;
        if(supported!=null)for(int i=0;i<supported.length();i++)if(resolvedEffort.equals(supported.getJSONObject(i).optString("reasoningEffort")))valid=true;
        if(!valid)throw new IOException("EFFORT_UNAVAILABLE");
        return new JSONObject().put("model",selected.getString("model")).put("effort",resolvedEffort);
    }
}
