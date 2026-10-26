package com.swami.skillquiz.service;

import com.swami.skillquiz.config.QuizProperties;
import com.swami.skillquiz.model.Attempt;
import com.swami.skillquiz.model.AttemptItem;
import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import com.swami.skillquiz.repo.AttemptRepository;
import com.swami.skillquiz.repo.QuestionRepository;
import com.swami.skillquiz.web.Dtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class QuizService {

    private final QuestionPoolService pool;
    private final PoolRefiller refiller;
    private final QuestionRepository questions;
    private final AttemptRepository attempts;
    private final QuizProperties props;
    private final SecureRandom random = new SecureRandom();

    public QuizService(QuestionPoolService pool, PoolRefiller refiller, QuestionRepository questions,
                       AttemptRepository attempts, QuizProperties props) {
        this.pool = pool;
        this.refiller = refiller;
        this.questions = questions;
        this.attempts = attempts;
        this.props = props;
    }

    /**
     * 1. make sure the topic pool exists (AI call only if the topic is new)
     * 2. pick questions this employee has NOT seen before, at random
     * 3. shuffle the 4 options separately for this person
     * Result: the person sitting next to you gets a different paper.
     */
    public StartResponse start(StartRequest req) {
        String cleaned = Topics.clean(req.topic());
        String key = Topics.key(cleaned);
        String label = Topics.label(cleaned);
        Level level = req.level();
        int perTest = props.questionsPerTest();

        // Blocking only when the pool is too small (first person on a new topic waits ~20-40s)
        pool.ensureAtLeast(key, label, level, Math.max(props.minPoolSize(), perTest));

        List<Question> all = questions.findByTopicKeyAndLevelAndActiveTrue(key, level);
        if (all.isEmpty()) {
            throw new ApiException(503, "Is topic ke questions abhi ready nahi hain. Thodi der baad try karo.");
        }
        Set<Long> seen = new HashSet<>(attempts.seenQuestionIds(req.empId().trim(), key, level));

        List<Question> fresh = new ArrayList<>(all.stream().filter(q -> !seen.contains(q.getId())).toList());
        List<Question> old = new ArrayList<>(all.stream().filter(q -> seen.contains(q.getId())).toList());
        Collections.shuffle(fresh, random);
        Collections.shuffle(old, random);

        List<Question> paper = new ArrayList<>(fresh.subList(0, Math.min(perTest, fresh.size())));
        for (Question q : old) {
            if (paper.size() >= perTest) break;
            paper.add(q);
        }
        Collections.shuffle(paper, random);

        Attempt a = new Attempt();
        a.setEmpId(req.empId().trim());
        a.setEmpName(req.empName().trim());
        a.setDepartment(req.department() == null ? null : req.department().trim());
        a.setTopicKey(key);
        a.setTopicLabel(label);
        a.setLevel(level);
        a.setTotal(paper.size());
        for (int i = 0; i < paper.size(); i++) {
            AttemptItem item = new AttemptItem();
            item.setAttempt(a);
            item.setQuestion(paper.get(i));
            item.setPosition(i + 1);
            item.setOptionOrder(randomOrder());
            a.getItems().add(item);
        }
        attempts.save(a);

        // Grow the pool in the background so the next people get even more variety.
        // Grow faster if this employee has already seen most of the pool (retakes).
        int target = props.targetPoolSize();
        if (fresh.size() < perTest * 2) target = Math.max(target, all.size() + props.batchSize());
        if (all.size() < Math.min(target, props.maxPoolSize())) {
            refiller.refill(key, label, level, target);
        }

        List<PaperQuestion> qs = a.getItems().stream()
                .map(it -> new PaperQuestion(it.getId(), it.getPosition(), it.getQuestion().getText(), it.displayedOptions()))
                .toList();
        return new StartResponse(a.getId(), label, level, props.timeLimitMinutes() * 60, a.getStartedAt(), qs);
    }

    @Transactional
    public ResultResponse submit(String attemptId, Map<Long, Integer> answers) {
        Attempt a = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(404, "Test nahi mila. Page refresh karke dobara start karo."));
        if (a.isSubmitted()) {
            return toResult(a); // double-click / retry safe
        }
        int score = 0;
        for (AttemptItem it : a.getItems()) {
            Integer sel = answers == null ? null : answers.get(it.getId());
            if (sel != null && (sel < 0 || sel > 3)) sel = null;
            it.setSelectedDisplayIndex(sel);
            boolean ok = sel != null && sel == it.correctDisplayIndex();
            it.setCorrect(ok);
            if (ok) score++;
        }
        Instant now = Instant.now();
        a.setScore(score);
        a.setPercentage(a.getTotal() == 0 ? 0 : Math.round(score * 1000.0 / a.getTotal()) / 10.0);
        a.setSubmittedAt(now);
        a.setDurationSeconds((int) Math.min(Duration.between(a.getStartedAt(), now).getSeconds(), Integer.MAX_VALUE));
        a.setSubmitted(true);
        attempts.save(a);
        return toResult(a);
    }

    @Transactional(readOnly = true)
    public ResultResponse result(String attemptId) {
        Attempt a = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(404, "Result nahi mila."));
        if (!a.isSubmitted()) throw new ApiException(409, "Ye test abhi submit nahi hua hai.");
        return toResult(a);
    }

    @Transactional(readOnly = true)
    public List<LeaderRow> leaderboard(String topic, Level level, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        List<Attempt> rows;
        if (topic == null || topic.isBlank()) {
            rows = attempts.findBySubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(page);
        } else {
            String key = Topics.key(Topics.clean(topic));
            rows = level == null
                    ? attempts.findByTopicKeyAndSubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(key, page)
                    : attempts.findByTopicKeyAndLevelAndSubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(key, level, page);
        }
        return IntStream.range(0, rows.size()).mapToObj(i -> {
            Attempt a = rows.get(i);
            return new LeaderRow(i + 1, a.getEmpName(), a.getDepartment(), a.getTopicLabel(), a.getLevel(),
                    nz(a.getScore()), a.getTotal(), nzd(a.getPercentage()), nz(a.getDurationSeconds()), a.getSubmittedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryRow> history(String empId) {
        return attempts.findByEmpIdIgnoreCaseAndSubmittedTrueOrderBySubmittedAtDesc(empId.trim()).stream()
                .map(a -> new HistoryRow(a.getId(), a.getTopicLabel(), a.getLevel(), nz(a.getScore()), a.getTotal(),
                        nzd(a.getPercentage()), nz(a.getDurationSeconds()), a.getSubmittedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        List<TopicStat> popular = attempts.popularTopics(PageRequest.of(0, 12)).stream()
                .map(r -> new TopicStat((String) r[0], (String) r[1], ((Number) r[2]).longValue()))
                .toList();
        return new Stats(attempts.countBySubmittedTrue(), questions.count(), popular);
    }

    // ---------------- helpers ----------------

    private ResultResponse toResult(Attempt a) {
        List<ReviewItem> review = a.getItems().stream().map(it -> new ReviewItem(
                it.getPosition(),
                it.getQuestion().getText(),
                it.displayedOptions(),
                it.getSelectedDisplayIndex(),
                it.correctDisplayIndex(),
                Boolean.TRUE.equals(it.getCorrect()),
                it.getQuestion().getExplanation())).toList();

        // rank among everyone who took the same topic+level
        List<Attempt> board = attempts.findByTopicKeyAndLevelAndSubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(
                a.getTopicKey(), a.getLevel(), PageRequest.of(0, 1000));
        int rank = 0;
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).getId().equals(a.getId())) { rank = i + 1; break; }
        }
        return new ResultResponse(a.getId(), a.getEmpId(), a.getEmpName(), a.getDepartment(), a.getTopicLabel(),
                a.getLevel(), nz(a.getScore()), a.getTotal(), nzd(a.getPercentage()), nz(a.getDurationSeconds()),
                a.getSubmittedAt(), rank, board.size(), review);
    }

    /** random permutation of 0..3 as "2,0,3,1" */
    private String randomOrder() {
        List<Integer> idx = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(idx, random);
        return idx.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static double nzd(Double v) { return v == null ? 0 : v; }
}
