package com.family.gate;

import org.json.JSONException;
import org.json.JSONObject;

/** 一道选择题 */
public class Question {

    public long id;
    public String subject;   // 科目：语文/数学/英语...
    public int difficulty;   // 1易 2中 3难
    public int grade = 3;    // 学年 1-6
    public int term = 1;     // 学期 1=上 2=下
    public String stem;      // 题干
    public String optA, optB, optC, optD;
    public String answer;    // "A" / "B" / "C" / "D"
    public String explanation;
    public boolean enabled = true;

    public String optionOf(int idx) {
        switch (idx) {
            case 0: return optA;
            case 1: return optB;
            case 2: return optC;
            case 3: return optD;
            default: return null;
        }
    }

    public String letterOf(int idx) {
        return idx >= 0 && idx < 4 ? String.valueOf((char) ('A' + idx)) : "";
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("subject", subject);
            o.put("difficulty", difficulty);
            o.put("grade", grade);
            o.put("term", term);
            o.put("stem", stem);
            o.put("optA", optA);
            o.put("optB", optB);
            o.put("optC", optC);
            o.put("optD", optD);
            o.put("answer", answer);
            o.put("explanation", explanation);
            o.put("enabled", enabled);
        } catch (JSONException ignored) { }
        return o;
    }

    public static Question fromJson(JSONObject o) {
        Question q = new Question();
        q.id = o.optLong("id", 0);
        q.subject = o.optString("subject", "通用");
        q.difficulty = o.optInt("difficulty", 2);
        q.grade = o.optInt("grade", 0);
        q.term = o.optInt("term", 0);
        q.stem = o.optString("stem", "");
        q.optA = o.optString("optA", null);
        q.optB = o.optString("optB", null);
        q.optC = o.optString("optC", null);
        q.optD = o.optString("optD", null);
        q.answer = o.optString("answer", "A").toUpperCase();
        q.explanation = o.optString("explanation", "");
        q.enabled = o.optBoolean("enabled", true);
        return q;
    }
}
