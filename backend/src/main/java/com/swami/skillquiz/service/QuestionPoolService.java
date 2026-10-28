package com.swami.skillquiz.service;

import com.swami.skillquiz.config.QuizProperties;
import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import com.swami.skillquiz.repo.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The heart of the design: AI is called per TOPIC POOL, not per employee.
 * 30 people starting "Java" at the same time cause at most one AI call;
 * everyone else waits for that call and then draws a random paper from the pool.
 */
@Service
public class QuestionPoolService {

    private static final Logger log = LoggerFactory.getLogger(QuestionPoolService.class);

    private final QuestionRepository questions;
    private final QuestionGenerator generator;
    private final GenerationRateLimiter limiter;
    private final QuizProperties props;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public QuestionPoolService(QuestionRepository questions, QuestionGenerator generator,
                               GenerationRateLimiter limiter, QuizProperties props) {
        this.questions = questions;
        this.generator = generator;
        this.limiter = limiter;
        this.props = props;
    }

    public long size(String topicKey, Level level) {
        return questions.countByTopicKeyAndLevelAndActiveTrue(topicKey, level);
    }

    /**
     * Blocking: make sure the pool has at least {@code needed} questions.
     * Called on the request path when a topic is brand new.
     */
    public void ensureAtLeast(String topicKey, String topicLabel, Level level, int needed) {
        if (size(topicKey, level) >= needed) return;

        ReentrantLock lock = lockFor(topicKey, level);
        boolean got;
        try {
            got = lock.tryLock(170, TimeUnit.SECONDS); // someone else may be generating this topic
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "Request cancel ho gayi, dobara try karo.");
        }
        if (!got) throw new ApiException(503, "Paper abhi ban raha hai, 1 minute baad try karo.");
        try {
            int rounds = 0;
            while (size(topicKey, level) < needed && rounds++ < 2) {
                if (!limiter.tryAcquire()) {
                    if (size(topicKey, level) >= props.questionsPerTest()) return; // good enough for now
                    throw new ApiException(429, "Bahut log ek saath naya topic shuru kar rahe hain. Thodi der mein apne aap dobara try hoga.");
                }
                try {
                    int added = generateBatch(topicKey, topicLabel, level);
                    if (added == 0 && size(topicKey, level) >= props.questionsPerTest()) return;
                } catch (ApiException e) {
                    // AI failed (limit / outage). If we already have one full paper, still let the employee start.
                    if (e.status() != 422 && size(topicKey, level) >= props.questionsPerTest()) {
                        log.info("AI unavailable for {}/{} ({}), serving existing pool", topicKey, level, e.getMessage());
                        return;
                    }
                    throw e;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** Non-blocking top-up used in the background. Skips if someone is already generating. */
    public void topUp(String topicKey, String topicLabel, Level level, int target) {
        ReentrantLock lock = lockFor(topicKey, level);
        if (!lock.tryLock()) return;
        try {
            int cap = Math.min(target, props.maxPoolSize());
            // one AI call per trigger: the free daily quota is better spent on NEW topics
            if (size(topicKey, level) < cap && limiter.tryAcquire()) {
                generateBatch(topicKey, topicLabel, level);
            }
        } catch (ApiException e) {
            log.info("Background top-up for {}/{} stopped: {}", topicKey, level, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** @return number of NEW questions saved */
    private int generateBatch(String topicKey, String topicLabel, Level level) {
        List<String> avoid = questions.recentTexts(topicKey, level, PageRequest.of(0, 40));
        log.info("Generating {} questions for '{}' [{}], pool={}", props.batchSize(), topicKey, level, size(topicKey, level));

        QuestionGenerator.Result result = generator.generate(topicLabel, level, props.batchSize(), avoid);
        if (!result.validTopic()) {
            String why = result.reason() == null || result.reason().isBlank() ? "" : " (" + result.reason() + ")";
            throw new ApiException(422, "'" + topicLabel + "' ek valid technology/skill nahi lag raha" + why
                    + ". Koi aur topic try karo, jaise Java, Python, GenAI, PHP.");
        }

        Set<String> seen = new HashSet<>(questions.fingerprints(topicKey, level));
        List<Question> fresh = new ArrayList<>();
        for (QuestionGenerator.Generated g : result.questions()) {
            Question q = toEntity(g, topicKey, level);
            if (q != null && seen.add(q.getFingerprint())) fresh.add(q);
        }
        questions.saveAll(fresh);
        log.info("Saved {} new questions for '{}' [{}]", fresh.size(), topicKey, level);
        return fresh.size();
    }

    /** Validates one AI question; returns null if it is broken. */
    private Question toEntity(QuestionGenerator.Generated g, String topicKey, Level level) {
        if (g.question() == null || g.question().isBlank() || g.question().length() > 1000) return null;
        List<String> opts = g.options();
        if (opts == null || opts.size() != 4) return null;
        if (g.correctIndex() < 0 || g.correctIndex() > 3) return null;
        Set<String> distinct = new HashSet<>();
        for (String o : opts) {
            if (o == null || o.isBlank() || o.length() > 400) return null;
            distinct.add(o.trim().toLowerCase());
        }
        if (distinct.size() != 4) return null;

        Question q = new Question();
        q.setTopicKey(topicKey);
        q.setLevel(level);
        q.setText(g.question().trim());
        q.setOptionA(opts.get(0).trim());
        q.setOptionB(opts.get(1).trim());
        q.setOptionC(opts.get(2).trim());
        q.setOptionD(opts.get(3).trim());
        q.setCorrectIndex(g.correctIndex());
        String exp = g.explanation() == null ? "" : g.explanation().trim();
        q.setExplanation(exp.length() > 1000 ? exp.substring(0, 1000) : exp);
        q.setFingerprint(Topics.fingerprint(q.getText()));
        return q;
    }

    private ReentrantLock lockFor(String topicKey, Level level) {
        return locks.computeIfAbsent(topicKey + "|" + level, k -> new ReentrantLock());
    }
}
