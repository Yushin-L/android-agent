package dev.androidagent.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Owns one app-server for all GUI workspaces. Screen navigation never stops a turn. */
public final class AgentService extends Service implements AppServerConnection.Listener {
    public final class LocalBinder extends Binder {public AgentService service(){return AgentService.this;}}
    public interface Task {void run() throws Exception;}
    public interface Result {void done(Exception error);}
    public static final class Question {
        public final String key,thread;public final JSONObject params;
        final CompletableFuture<JSONObject> answer=new CompletableFuture<>();
        Question(String key,String thread,JSONObject params){this.key=key;this.thread=thread;this.params=params;}
    }
    private final LocalBinder binder=new LocalBinder();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String,Question> questions=new ConcurrentHashMap<>();
    private AppServerConnection connection;
    private AndroidRuntime runtime;
    public WorkspaceStore store;
    public SessionController sessions;
    public volatile String error="",loginUrl="",loginCode="",account="로그인 확인 중";
    private volatile String loginId="";
    private volatile boolean destroyed;
    @Override public void onCreate(){
        super.onCreate();runtime=new AndroidRuntime(this);
        try {store=new WorkspaceStore(new java.io.File(getFilesDir(),"workspaces.json").toPath());}
        catch(Exception e){error="쓰레드 목록을 읽지 못했습니다. 기존 데이터는 보존했습니다.";}
        sessions=new SessionController((method,params)->rpc().call(method,params,45000),runtime.workspace.getAbsolutePath(),this::changed);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("agent", "진행 중인 대화",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,AgentActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(2,new Notification.Builder(this,"agent").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Android Agent").setContentText("대화 연결이 유지되고 있습니다").setContentIntent(open).build());
        return START_NOT_STICKY;
    }
    public void observe(Runnable listener){listeners.add(listener);}
    public void unobserve(Runnable listener){listeners.remove(listener);}
    public void changed(){for(Runnable listener:listeners)ui.post(listener);}
    public void submit(Task task,Result callback){
        worker.execute(()->{Exception failure=null;try{task.run();}catch(Exception e){failure=e;}
            final Exception result=failure;ui.post(()->{if(!destroyed){callback.done(result);changed();}});});
    }
    private synchronized AppServerConnection rpc() throws Exception {
        if(connection!=null&&!connection.isClosed())return connection;
        if(destroyed)throw new IOException("SERVICE_CLOSED");
        connection=new AppServerConnection(runtime.start(),this);
        try {connection.call("initialize",new JSONObject().put("clientInfo",new JSONObject().put("name","android_agent").put("version","0.7.0"))
            .put("capabilities",new JSONObject().put("experimentalApi",true)),20000);
        connection.notify("initialized",new JSONObject());return connection;
        } catch(Exception e){connection.close();connection=null;throw e;}
    }
    public void refreshAccount() throws Exception {
        JSONObject value=rpc().call("account/read",new JSONObject().put("refreshToken",false),15000).optJSONObject("account");
        account=value!=null&&"chatgpt".equals(value.optString("type"))?"ChatGPT 연결됨":"로그인이 필요합니다";changed();
    }
    public void login(boolean device) throws Exception {
        if(!loginId.isEmpty())rpc().call("account/login/cancel",new JSONObject().put("loginId",loginId),15000);
        JSONObject value=rpc().call("account/login/start",new JSONObject().put("type",device?"chatgptDeviceCode":"chatgpt"),45000);
        String candidate=value.getString(device?"verificationUrl":"authUrl");android.net.Uri uri=android.net.Uri.parse(candidate);
        if(!"https".equals(uri.getScheme())||!("auth.openai.com".equals(uri.getHost())||"chatgpt.com".equals(uri.getHost())))throw new IOException("INVALID_LOGIN_URL");
        loginId=value.getString("loginId");loginUrl=candidate;loginCode=device?value.getString("userCode"):"";changed();
    }
    public void logout() throws Exception {
        JSONArray all=store.list();
        for(int i=0;i<all.length();i++){JSONArray ids=all.getJSONObject(i).getJSONArray("sessions");for(int j=0;j<ids.length();j++)if(sessions.busy(ids.getJSONObject(j).getString("id")))throw new IOException("ACTIVE_TURNS");}
        rpc().call("account/logout",new JSONObject(),15000);loginUrl="";loginCode="";loginId="";refreshAccount();
    }
    public String createWorkspace(String name) throws Exception {
        String id=store.create(name);String session=sessions.create();store.attach(id,session);return id;
    }
    public String newSession(String workspace) throws Exception {String id=sessions.create();store.attach(workspace,id);return id;}
    public Question question(String session){for(Question q:questions.values())if(q.thread.equals(session))return q;return null;}
    public void answer(Question question,JSONObject response){question.answer.complete(response);changed();}
    @Override public void notification(String method,JSONObject params){
        try {
            if(method.equals("account/login/completed")){
                if(loginId.equals(params.optString("loginId"))){loginUrl="";loginCode="";loginId="";
                    if(params.optBoolean("success"))submit(this::refreshAccount,e->{if(e!=null)account="계정 확인 실패";});
                    else {account="로그인 실패 · 다시 시도해 주세요";changed();}}
            } else {
                sessions.notification(method,params);
                if(method.equals("turn/completed")){String thread=params.optString("threadId");for(Question q:questions.values())if(q.thread.equals(thread))q.answer.complete(new JSONObject().put("answers",new JSONObject()));}
            }
        }catch(Exception e){error="응답 상태를 읽지 못했습니다. 대화를 다시 열어 주세요.";changed();}
    }
    @Override public JSONObject request(String method,JSONObject params) throws Exception {
        if(method.equals("item/tool/call"))return sessions.battery(params,runtime::battery);
        String thread=params.optString("threadId");
        if(!sessions.busy(thread))throw new IOException("UNKNOWN_REQUEST_SESSION");
        if(method.equals("item/tool/requestUserInput")){
            Question q=new Question(UUID.randomUUID().toString(),thread,new JSONObject(params.toString()));questions.put(q.key,q);changed();
            try{return q.answer.get(10,TimeUnit.MINUTES);}finally{questions.remove(q.key);changed();}
        }
        // Shell/file writes are disabled. Never silently grant an unexpected approval.
        if(method.equals("item/commandExecution/requestApproval")||method.equals("item/fileChange/requestApproval"))return new JSONObject().put("decision","decline");
        throw new IOException("UNSUPPORTED_SERVER_REQUEST");
    }
    @Override public void disconnected(){
        if(sessions!=null)sessions.disconnected();
        for(Question q:questions.values())q.answer.completeExceptionally(new IOException("CONNECTION_CLOSED"));
        loginUrl="";loginCode="";loginId="";changed();
    }
    @Override public void onDestroy(){destroyed=true;listeners.clear();if(connection!=null)connection.close();worker.shutdownNow();super.onDestroy();}
}
