package com.family.gate;

import android.content.Context;
import android.content.SharedPreferences;

/** 全局配置（SharedPreferences 单例） */
public class Prefs {

    private static SharedPreferences sp;

    public static final String DEFAULT_PARENT_SEQ = "UDUDLRLR"; // 上上下下左右左右

    public static synchronized SharedPreferences get(Context ctx) {
        if (sp == null) {
            sp = ctx.getApplicationContext()
                    .getSharedPreferences("gate", Context.MODE_PRIVATE);
        }
        return sp;
    }

    // ---- 配置项 ----
    public static boolean gateEnabled(Context c)            { return get(c).getBoolean("gateEnabled", false); }
    public static void setGateEnabled(Context c, boolean v) { get(c).edit().putBoolean("gateEnabled", v).apply(); }

    public static int bootQuestions(Context c)              { return get(c).getInt("bootQuestions", 3); }
    public static void setBootQuestions(Context c, int v)   { get(c).edit().putInt("bootQuestions", v).apply(); }

    public static int quizIntervalMin(Context c)            { return get(c).getInt("quizIntervalMin", 10); }
    public static void setQuizIntervalMin(Context c, int v) { get(c).edit().putInt("quizIntervalMin", v).apply(); }

    /** 孩子当前学年（1-6）与学期（1=上 2=下），抽题池 = 学过的一切 */
    public static int kidGrade(Context c)            { return get(c).getInt("kidGrade", 3); }
    public static void setKidGrade(Context c, int v) { get(c).edit().putInt("kidGrade", v).apply(); }
    public static int kidTerm(Context c)             { return get(c).getInt("kidTerm", 1); }
    public static void setKidTerm(Context c, int v)  { get(c).edit().putInt("kidTerm", v).apply(); }

    public static boolean watchMonitorEnabled(Context c)            { return get(c).getBoolean("watchMonitorEnabled", false); }
    public static void setWatchMonitorEnabled(Context c, boolean v) { get(c).edit().putBoolean("watchMonitorEnabled", v).apply(); }

    /** 家长密码：U/D/L/R 方向序列 */
    public static String parentSeq(Context c)               { return get(c).getString("parentSeq", DEFAULT_PARENT_SEQ); }
    public static void setParentSeq(Context c, String v)    { get(c).edit().putString("parentSeq", v).apply(); }

    /** 管理页访问密码（局域网任何设备都能打开管理页，必须有密码保护） */
    public static String adminPass(Context c)               { return get(c).getString("adminPass", "0000"); }
    public static void setAdminPass(Context c, String v)    { get(c).edit().putString("adminPass", v).apply(); }

    /** 视频类应用包名，逗号分隔 */
    public static String videoApps(Context c) {
        return get(c).getString("videoApps",
                "com.tencent.qqlivetv,com.qiyi.video.tv,com.youku.tv,com.tvtaobao.tv.tudou,"
                + "tv.danmaku.bili,com.hunantv.imgo.activity,com.air.tv.videoplus,"
                + "com.le.tvvideo,com.tbs.caifutong,com.skyworthwebos.app.tvvideo");
    }
    public static void setVideoApps(Context c, String v)    { get(c).edit().putString("videoApps", v).apply(); }

    public static boolean containsVideoApp(Context c, String pkg) {
        if (pkg == null) return false;
        for (String s : videoApps(c).split(",")) {
            if (!s.trim().isEmpty() && pkg.contains(s.trim())) return true;
        }
        return false;
    }

    // ---- 家长放行状态 ----
    public static long unlockUntil(Context c)               { return get(c).getLong("unlockUntil", 0L); }
    public static void setUnlockUntil(Context c, long ts)   { get(c).edit().putLong("unlockUntil", ts).apply(); }

    public static boolean isUnlockedNow(Context c) {
        return System.currentTimeMillis() < unlockUntil(c);
    }
}
