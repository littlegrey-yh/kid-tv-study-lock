package com.family.gate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 观看监控服务（FR-2.1~2.3）：
 * 每 15 秒检查一次前台应用，累计"视频类 App"的连续观看时长，
 * 每满「弹题间隔」分钟触发一次答题弹窗；答题期间与家长放行期内暂停累计。
 */
public class WatchMonitorService extends Service {

    private static final long CHECK_INTERVAL_MS = 15000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int watchedSec = 0;
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            check();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("watch", "观看监控",
                    NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, "watch")
                    .setContentTitle("学习门禁 · 观看监控中")
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .build();
            startForeground(43, n);
        } else {
            Notification n = new Notification.Builder(this)
                    .setContentTitle("学习门禁 · 观看监控中")
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .build();
            startForeground(43, n);
        }
        running = true;
        handler.postDelayed(tick, CHECK_INTERVAL_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    private void check() {
        if (!Prefs.gateEnabled(this)) return;     // 总开关关闭：不计时不弹题
        if (GateService.showing) return;          // 答题弹窗已弹出，暂停计时
        if (Prefs.isUnlockedNow(this)) return;    // 家长放行期内
        if (!Prefs.watchMonitorEnabled(this)) return;

        String foreground = getForegroundPackage();
        if (foreground == null) return;
        if (!Prefs.containsVideoApp(this, foreground)) return;

        watchedSec += (int) (CHECK_INTERVAL_MS / 1000);
        int limitSec = Prefs.quizIntervalMin(this) * 60;
        if (watchedSec >= limitSec) {
            watchedSec = 0;
            if (!GateService.showing) GateService.show(this, false);
        }
    }

    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(now - 120000, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    last = e.getPackageName();
                }
            }
            return last;
        } catch (Exception ex) {
            return null; // 无"使用情况访问"权限时静默跳过
        }
    }

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, WatchMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, WatchMonitorService.class);
        i.setAction("STOP");
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }
}
