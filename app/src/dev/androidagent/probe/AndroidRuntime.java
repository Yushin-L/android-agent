package dev.androidagent.probe;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import javax.net.ssl.*;

/** The same Bionic executable, private credential home and Android CA roots proven by the probe. */
public final class AndroidRuntime {
    private final Context context;
    public final File root,home,workspace;
    public AndroidRuntime(Context context) {
        this.context=context.getApplicationContext();root=new File(context.getFilesDir(),"online-runtime");
        home=new File(root,"codex-home");workspace=new File(root,"workspace");
    }
    public Process start() throws Exception {
        Files.createDirectories(home.toPath());Files.createDirectories(workspace.toPath());
        File config=new File(home,"config.toml");
        if(!config.exists())Files.write(config.toPath(),("cli_auth_credentials_store = \"file\"\nweb_search = \"disabled\"\n[analytics]\nenabled = false\n[feedback]\nenabled = false\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder pem=new StringBuilder();
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init((java.security.KeyStore)null);
        for(TrustManager manager:factory.getTrustManagers())if(manager instanceof X509TrustManager)
            for(java.security.cert.X509Certificate cert:((X509TrustManager)manager).getAcceptedIssuers())
                pem.append("-----BEGIN CERTIFICATE-----\n").append(Base64.encodeToString(cert.getEncoded(),Base64.NO_WRAP)).append("\n-----END CERTIFICATE-----\n");
        if(pem.length()==0)throw new IOException("NO_ANDROID_CERTIFICATES");
        File ca=new File(root,"android-trust.pem");Files.write(ca.toPath(),pem.toString().getBytes(StandardCharsets.US_ASCII));
        String nativeDir=context.getApplicationInfo().nativeLibraryDir;
        String binary=new File(nativeDir,"libcodex.so").getAbsolutePath();
        ProcessBuilder builder=new ProcessBuilder(binary,"app-server","--listen","stdio://","-c","features.shell_tool=true","-c","web_search=\"disabled\"").directory(workspace);
        Map<String,String> env=builder.environment();env.clear();
        env.put("HOME",root.getAbsolutePath());env.put("CODEX_HOME",home.getAbsolutePath());
        env.put("SHELL","/system/bin/sh");
        env.put("TMPDIR",context.getCacheDir().getAbsolutePath());env.put("PATH","/system/bin:/system/xbin");
        env.put("CODEX_SELF_EXE",binary);env.put("LD_LIBRARY_PATH",nativeDir);env.put("LANG","C.UTF-8");
        env.put("ANDROID_ROOT","/system");env.put("ANDROID_DATA","/data");
        env.put("SSL_CERT_FILE",ca.getAbsolutePath());env.put("CODEX_CA_CERTIFICATE",ca.getAbsolutePath());
        return builder.start();
    }
    public JSONObject battery() throws Exception {
        Intent intent=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(intent==null)throw new IOException("BATTERY_UNAVAILABLE");
        int level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        if(level<0||scale<=0)throw new IOException("BATTERY_UNAVAILABLE");
        int status=intent.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        return new JSONObject().put("batteryPercent",Math.round(level*100f/scale)).put("charging",
            status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL);
    }
}
