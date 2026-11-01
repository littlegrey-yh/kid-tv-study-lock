package com.family.gate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** 开机广播：延迟几秒拉起门禁（等系统桌面就绪），家长放行期内不打扰 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) return;

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                GateApp.maybeShowGate(context);
            }
        }, 8000);
    }
}
