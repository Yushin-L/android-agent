package dev.androidagent.probe;

import android.content.Context;
import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

/** Dedicated trust store, no global TLS override and no GitHub credential on device. */
final class FeedbackClient {
    private final Context context;
    FeedbackClient(Context context){this.context=context;}
    boolean connected(){return !context.getSharedPreferences("feedback",0).getString("token","").isEmpty();}
    JSONObject post(String action,JSONObject body)throws Exception{
        KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);
        try(InputStream in=context.getAssets().open("feedback-server.crt")){store.setCertificateEntry("feedback",CertificateFactory.getInstance("X.509").generateCertificate(in));}
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,trust.getTrustManagers(),null);
        HttpsURLConnection connection=(HttpsURLConnection)new URL("https://android-agent.140.245.79.96.sslip.io:80/android-agent-feedback/"+action).openConnection();
        connection.setSSLSocketFactory(tls.getSocketFactory());connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(35000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
        String token=context.getSharedPreferences("feedback",0).getString("token","");if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
        try{byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            int code=connection.getResponseCode();InputStream source=code==200?connection.getInputStream():connection.getErrorStream();if(source==null)throw new IOException("FEEDBACK_HTTP_"+code);
            ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream in=source){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(data.size()+n>524288)throw new IOException("FEEDBACK_RESPONSE_SIZE");data.write(buffer,0,n);}}
            JSONObject result=new JSONObject(new String(data.toByteArray(),StandardCharsets.UTF_8));if(code!=200)throw new IOException(result.optString("error","FEEDBACK_HTTP_"+code));return result;
        }finally{connection.disconnect();}
    }
    void pair(String code)throws Exception{String token=post("pair",new JSONObject().put("code",code.trim())).getString("token");if(token.length()<32)throw new IOException("INVALID_PAIR_RECEIPT");if(!context.getSharedPreferences("feedback",0).edit().putString("token",token).commit())throw new IOException("TOKEN_SAVE_FAILED");}
    void disconnect()throws Exception{post("revoke",new JSONObject());context.getSharedPreferences("feedback",0).edit().clear().commit();}
    JSONObject invoke(String tool,JSONObject args,String requestId)throws Exception{
        if(!connected())throw new IOException("FEEDBACK_NOT_CONNECTED_OPEN_SETTINGS");
        JSONObject body=new JSONObject(args.toString());String action;
        if(tool.equals("submit_app_feedback")){action="submit";body.put("requestId",requestId).put("appVersion",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName).put("androidVersion",android.os.Build.VERSION.RELEASE);}
        else if(tool.equals("reply_app_feedback")){action="reply";body.put("requestId",requestId);}
        else action=tool.equals("read_app_feedback")?"read":"list";
        JSONObject result=post(action,body);
        if((action.equals("submit")||action.equals("reply"))&&(!"accepted".equals(result.optString("status"))||result.optLong("feedbackId")<1))throw new IOException("INVALID_FEEDBACK_RECEIPT");return result;
    }
}
