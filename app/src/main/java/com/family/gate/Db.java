package com.family.gate;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 本地题库与答题记录（SQLite） */
public class Db extends SQLiteOpenHelper {

    private static Db inst;

    public static synchronized Db get(Context ctx) {
        if (inst == null) inst = new Db(ctx.getApplicationContext());
        return inst;
    }

    private Db(Context c) {
        super(c, "gate.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE questions("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "subject TEXT, difficulty INTEGER DEFAULT 2,"
                + "grade INTEGER DEFAULT 3, term INTEGER DEFAULT 1,"
                + "stem TEXT NOT NULL,"
                + "optA TEXT, optB TEXT, optC TEXT, optD TEXT,"
                + "answer TEXT NOT NULL, explanation TEXT,"
                + "enabled INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE attempts("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "qid INTEGER, correct INTEGER, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE questions ADD COLUMN grade INTEGER DEFAULT 3");
            db.execSQL("ALTER TABLE questions ADD COLUMN term INTEGER DEFAULT 1");
        }
    }

    // ---- 题目管理 ----

    public long addQuestion(Question q) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("subject", q.subject);
        cv.put("difficulty", q.difficulty);
        cv.put("grade", q.grade);
        cv.put("term", q.term);
        cv.put("stem", q.stem);
        cv.put("optA", q.optA);
        cv.put("optB", q.optB);
        cv.put("optC", q.optC);
        cv.put("optD", q.optD);
        cv.put("answer", q.answer);
        cv.put("explanation", q.explanation);
        cv.put("enabled", q.enabled ? 1 : 0);
        return db.insert("questions", null, cv);
    }

    public List<Question> listQuestions() {
        List<Question> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("questions", null, null, null, null, null, "id DESC");
        while (c.moveToNext()) out.add(fromCursor(c));
        c.close();
        return out;
    }

    public void deleteQuestion(long id) {
        getWritableDatabase().delete("questions", "id=?", new String[]{String.valueOf(id)});
    }

    public void setEnabled(long id, boolean en) {
        ContentValues cv = new ContentValues();
        cv.put("enabled", en ? 1 : 0);
        getWritableDatabase().update("questions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    /** 清空全部题目和答题记录（家长在管理页显式确认后调用） */
    public void clearQuestions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("questions", null, null);
        db.delete("attempts", null, null);
    }

    public int countEnabled() {
        Cursor c = getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM questions WHERE enabled=1", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /**
     * 随机抽 n 道启用的题，年级池 = (grade < maxGrade) 或 (grade == maxGrade 且 term <= maxTerm)。
     * 尽量避开 excludeIds 中的题；题不够时可重复。
     */
    public List<Question> pickRandom(int n, List<Long> excludeIds, int maxGrade, int maxTerm) {
        List<Question> out = new ArrayList<>();
        if (n <= 0) return out;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM questions WHERE enabled=1 AND "
                        + "(grade < ? OR (grade = ? AND term <= ?)) ORDER BY RANDOM()",
                new String[]{String.valueOf(maxGrade), String.valueOf(maxGrade), String.valueOf(maxTerm)});
        while (c.moveToNext() && out.size() < n) {
            Question q = fromCursor(c);
            if (excludeIds != null && excludeIds.contains(q.id)) continue;
            out.add(q);
        }
        c.close();
        // 年级池内题量不足时放宽限制补齐
        while (out.size() < n && countEnabled() > 0) {
            List<Question> more = pickRandom(1, null, 6, 2);
            if (more.isEmpty()) break;
            out.add(more.get(0));
        }
        return out;
    }

    public void logAttempt(long qid, boolean correct) {
        ContentValues cv = new ContentValues();
        cv.put("qid", qid);
        cv.put("correct", correct ? 1 : 0);
        cv.put("ts", System.currentTimeMillis());
        getWritableDatabase().insert("attempts", null, cv);
    }

    /** 按天×科目聚合答题正确率（最近 days 天），供管理页薄弱折线图 */
    public JSONArray statsBySubject(int days) throws Exception {
        long since = System.currentTimeMillis() - days * 86400000L;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT date(a.ts/1000, 'unixepoch', 'localtime') d, q.subject s, "
                        + "COUNT(*) total, SUM(a.correct) ok FROM attempts a "
                        + "JOIN questions q ON a.qid = q.id WHERE a.ts >= ? "
                        + "GROUP BY d, s ORDER BY d",
                new String[]{String.valueOf(since)});
        JSONArray arr = new JSONArray();
        while (c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put("day", c.getString(0));
            o.put("subject", c.getString(1));
            o.put("total", c.getInt(2));
            o.put("ok", c.getInt(3));
            arr.put(o);
        }
        c.close();
        return arr;
    }

    /** 错误最多的题 Top N（薄弱题清单） */
    public JSONArray weakQuestions(int limit) throws Exception {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT q.subject s, q.stem, COUNT(*) total, SUM(a.correct) ok "
                        + "FROM attempts a JOIN questions q ON a.qid = q.id "
                        + "GROUP BY a.qid HAVING ok < total "
                        + "ORDER BY (total - ok) DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        JSONArray arr = new JSONArray();
        while (c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put("subject", c.getString(0));
            o.put("stem", c.getString(1));
            o.put("total", c.getInt(2));
            o.put("ok", c.getInt(3));
            arr.put(o);
        }
        c.close();
        return arr;
    }

    /** 批量导入题目，支持纯数组或 {"questions":[...]} 两种格式，返回导入条数 */
    public int importJson(String json, int defGrade, int defTerm) throws Exception {
        String s = json.trim();
        JSONArray arr;
        if (s.startsWith("[")) {
            arr = new JSONArray(s);
        } else {
            JSONObject top = new JSONObject(s);
            arr = top.optJSONArray("questions");
            if (arr == null) arr = new JSONArray(s);
        }
        int n = 0;
        for (int i = 0; i < arr.length(); i++) {
            Question q = Question.fromJson(arr.getJSONObject(i));
            if (q.stem != null && !q.stem.trim().isEmpty()) {
                q.id = 0; // 导入一律新增
                if (q.grade < 1 || q.grade > 6) q.grade = defGrade;
                if (q.term < 1 || q.term > 2) q.term = defTerm;
                addQuestion(q);
                n++;
            }
        }
        return n;
    }

    public String exportJson() {
        try {
            JSONArray arr = new JSONArray();
            for (Question q : listQuestions()) arr.put(q.toJson());
            JSONObject top = new JSONObject();
            top.put("questions", arr);
            return top.toString();
        } catch (Exception e) {
            return "{\"questions\":[]}";
        }
    }

    private static Question fromCursor(Cursor c) {
        Question q = new Question();
        q.id = c.getLong(c.getColumnIndexOrThrow("id"));
        q.subject = c.getString(c.getColumnIndexOrThrow("subject"));
        q.difficulty = c.getInt(c.getColumnIndexOrThrow("difficulty"));
        q.grade = c.getInt(c.getColumnIndexOrThrow("grade"));
        q.term = c.getInt(c.getColumnIndexOrThrow("term"));
        q.stem = c.getString(c.getColumnIndexOrThrow("stem"));
        q.optA = c.getString(c.getColumnIndexOrThrow("optA"));
        q.optB = c.getString(c.getColumnIndexOrThrow("optB"));
        q.optC = c.getString(c.getColumnIndexOrThrow("optC"));
        q.optD = c.getString(c.getColumnIndexOrThrow("optD"));
        q.answer = c.getString(c.getColumnIndexOrThrow("answer"));
        q.explanation = c.getString(c.getColumnIndexOrThrow("explanation"));
        q.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1;
        return q;
    }
}
