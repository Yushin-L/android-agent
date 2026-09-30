package dev.androidagent.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import java.util.*;

/** Foreground ownership, notifications and bounded CPU leases. No model requests. */
final class AgentLifetime {
    static final String START="agent.START", STOP="agent.STOP", INTERRUPT="agent.INTERRUPT";
    private final AgentService service;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final NotificationManager manager;
    private final PowerManager.WakeLock wake;
    private final Set<String> waiting=new HashSet<>();
    private boolean running;
    private String lastText="";
    private final Runnable refresh=this::refresh;
    private final Runnable heartbeat=new Runnable(){public void run(){if(running){refresh();handler.postDelayed(this,45000);}}};
    AgentLifetime(AgentService service){
        this.service=service;manager=service.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("agent","에이전트 실행 상태",NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel("agent-results","작업 완료와 확인 요청",NotificationManager.IMPORTANCE_DEFAULT));
        wake=service.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AndroidAgent:work");wake.setReferenceCounted(false);
    }
    static boolean enabled(Context context){return context.getSharedPreferences("lifetime",0).getBoolean("enabled",false);}
    static void enabled(Context context,boolean value){context.getSharedPreferences("lifetime",0).edit().putBoolean("enabled",value).commit();}
    void start(){
        running=true;lastText="백그라운드에서 대기 중";
        // aapt's resource table is API 30; the manifest uses the equivalent integer flags.
        service.startForeground(2,notification(lastText),Build.VERSION.SDK_INT>=34?0x40000000:1);
        handler.removeCallbacks(heartbeat);heartbeat.run();
    }
    boolean running(){return running;}
    void changed(){handler.removeCallbacks(refresh);handler.postDelayed(refresh,250);}
    private PendingIntent action(String value){return PendingIntent.getService(service,0,new Intent(service,AgentService.class).setAction(value),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private PendingIntent open(String session){
        Intent intent=new Intent(service,AgentActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(session!=null&&!session.isEmpty())intent.setData(Uri.parse("android-agent://session/"+Uri.encode(session))).putExtra("session",session);
        return PendingIntent.getActivity(service,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification notification(String text){
        Notification.Builder b=new Notification.Builder(service,"agent").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Android Agent").setContentText(text).setContentIntent(open(""))
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(null,"서비스 종료",action(STOP)).build());
        if(!service.sessions.activeSessions().isEmpty())b.addAction(new Notification.Action.Builder(null,"작업 중단",action(INTERRUPT)).build());
        return b.build();
    }
    private void refresh(){
        if(!running)return;
        java.util.List<String> active=service.sessions.activeSessions();
        String text=active.isEmpty()?"백그라운드에서 대기 중":active.size()+"개 대화에서 작업 중";
        boolean executing=service.pendingWork()>0;
        Set<String> now=new HashSet<>();
        for(String id:active){AgentService.Question q=service.question(id);if(q!=null){text="승인 또는 답변을 기다리고 있습니다";now.add(q.key);if(!waiting.contains(q.key))notice(id,"확인이 필요합니다","대화를 열어 승인하거나 답변해 주세요");}else executing=true;}
        waiting.clear();waiting.addAll(now);
        if(executing)wake.acquire(120000);else if(wake.isHeld())wake.release();
        if(!text.equals(lastText)){lastText=text;manager.notify(2,notification(text));}
    }
    void notice(String session,String title,String text){
        handler.post(()->{if(!running)return;try{manager.notify("session:"+session,3,new Notification.Builder(service,"agent-results")
            .setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(title).setContentText(text)
            .setContentIntent(open(session)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());}catch(SecurityException ignored){}});
    }
    void dismiss(String session){manager.cancel("session:"+session,3);}
    void stop(){running=false;handler.removeCallbacksAndMessages(null);if(wake.isHeld())wake.release();service.stopForeground(Service.STOP_FOREGROUND_REMOVE);}
}
