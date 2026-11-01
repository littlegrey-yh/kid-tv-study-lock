package com.family.gate;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 局域网管理服务（FR-4）：家长用手机/电脑浏览器访问 http://电视IP:8765
 * 提供录题、配置、导入导出、远程解锁。
 */
public class AdminServer extends NanoHTTPD {

    private final Context ctx;

    public AdminServer(Context ctx, int port) {
        super("0.0.0.0", port);
        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if ("/".equals(uri) || "/index.html".equals(uri)) {
                return asset("admin.html", "text/html; charset=utf-8");
            }
            if ("/api/status".equals(uri)) {
                return json(status());
            }
            if ("/api/questions".equals(uri)) {
                if (session.getMethod() == Method.GET) {
                    JSONArray arr = new JSONArray();
                    for (Question q : Db.get(ctx).listQuestions()) arr.put(q.toJson());
                    JSONObject o = new JSONObject();
                    o.put("questions", arr);
                    return json(o);
                }
                if (session.getMethod() == Method.POST) {
                    Question q = Question.fromJson(new JSONObject(body(session)));
                    q.id = 0;
                    long id = Db.get(ctx).addQuestion(q);
                    return json(new JSONObject().put("ok", true).put("id", id));
                }
            }
            if ("/api/questions/delete".equals(uri) && session.getMethod() == Method.POST) {
                JSONObject b = new JSONObject(body(session));
                Db.get(ctx).deleteQuestion(b.getLong("id"));
                return json(new JSONObject().put("ok", true));
            }
            if ("/api/questions/enable".equals(uri) && session.getMethod() == Method.POST) {
                JSONObject b = new JSONObject(body(session));
                Db.get(ctx).setEnabled(b.getLong("id"), b.optBoolean("enabled", true));
                return json(new JSONObject().put("ok", true));
            }
            if ("/api/export".equals(uri)) {
                return json(new JSONObject(Db.get(ctx).exportJson()));
            }
            if ("/api/import".equals(uri) && session.getMethod() == Method.POST) {
                JSONObject b = new JSONObject(body(session));
                int n = Db.get(ctx).importJson(b.optString("json", "{}"));
                return json(new JSONObject().put("ok", true).put("imported", n));
            }
            if ("/api/config".equals(uri)) {
                if (session.getMethod() == Method.GET) return json(config());
                JSONObject b = new JSONObject(body(session));
                if (b.has("gateEnabled"))          Prefs.setGateEnabled(ctx, b.getBoolean("gateEnabled"));
                if (b.has("bootQuestions"))        Prefs.setBootQuestions(ctx, b.getInt("bootQuestions"));
                if (b.has("quizIntervalMin"))      Prefs.setQuizIntervalMin(ctx, b.getInt("quizIntervalMin"));
                if (b.has("watchMonitorEnabled"))  Prefs.setWatchMonitorEnabled(ctx, b.getBoolean("watchMonitorEnabled"));
                if (b.has("videoApps"))            Prefs.setVideoApps(ctx, b.getString("videoApps"));
                if (b.has("parentSeq")) {
                    String seq = b.getString("parentSeq").toUpperCase().replaceAll("[^UDLR]", "");
                    if (seq.length() >= 4) Prefs.setParentSeq(ctx, seq);
                }
                if (b.optBoolean("watchMonitorEnabled")) WatchMonitorService.start(ctx);
                return json(new JSONObject().put("ok", true));
            }
            if ("/api/unlock".equals(uri) && session.getMethod() == Method.POST) {
                JSONObject b = new JSONObject(body(session));
                String seq = b.optString("seq", "").toUpperCase();
                int minutes = b.optInt("minutes", 30);
                if (!seq.equals(Prefs.parentSeq(ctx))) {
                    return json(new JSONObject().put("ok", false).put("error", "方向密码错误"));
                }
                Prefs.setUnlockUntil(ctx, System.currentTimeMillis() + minutes * 60000L);
                GateService.hide(ctx);
                return json(new JSONObject().put("ok", true));
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
        } catch (Exception e) {
            try {
                return json(new JSONObject().put("ok", false).put("error", String.valueOf(e.getMessage())));
            } catch (Exception ee) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error");
            }
        }
    }

    private JSONObject status() throws Exception {
        JSONObject o = new JSONObject();
        o.put("gateEnabled", Prefs.gateEnabled(ctx));
        o.put("count", Db.get(ctx).countEnabled());
        o.put("unlockUntil", Prefs.unlockUntil(ctx));
        o.put("version", "0.5.0");
        return o;
    }

    private JSONObject config() throws Exception {
        JSONObject o = new JSONObject();
        o.put("gateEnabled", Prefs.gateEnabled(ctx));
        o.put("bootQuestions", Prefs.bootQuestions(ctx));
        o.put("quizIntervalMin", Prefs.quizIntervalMin(ctx));
        o.put("watchMonitorEnabled", Prefs.watchMonitorEnabled(ctx));
        o.put("videoApps", Prefs.videoApps(ctx));
        o.put("parentSeq", Prefs.parentSeq(ctx));
        return o;
    }

    private String body(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String post = files.get("postData");
            return post == null ? "" : post;
        } catch (Exception e) {
            return "";
        }
    }

    private Response json(JSONObject o) {
        return newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", o.toString());
    }

    private Response asset(String path, String mime) {
        try (InputStream in = ctx.getAssets().open(path)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return newFixedLengthResponse(Response.Status.OK, mime,
                    bos.toString("UTF-8"));
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "missing asset");
        }
    }
}
