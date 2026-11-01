package com.family.gate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** 电视端主界面：状态展示 + 权限引导 + 测试门禁 */
public class MainActivity extends Activity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(90), dp(70), dp(90), dp(70));
        root.setBackgroundColor(Color.parseColor("#F214181F"));

        TextView title = new TextView(this);
        title.setText("学习门禁");
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setTextColor(Color.parseColor("#9FE1CB"));
        statusText.setPadding(0, dp(24), 0, 0);
        root.addView(statusText);

        root.addView(button("① 授予悬浮窗权限（必须）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));

        root.addView(button("② 授予使用情况访问权限（观看计时用）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception ignored) { }
            }
        }));

        root.addView(button("③ 测试：立即弹出答题门禁", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Db.get(MainActivity.this).countEnabled() == 0) {
                    statusText.setText("题库为空！请先用手机浏览器打开管理页录题。");
                    return;
                }
                GateService.show(MainActivity.this, true);
                finish();
            }
        }));

        setContentView(ScrollViewWrap(root));
        refreshStatus();
    }

    private ViewGroup ScrollViewWrap(LinearLayout inner) {
        ScrollView sv = new ScrollView(this);
        sv.addView(inner);
        return sv;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(20), dp(14), dp(20), dp(14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(20);
        b.setLayoutParams(p);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        String ip = localIp();
        String url = ip == null ? "（电视未联网）" : "http://" + ip + ":8765";
        String s = "管理页地址（手机浏览器打开）：" + url
                + "\n\n门禁开关：" + (Prefs.gateEnabled(this) ? "已开启" : "未开启（去管理页打开）")
                + "\n题库题目数：" + Db.get(this).countEnabled()
                + "\n开机答题数：" + Prefs.bootQuestions(this) + " 题"
                + "\n弹题间隔：" + Prefs.quizIntervalMin(this) + " 分钟"
                + "\n家长方向密码：已设置（内容不在此显示，家长请查看纸质/本地手册或管理页）"
                + "\n\n家长解锁：门禁界面长按 OK 键 3 秒 → 输入方向密码 → 选择放行时长";
        statusText.setText(s);
    }

    private int dp(int v) {
        return (int) getResources().getDisplayMetrics().density * v;
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getAddress().length == 4) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
