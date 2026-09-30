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
        public boolean approval;
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
    public WorkspaceFiles files;
    private GeneratedImages generatedImages;
    public volatile String shellProbe="NOT_RUN";
    private final Map<String,Integer> fileJobs=new HashMap<>();
    private final ConcurrentMap<String,String> fileStatus=new ConcurrentHashMap<>();
    public String fileStatus(String id){return fileStatus.getOrDefault(id, "");}
    public SessionController sessions;
    public volatile String error="",loginUrl="",loginCode="",account="로그인 확인 중";
    private volatile String loginId="";
    private volatile boolean destroyed;
    @Override public void onCreate(){
        super.onCreate();runtime=new AndroidRuntime(this);
        try {files=new WorkspaceFiles(new java.io.File(getFilesDir(),"workspaces").toPath(),new java.io.File(getCacheDir(),"file-jobs").toPath());store=new WorkspaceStore(new java.io.File(getFilesDir(),"workspaces.json").toPath());}
        catch(Exception e){error="쓰레드 목록을 읽지 못했습니다. 기존 데이터는 보존했습니다.";}
        generatedImages=new GeneratedImages(files,runtime.home.toPath());
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
        try {connection.call("initialize",new JSONObject().put("clientInfo",new JSONObject().put("name","android_agent").put("version","0.8.1"))
            .put("capabilities",new JSONObject().put("experimentalApi",true)),20000);
        connection.notify("initialized",new JSONObject());probeShell(connection);return connection;
        } catch(Exception e){connection.close();connection=null;throw e;}
    }
    private void probeShell(AppServerConnection wire){
        java.nio.file.Path directory=null;
        try{
            directory=java.nio.file.Files.createTempDirectory(getFilesDir().toPath(),"shell-check-");
            JSONObject policy=new JSONObject().put("type","workspaceWrite").put("writableRoots",new JSONArray().put(directory.toString())).put("networkAccess",false).put("excludeTmpdirEnvVar",true).put("excludeSlashTmp",true);
            JSONObject response=wire.call("command/exec",new JSONObject().put("command",new JSONArray().put("/system/bin/sh").put("-c").put("printf shell-ok > first && cp first second && mv second final && cat final"))
                .put("cwd",directory.toString()).put("sandboxPolicy",policy).put("timeoutMs",5000),10000);
            shellProbe=response.optInt("exitCode",-1)==0&&java.nio.file.Files.exists(directory.resolve("final"))&&"shell-ok".equals(new String(java.nio.file.Files.readAllBytes(directory.resolve("final")),java.nio.charset.StandardCharsets.UTF_8))?"PASS_CREATE_COPY_MOVE":"FAILED_EXECUTION";
        }catch(Exception e){shellProbe="FAILED_"+(e.getMessage()!=null&&e.getMessage().matches("RPC_ERROR_[-0-9]+")?e.getMessage():"STARTUP");}
        finally{if(directory!=null)try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(directory)){for(java.nio.file.Path p:(Iterable<java.nio.file.Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){}changed();}
    }
    private void retainImages(String id)throws Exception{
        String owner=store.owner(id);JSONArray items=sessions.items(id);
        for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(!item.optString("type").equals("imageGeneration")||!item.optString("status").equals("completed")||item.has("workspaceFile"))continue;
            beginFiles(owner);try{sessions.imageFile(id,item.getString("id"),generatedImages.retain(owner,id,item),"");}catch(Exception e){sessions.imageFile(id,item.getString("id"),null,"생성 이미지 저장 경로를 확인하지 못했습니다");}finally{endFiles(owner);}
        }
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
    public JSONArray models() throws Exception {
        JSONArray result=new JSONArray();String cursor="";Set<String> seen=new HashSet<>();
        do {
            JSONObject params=new JSONObject().put("limit",100);if(!cursor.isEmpty())params.put("cursor",cursor);
            JSONObject page=rpc().call("model/list",params,30000);JSONArray entries=page.getJSONArray("data");
            for(int i=0;i<entries.length();i++)if(!entries.getJSONObject(i).optBoolean("hidden"))result.put(entries.getJSONObject(i));
            cursor=page.isNull("nextCursor")?"":page.optString("nextCursor");
            if(!cursor.isEmpty()&&!seen.add(cursor))throw new IOException("MODEL_CURSOR_LOOP");
        }while(!cursor.isEmpty());
        return result;
    }
    public void setModel(String workspace,String model,String effort) throws Exception {
        ModelSelection.resolve(models(),model,effort);store.setModel(workspace,model,effort);changed();
    }
    public void sendMessage(String workspace,String session,String text,JSONArray attachments) throws Exception {
        JSONObject w=store.get(workspace);JSONArray entries=w.getJSONArray("sessions");boolean owned=false;
        for(int i=0;i<entries.length();i++)if(session.equals(entries.getJSONObject(i).getString("id")))owned=true;
        if(!owned)throw new IOException("SESSION_NOT_IN_WORKSPACE");
        JSONObject selection=ModelSelection.resolve(models(),w.optString("model"),w.optString("effort"));
        loadSession(session);
        JSONArray payload=new JSONArray();
        for(int i=0;i<attachments.length();i++){JSONObject a=attachments.getJSONObject(i);java.nio.file.Path path=files.resolve(workspace,a.getString("path"));if(!java.nio.file.Files.isRegularFile(path))throw new IOException("ATTACHMENT_MISSING");payload.put(new JSONObject(a.toString()).put("absolutePath",imageInput(workspace,path,a.optString("mime"))));}
        sessions.send(session,text,selection,payload);store.consumeAttachments(workspace,session,attachments);
    }
    private String imageInput(String owner,java.nio.file.Path path,String mime)throws Exception{
        if(!mime.startsWith("image/")||mime.equals("image/jpeg")||mime.equals("image/png")||mime.equals("image/webp")||mime.equals("image/gif"))return path.toString();
        java.io.File converted=new java.io.File(getCacheDir(),"image-"+java.util.UUID.randomUUID()+".jpg");
        android.graphics.Bitmap bitmap=android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(path.toFile()),(decoder,info,source)->{
            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);int width=info.getSize().getWidth(),height=info.getSize().getHeight();float scale=Math.min(1f,4096f/Math.max(width,height));decoder.setTargetSize(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));
        });
        try{
            try(java.io.OutputStream out=new java.io.FileOutputStream(converted)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,out))throw new IOException("IMAGE_CONVERSION_FAILED");}finally{bitmap.recycle();}
            try(java.io.InputStream in=new java.io.FileInputStream(converted)){JSONObject item=files.copyIn(owner,path.getFileName().toString()+".jpg",in,()->destroyed);return files.resolve(owner,item.getString("path")).toString();}
        }finally{converted.delete();}
    }
    public String createWorkspace(String name) throws Exception {
        String id=store.create(name);files.root(id);String session=sessions.create(files.root(id).toString());store.attach(id,session);return id;
    }
    public String newSession(String workspace) throws Exception {store.get(workspace);String id=sessions.create(files.root(workspace).toString());store.attach(workspace,id);return id;}
    public synchronized void deleteWorkspace(String id) throws Exception {
        JSONArray entries=store.get(id).getJSONArray("sessions");
        for(int i=0;i<entries.length();i++)if(sessions.busy(entries.getJSONObject(i).getString("id")))throw new IOException("WORKSPACE_BUSY");
        if(fileJobs.getOrDefault(id,0)>0)throw new IOException("WORKSPACE_BUSY");
        files.delete(id);store.delete(id);changed();
    }
    public void loadSession(String id)throws Exception{String owner=store.owner(id);sessions.directory(id,files.root(owner).toString());sessions.load(id);retainImages(id);}
    private synchronized void beginFiles(String owner)throws Exception{store.get(owner);fileJobs.put(owner,fileJobs.getOrDefault(owner,0)+1);}
    private synchronized void endFiles(String owner){int n=fileJobs.getOrDefault(owner,1)-1;if(n==0)fileJobs.remove(owner);else fileJobs.put(owner,n);}
    private JSONObject toolResult(boolean success,JSONObject result)throws Exception{return new JSONObject().put("success",success).put("contentItems",new JSONArray().put(new JSONObject().put("type","inputText").put("text",result.toString())));}
    public void importFiles(String owner,String id,java.util.List<android.net.Uri> uris,java.util.function.BooleanSupplier cancelled)throws Exception{
        if(!owner.equals(store.owner(id)))throw new IOException("SESSION_NOT_IN_WORKSPACE");beginFiles(owner);
        try {for(android.net.Uri uri:uris){
            if(cancelled.getAsBoolean())throw new IOException("CANCELLED");
            JSONArray current=store.attachments(owner,id);if(current.length()>=10)throw new IOException("ATTACHMENT_LIMIT");
            String name="file",mime=getContentResolver().getType(uri);
            try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("FILE_UNAVAILABLE");JSONObject item=files.copyIn(owner,name,in,cancelled).put("mime",mime==null?WorkspaceProvider.mime(name):mime);
                store.appendAttachment(owner,id,item);
            }
        }}finally{endFiles(owner);}
    }
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
                if(method.equals("item/completed")&&params.optJSONObject("item")!=null&&params.getJSONObject("item").optString("type").equals("imageGeneration")){String thread=params.optString("threadId");submit(()->retainImages(thread),e->{});}
                if(method.equals("turn/completed")){String thread=params.optString("threadId");for(Question q:questions.values())if(q.thread.equals(thread))q.answer.complete(q.approval?new JSONObject().put("decision","cancel"):new JSONObject().put("answers",new JSONObject()));}
            }
        }catch(Exception e){error="응답 상태를 읽지 못했습니다. 대화를 다시 열어 주세요.";changed();}
    }
    @Override public JSONObject request(String method,JSONObject params) throws Exception {
        if(method.equals("item/tool/call")){
            if("android_battery_status".equals(params.optString("tool")))return sessions.battery(params,runtime::battery);
            sessions.validateTool(params);String id=params.getString("threadId"),turn=params.getString("turnId"),owner=store.owner(id);beginFiles(owner);
            try {
                fileStatus.put(id,params.optString("tool").equals("workspace_download")?"파일 다운로드 중… · 중단 버튼으로 취소":"파일 작업 중…");changed();
                JSONObject result=files.invoke(owner,params.getString("tool"),params.getJSONObject("arguments"),()->sessions.toolCancelled(id,turn));
                return toolResult(true,result);
            }catch(Exception e){String code=e.getMessage();if(code==null||!code.matches("[A-Z_0-9]+"))code="FILE_OPERATION_FAILED";return toolResult(false,new JSONObject().put("error",code));}
            finally{fileStatus.remove(id);endFiles(owner);changed();}
        }
        String thread=params.optString("threadId");
        if(!sessions.busy(thread))throw new IOException("UNKNOWN_REQUEST_SESSION");
        if(method.equals("item/tool/requestUserInput")){
            Question q=new Question(UUID.randomUUID().toString(),thread,new JSONObject(params.toString()));questions.put(q.key,q);changed();
            try{return q.answer.get(10,TimeUnit.MINUTES);}finally{questions.remove(q.key);changed();}
        }
        if(method.equals("item/commandExecution/requestApproval")||method.equals("item/fileChange/requestApproval")){
            store.owner(thread);String turn=params.optString("turnId");if(!sessions.currentTurn(thread,turn))return new JSONObject().put("decision","cancel");
            JSONObject preview=new JSONObject(params.toString());JSONArray items=sessions.items(thread);for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.optString("id").equals(params.optString("itemId"))){if(!preview.has("command")&&item.has("command"))preview.put("command",item.get("command"));if(item.has("changes"))preview.put("changesPreview",item.getJSONArray("changes").toString(2));}}
            Question q=new Question(UUID.randomUUID().toString(),thread,preview);q.approval=true;questions.put(q.key,q);changed();
            try{JSONObject decision=q.answer.get(10,TimeUnit.MINUTES);return sessions.currentTurn(thread,turn)?decision:new JSONObject().put("decision","cancel");}finally{questions.remove(q.key);changed();}
        }
        throw new IOException("UNSUPPORTED_SERVER_REQUEST");
    }
    @Override public void disconnected(){
        if(sessions!=null)sessions.disconnected();
        for(Question q:questions.values())q.answer.completeExceptionally(new IOException("CONNECTION_CLOSED"));
        loginUrl="";loginCode="";loginId="";changed();
    }
    @Override public void onDestroy(){destroyed=true;listeners.clear();if(connection!=null)connection.close();worker.shutdownNow();super.onDestroy();}
}
