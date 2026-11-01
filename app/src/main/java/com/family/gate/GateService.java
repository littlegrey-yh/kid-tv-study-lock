package com.family.gate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * 门禁核心服务：全屏悬浮窗（覆盖桌面与视频），
 * 三种模式：MODE_QUIZ 答题 / MODE_PIN 家长方向密码 / MODE_DURATION 选择放行时长。
 * 遥控器交互：方向键选选项，OK 提交；长按 OK 3 秒进入家长解锁。
 */
public class GateService extends Service {

    public static final String ACTION_SHOW_BOOT = "com.family.gate.SHOW_BOOT";
    public static final String ACTION_SHOW_QUIZ = "com.family.gate.SHOW_QUIZ";
    public static final String ACTION_HIDE      = "com.family.gate.HIDE";

    /** 供其他组件读取的悬浮窗状态 */
    public static volatile boolean showing = false;

    private static final int MODE_QUIZ = 0, MODE_PIN = 1, MODE_DURATION = 2;

    private WindowManager wm;
    private LinearLayout root;
    private int mode = MODE_QUIZ;

    private TextView titleText, progressText, stemText, hintText;
    private TextView[] optionViews = new TextView[4];
    private LinearLayout quizPanel, pinPanel, durationPanel;
    private TextView pinDots, pinTitle;

    private QuizEngine engine;
    private int sel = 0;
    private int durationSel = 0;
    private StringBuilder pinInput = new StringBuilder();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean okDown = false;
    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (okDown && mode == MODE_QUIZ) enterPinMode();
        }
    };

    private static final String[] DURATIONS = {"本次放行（30 分钟）", "放行 1 小时", "放行 2 小时", "今日剩余时间"};

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW_BOOT : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            hideOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Prefs.gateEnabled(this) && ACTION_SHOW_BOOT.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Db db = Db.get(this);
        if (db.countEnabled() == 0) {
            // 题库为空：绝不锁死孩子（FR-4.2）
            stopSelf();
            return START_NOT_STICKY;
        }
        showOverlay(action);
        return START_STICKY;
    }

    // ================= 悬浮窗 =================

    private void showOverlay(String action) {
        if (root != null) { render(); return; }
        boolean boot = ACTION_SHOW_BOOT.equals(action);
        engine = QuizEngine.start(Db.get(this), boot ? Prefs.bootQuestions(this) : 3);
        sel = 0;
        mode = MODE_QUIZ;
        buildUi(boot);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        wm.addView(root, p);
        showing = true;
        root.post(new Runnable() { @Override public void run() { root.requestFocus(); } });
    }

    private void hideOverlay() {
        if (root != null && root.isAttachedToWindow()) {
            try { wm.removeView(root); } catch (Exception ignored) { }
        }
        root = null;
        showing = false;
    }

    // ================= UI 构建 =================

    private TextView makeText(String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        return t;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(topDp);
        return l;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private void buildUi(boolean boot) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F214181F"));
        root.setPadding(dp(90), dp(70), dp(90), dp(70));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener(keyListener);

        quizPanel = new LinearLayout(this);
        quizPanel.setOrientation(LinearLayout.VERTICAL);

        titleText = makeText(boot ? "学习门禁 · 答对方可使用电视" : "休息一下 · 答对继续观看",
                26, Color.WHITE, true);
        quizPanel.addView(titleText);

        progressText = makeText("", 16, Color.parseColor("#9FE1CB"), false);
        quizPanel.addView(progressText, lp(10));

        stemText = makeText("", 30, Color.WHITE, true);
        stemText.setLineSpacing(dp(6), 1f);
        quizPanel.addView(stemText, lp(24));

        for (int i = 0; i < 4; i++) {
            TextView o = makeText("", 24, Color.WHITE, false);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(Color.parseColor("#26FFFFFF"));
            o.setBackground(bg);
            o.setPadding(dp(24), dp(16), dp(24), dp(16));
            quizPanel.addView(o, lp(16));
            optionViews[i] = o;
        }

        hintText = makeText("↑↓ 选择 · OK 确认 · 长按 OK 3 秒 = 家长解锁", 15,
                Color.parseColor("#88FFFFFF"), false);
        quizPanel.addView(hintText, lp(30));

        // 家长方向密码面板
        pinPanel = new LinearLayout(this);
        pinPanel.setOrientation(LinearLayout.VERTICAL);
        pinTitle = makeText("家长解锁 · 请输入方向密码", 26, Color.WHITE, true);
        pinPanel.addView(pinTitle);
        pinDots = makeText("", 30, Color.parseColor("#9FE1CB"), true);
        pinDots.setGravity(Gravity.CENTER);
        pinPanel.addView(pinDots, lp(40));
        pinPanel.addView(makeText("用遥控器方向键输入（错误自动清空重来）", 15,
                Color.parseColor("#88FFFFFF"), false), lp(20));

        // 放行时长面板
        durationPanel = new LinearLayout(this);
        durationPanel.setOrientation(LinearLayout.VERTICAL);
        durationPanel.addView(makeText("家长已验证 · 选择放行时长", 26, Color.WHITE, true));
        for (int i = 0; i < DURATIONS.length; i++) {
            TextView o = makeText(DURATIONS[i], 22, Color.WHITE, false);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(Color.parseColor("#26FFFFFF"));
            o.setBackground(bg);
            o.setPadding(dp(24), dp(14), dp(24), dp(14));
            durationPanel.addView(o, lp(16));
        }

        root.addView(quizPanel);
        root.addView(pinPanel);
        root.addView(durationPanel);
        pinPanel.setVisibility(View.GONE);
        durationPanel.setVisibility(View.GONE);

        render();
    }

    private void highlight(TextView[] views, int idx, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.parseColor(selected ? "#5DCAA5" : "#26FFFFFF"));
        views[idx].setBackground(bg);
    }

    private void render() {
        if (mode == MODE_QUIZ) {
            Question q = engine == null ? null : engine.current();
            progressText.setText("本轮剩余 " + (engine == null ? 0 : engine.remaining())
                    + " 题 · 已答错 " + (engine == null ? 0 : engine.wrongCount) + " 题");
            if (q == null) { hideOverlay(); stopSelf(); return; }
            stemText.setText(q.subject + "｜" + q.stem);
            String[] opts = {q.optA, q.optB, q.optC, q.optD};
            int visible = 0;
            for (int i = 0; i < 4; i++) {
                if (opts[i] != null && !opts[i].trim().isEmpty()) {
                    optionViews[i].setText(q.letterOf(i) + ". " + opts[i]);
                    optionViews[i].setVisibility(View.VISIBLE);
                    highlight(optionViews, i, i == sel);
                    visible++;
                } else {
                    optionViews[i].setVisibility(View.GONE);
                }
            }
            if (sel >= visible) sel = 0;
        } else if (mode == MODE_PIN) {
            int total = Prefs.parentSeq(this).length();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < total; i++)
                sb.append(i < pinInput.length() ? "● " : "○ ");
            pinDots.setText(sb.toString());
        } else if (mode == MODE_DURATION) {
            for (int i = 0; i < DURATIONS.length; i++) {
                TextView v = (TextView) durationPanel.getChildAt(i + 1);
                highlight(new TextView[]{v}, 0, i == durationSel);
            }
        }
    }

    // ================= 按键处理 =================

    private View.OnKeyListener keyListener = new View.OnKeyListener() {
        @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (mode == MODE_QUIZ && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    if (!okDown) {
                        okDown = true;
                        handler.postDelayed(longPressRunnable, 3000);
                    }
                    return true;
                }
                switch (mode) {
                    case MODE_QUIZ: handleQuizKey(keyCode); return true;
                    case MODE_PIN:  handlePinKey(keyCode);  return true;
                    case MODE_DURATION: handleDurationKey(keyCode); return true;
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER)) {
                okDown = false;
                handler.removeCallbacks(longPressRunnable);
                return true;
            }
            return true; // 悬浮窗内吞掉所有按键，防止逃逸
        }
    };

    private void handleQuizKey(int keyCode) {
        Question q = engine == null ? null : engine.current();
        if (q == null) return;
        int count = 0;
        String[] opts = {q.optA, q.optB, q.optC, q.optD};
        for (String o : opts) if (o != null && !o.trim().isEmpty()) count++;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:   sel = (sel - 1 + count) % count; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: sel = (sel + 1) % count; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                boolean done = engine.submit(Db.get(this), q.letterOf(sel));
                if (done) {
                    unlock(30); // 全部答对：放行 30 分钟，监控计时重新累计
                    return;
                }
                break;
        }
        render();
    }

    private void handlePinKey(int keyCode) {
        String seq = Prefs.parentSeq(this);
        String key = dirOf(keyCode);
        if (key == null) return; // 只接受方向键
        pinInput.append(key);
        if (!seq.startsWith(pinInput.toString())) {
            pinInput.setLength(0); // 输错清空重来
        } else if (pinInput.length() == seq.length()) {
            pinInput.setLength(0);
            mode = MODE_DURATION;
            durationSel = 0;
        }
        render();
    }

    private void handleDurationKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:   durationSel = (durationSel - 1 + DURATIONS.length) % DURATIONS.length; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: durationSel = (durationSel + 1) % DURATIONS.length; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                int minutes;
                switch (durationSel) {
                    case 0: minutes = 30; break;
                    case 1: minutes = 60; break;
                    case 2: minutes = 120; break;
                    default:
                        Calendar c = Calendar.getInstance();
                        c.set(Calendar.HOUR_OF_DAY, 23);
                        c.set(Calendar.MINUTE, 59);
                        c.set(Calendar.SECOND, 59);
                        minutes = (int) Math.max(30,
                                (c.getTimeInMillis() - System.currentTimeMillis()) / 60000);
                }
                unlock(minutes);
                return;
        }
        render();
    }

    private String dirOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    return "U";
            case KeyEvent.KEYCODE_DPAD_DOWN:  return "D";
            case KeyEvent.KEYCODE_DPAD_LEFT:  return "L";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "R";
            default: return null;
        }
    }

    private void unlock(int minutes) {
        Prefs.setUnlockUntil(this,
                System.currentTimeMillis() + minutes * 60000L);
        hideOverlay();
        stopSelf();
    }

    private void enterPinMode() {
        okDown = false;
        mode = MODE_PIN;
        pinInput.setLength(0);
        quizPanel.setVisibility(View.GONE);
        pinPanel.setVisibility(View.VISIBLE);
        render();
    }

    // ================= 通知与静态入口 =================

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("gate", "学习门禁",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, "gate")
                    .setContentTitle("学习门禁运行中")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .build();
            startForeground(42, n);
        } else {
            Notification n = new Notification.Builder(this)
                    .setContentTitle("学习门禁运行中")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .build();
            startForeground(42, n);
        }
    }

    /** 供开机广播 / 监控服务触发 */
    public static void show(Context ctx, boolean boot) {
        Intent i = new Intent(ctx, GateService.class);
        i.setAction(boot ? ACTION_SHOW_BOOT : ACTION_SHOW_QUIZ);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void hide(Context ctx) {
        Intent i = new Intent(ctx, GateService.class);
        i.setAction(ACTION_HIDE);
        ctx.startService(i);
    }
}
