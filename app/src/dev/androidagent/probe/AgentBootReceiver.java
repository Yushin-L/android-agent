package dev.androidagent.probe;

import android.content.*;

/** Restore only a previously enabled agent, never bypass user force-stop. */
public final class AgentBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        if(AgentLifetime.enabled(context))try{context.startForegroundService(new Intent(context,AgentService.class));}
        catch(IllegalStateException|SecurityException unavailable){/* User can restart from the app. */}
    }
}
