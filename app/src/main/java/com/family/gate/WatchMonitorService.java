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
    private long lastTickAt = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            // 用真实流逝时间计时：电视高负载时任务延迟数分钟也能正确累计
            long now = android.os.SystemClock.elapsedRealtime();
            long deltaSec = lastTickAt == 0 ? 0
                    : Math.min((now - lastTickAt) / 1000, 300);
            lastTickAt = now;
            check((int) deltaSec);
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

    private void check(int deltaSec) {
        if (!Prefs.gateEnabled(this)) { diag("跳过: 总开关关"); return; }
        if (GateService.showing)     { diag("跳过: 答题弹窗中"); return; }
        if (Prefs.isUnlockedNow(this)) { diag("跳过: 家长放行期至 "
                + java.text.DateFormat.getTimeInstance().format(
                        new java.util.Date(Prefs.unlockUntil(this)))); return; }
        if (!Prefs.watchMonitorEnabled(this)) { diag("跳过: 监控开关关"); return; }

        String fg = getForegroundPackage();
        boolean inList = fg != null && Prefs.containsVideoApp(this, fg);
        if (!inList) { diag("前台=" + fg + " 不在名单, 已计 " + watchedSec + "s"); return; }

        watchedSec += deltaSec;
        int limitSec = Prefs.quizIntervalMin(this) * 60;
        diag("计时中 前台=" + fg + " +" + deltaSec + "s, 累计 " + watchedSec + "/" + limitSec + "s");
        if (watchedSec >= limitSec) {
            watchedSec = 0;
            if (!GateService.showing) {
                diag(">>> 达到间隔，弹题！");
                GateService.show(this, false);
            }
        }
    }

    /** 可观测日志（该 ROM 屏蔽 Log.d，必须用 Log.i） */
    private void diag(String msg) {
        android.util.Log.i("LearningGate", "[监控] " + msg);
    }

    private String lastForeground = null;

    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) { diag("usm=null"); return null; }
            long now = System.currentTimeMillis();
            // 查最近 30 分钟事件取最新一条；一直挂着的场景没有新事件时沿用上次结果
            UsageEvents ev = usm.queryEvents(now - 30 * 60000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            int n = 0;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                n++;
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    last = e.getPackageName();
                }
            }
            if (last != null) lastForeground = last;
            if (n == 0) diag("30分钟窗口内无任何事件(权限?)");
            return lastForeground;
        } catch (SecurityException se) {
            diag("无使用情况权限: " + se.getMessage());
            return null;
        } catch (Exception ex) {
            diag("前台识别异常: " + ex);
            return null;
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
