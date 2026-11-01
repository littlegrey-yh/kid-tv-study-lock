package com.family.gate;

import java.util.ArrayList;
import java.util.List;

/**
 * 答题引擎：维护本轮答题队列。
 * 规则（FR-2.4 / FR-1.1）：答对出队；答错则本题移到队尾，并追加 2 道新题，
 * 队列清空即本轮通过。
 */
public class QuizEngine {

    private final List<Question> queue = new ArrayList<>();
    private final List<Long> usedIds = new ArrayList<>();
    public int wrongCount = 0;
    public int answeredCount = 0;
    public final int initialCount;

    private QuizEngine(List<Question> initial) {
        queue.addAll(initial);
        initialCount = initial.size();
        for (Question q : initial) usedIds.add(q.id);
    }

    /** 开启一轮 n 道题的答题 */
    public static QuizEngine start(Db db, int n) {
        return new QuizEngine(db.pickRandom(n, null));
    }

    public boolean isDone()      { return queue.isEmpty(); }
    public Question current()    { return queue.isEmpty() ? null : queue.get(0); }
    public int remaining()       { return queue.size(); }

    /**
     * 提交当前题的答案（字母 A-D）。
     * @return 本轮是否已全部完成
     */
    public boolean submit(Db db, String letter) {
        if (queue.isEmpty()) return true;
        Question q = queue.get(0);
        answeredCount++;
        boolean correct = q.answer != null && q.answer.equalsIgnoreCase(letter);
        db.logAttempt(q.id, correct);
        // 移除队首
        queue.remove(0);
        if (!correct) {
            wrongCount++;
            // 本题重新入队尾
            queue.add(q);
            // 追加 2 道新题（题库不足时有多少补多少）
            List<Question> extra = db.pickRandom(2, usedIds);
            for (Question e : extra) {
                queue.add(e);
                usedIds.add(e.id);
            }
        }
        return queue.isEmpty();
    }
}
