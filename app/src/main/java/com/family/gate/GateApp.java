package com.family.gate;

import android.app.Application;
import android.content.Intent;

/** 应用入口：启动局域网管理服务 + 监控服务，并做开机兜底检查 */
public class GateApp extends Application {

    private static AdminServer adminServer;

    @Override
    public void onCreate() {
        super.onCreate();
        startAdminServer();
        startWatchMonitor();

        // 兜底：进程被拉起时（如被厂商自启管理唤醒）再检查一次门禁
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                new Runnable() { @Override public void run() { maybeShowGate(GateApp.this); } },
                10000);
    }

    /** 满足条件才弹开机门禁：总开关 + 开机弹题开关 + 不在家长放行期 + 题库非空 */
    public static void maybeShowGate(android.content.Context ctx) {
        if (!Prefs.gateEnabled(ctx)) { log("gate off, skip"); return; }
        if (!Prefs.bootQuizEnabled(ctx)) { log("boot quiz off, skip"); return; }
        if (Prefs.isUnlockedNow(ctx)) { log("parent unlocked, skip"); return; }
        if (GateService.showing) { log("already showing, skip"); return; }
        if (Db.get(ctx).countEnabled() == 0) { log("empty bank, skip"); return; }
        log("showing boot gate");
        GateService.show(ctx, true);
    }

    private static void log(String msg) {
        android.util.Log.i("LearningGate", msg);
    }

    private void startAdminServer() {
        if (adminServer != null) return;
        try {
            adminServer = new AdminServer(this, 8765);
            adminServer.start(5000, true); // 5秒超时、守护线程运行
        } catch (Exception ignored) { }
    }

    private void startWatchMonitor() {
        // 总开关或监控开关任一关闭都不启动监控
        if (!Prefs.gateEnabled(this) || !Prefs.watchMonitorEnabled(this)) return;
        Intent i = new Intent(this, WatchMonitorService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }
}
