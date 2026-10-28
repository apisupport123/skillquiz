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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuizService.class);

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
    public StartResponse start(StartRequest req, String userAgent) {
        String cleaned = Topics.clean(req.topic());
        String key = Topics.key(cleaned);
        String label = Topics.label(cleaned);
        Level level = req.level();
        int perTest = props.questionsPerTest();

        String empId = req.empId().trim();
        String lang = Langs.normalize(req.lang());

        // Blocking only when the pool is too small (first person on a new topic waits ~20-40s)
        pool.ensureAtLeast(key, label, level, lang, Math.max(props.minPoolSize(), perTest));

        List<Question> all = questions.findByTopicKeyAndLevelAndLangAndActiveTrue(key, level, lang);
        if (all.isEmpty()) {
            throw new ApiException(503, "NOT_READY", "Questions for this topic are not ready yet. Please try again shortly.");
        }
        Set<Long> seen = new HashSet<>(attempts.seenQuestionIds(empId, key, level));
        List<Question> fresh = new ArrayList<>(all.stream().filter(q -> !seen.contains(q.getId())).toList());

        // Retake: this employee must never get a question again. Ask AI for more if needed.
        if (fresh.size() < perTest && all.size() < props.maxPoolSize()) {
            int needed = Math.min(props.maxPoolSize(), all.size() + (perTest - fresh.size()) + 5);
            try {
                pool.ensureAtLeast(key, label, level, lang, needed);
            } catch (ApiException e) {
                log.info("Could not grow pool for retake of {} on {}/{}: {}", empId, key, level, e.getMessage());
            }
            all = questions.findByTopicKeyAndLevelAndLangAndActiveTrue(key, level, lang);
            fresh = new ArrayList<>(all.stream().filter(q -> !seen.contains(q.getId())).toList());
        }
        if (fresh.size() < perTest) {
            if (!seen.isEmpty()) {
                throw new ApiException(409, "ALL_SEEN",
                        "You have answered almost every question for " + label + " at this level. New ones could not be made right now; try another level or topic, or come back tomorrow.",
                        Map.of("topic", label));
            }
            // first attempt but pool smaller than one paper (AI limit hit mid-way): give what we have
        }

        Collections.shuffle(fresh, random);
        List<Question> paper = new ArrayList<>(fresh.subList(0, Math.min(perTest, fresh.size())));

        Attempt a = new Attempt();
        a.setEmpId(empId);
        a.setEmpName(req.empName().trim());
        a.setDepartment(req.department() == null ? null : req.department().trim());
        a.setTopicKey(key);
        a.setTopicLabel(label);
        a.setLevel(level);
        a.setTotal(paper.size());
        a.setTimeLimitSeconds(props.timeLimitMinutes() * 60);
        a.setLang(lang);
        a.setWarnings(0);
        a.setDevice(DeviceInfo.describe(userAgent));
        a.setInstalledApp(Boolean.TRUE.equals(req.installed()));
        a.setUserAgent(userAgent == null ? null : (userAgent.length() > 400 ? userAgent.substring(0, 400) : userAgent));
        for (int i = 0; i < paper.size(); i++) {
            AttemptItem item = new AttemptItem();
            item.setAttempt(a);
            item.setQuestion(paper.get(i));
            item.setPosition(i + 1);
            item.setOptionOrder(randomOrder());
            a.getItems().add(item);
        }
        attempts.save(a);

        // Grow the pool in the background (one AI call) so later people get more variety.
        // Retakes only grow it when this employee has nothing fresh left for a second paper.
        int target = props.targetPoolSize();
        if (fresh.size() - perTest < perTest) target = Math.max(target, all.size() + props.batchSize());
        if (all.size() < Math.min(target, props.maxPoolSize())) {
            refiller.refill(key, label, level, lang, target);
        }

        List<PaperQuestion> qs = a.getItems().stream()
                .map(it -> new PaperQuestion(it.getId(), it.getPosition(), it.getQuestion().getText(), it.displayedOptions()))
                .toList();
        return new StartResponse(a.getId(), label, level, props.timeLimitMinutes() * 60, a.getStartedAt(), qs, lang);
    }

    @Transactional
    public ResultResponse submit(String attemptId, SubmitRequest req) {
        Attempt a = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(404, "TEST_NOT_FOUND", "This test was not found. Refresh the page and start again."));
        if (a.isSubmitted()) {
            return toResult(a); // double-click / retry / beacon + normal submit: first one wins
        }
        Map<Long, Integer> answers = req.answers() == null ? Map.of() : req.answers();
        Map<Long, Integer> timings = req.timings() == null ? Map.of() : req.timings();
        Map<Long, Integer> changes = req.changes() == null ? Map.of() : req.changes();
        int score = 0, answered = 0;
        for (AttemptItem it : a.getItems()) {
            Integer sel = answers.get(it.getId());
            if (sel != null && (sel < 0 || sel > 3)) sel = null;
            it.setSelectedDisplayIndex(sel);
            boolean ok = sel != null && sel == it.correctDisplayIndex();
            it.setCorrect(ok);
            if (sel != null) answered++;
            if (ok) score++;
            Integer t = timings.get(it.getId());
            it.setTimeSpentSeconds(t == null ? null : Math.max(0, Math.min(t, 24 * 3600)));
            Integer c = changes.get(it.getId());
            it.setAnswerChanges(c == null ? null : Math.max(0, Math.min(c, 1000)));
        }
        Instant now = Instant.now();
        a.setScore(score);
        a.setAnsweredCount(answered);
        a.setWrongCount(answered - score);
        a.setSkippedCount(a.getTotal() - answered);
        a.setPercentage(a.getTotal() == 0 ? 0 : Math.round(score * 1000.0 / a.getTotal()) / 10.0);
        a.setSubmittedAt(now);
        a.setDurationSeconds((int) Math.min(Duration.between(a.getStartedAt(), now).getSeconds(), Integer.MAX_VALUE));
        a.setSubmitted(true);
        a.setEndReason(cleanReason(req.reason()));
        if (req.warnings() != null) a.setWarnings(Math.max(0, Math.min(req.warnings(), 100)));
        attempts.save(a);
        return toResult(a);
    }

    @Transactional(readOnly = true)
    public ResultResponse result(String attemptId) {
        Attempt a = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(404, "RESULT_NOT_FOUND", "Result not found."));
        if (!a.isSubmitted()) throw new ApiException(409, "NOT_SUBMITTED", "This test has not been submitted yet.");
        return toResult(a);
    }

    /**
     * days: only tests submitted in the last N days (null = all time).
     * best: keep only each employee's best attempt.
     */
    @Transactional(readOnly = true)
    public List<LeaderRow> leaderboard(String topic, Level level, Integer days, boolean best, int limit) {
        int max = Math.min(Math.max(limit, 1), 200);
        Specification<Attempt> spec = (root, q, cb) -> cb.isTrue(root.get("submitted"));
        if (topic != null && !topic.isBlank()) {
            String key = Topics.key(Topics.clean(topic));
            spec = spec.and((root, q, cb) -> cb.equal(root.get("topicKey"), key));
        }
        if (level != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("level"), level));
        }
        if (days != null && days > 0) {
            Instant from = Instant.now().minus(Duration.ofDays(days));
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("submittedAt"), from));
        }
        Sort sort = Sort.by(Sort.Order.desc("percentage"), Sort.Order.asc("durationSeconds"), Sort.Order.asc("submittedAt"));
        // fetch extra rows when keeping one per person, so the board still fills up
        List<Attempt> all = attempts.findAll(spec, PageRequest.of(0, best ? Math.min(max * 5, 2000) : max, sort)).getContent();
        List<Attempt> rows = new ArrayList<>();
        Set<String> people = new HashSet<>();
        for (Attempt a : all) {
            if (best && !people.add(a.getEmpId().toLowerCase())) continue;
            rows.add(a);
            if (rows.size() >= max) break;
        }
        return IntStream.range(0, rows.size()).mapToObj(i -> {
            Attempt a = rows.get(i);
            return new LeaderRow(i + 1, a.getEmpId(), a.getEmpName(), a.getDepartment(), a.getTopicLabel(), a.getLevel(),
                    nz(a.getScore()), a.getTotal(), nzd(a.getPercentage()), nz(a.getDurationSeconds()), a.getSubmittedAt(),
                    wrong(a), skipped(a), a.getStartedAt(), a.getEndReason(), passed(a), lang(a));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryRow> history(String empId) {
        return attempts.findByEmpIdIgnoreCaseAndSubmittedTrueOrderBySubmittedAtDesc(empId.trim()).stream()
                .map(a -> new HistoryRow(a.getId(), a.getTopicLabel(), a.getLevel(), nz(a.getScore()), a.getTotal(),
                        nzd(a.getPercentage()), nz(a.getDurationSeconds()), a.getSubmittedAt(),
                        a.getStartedAt(), wrong(a), skipped(a), a.getEndReason(), passed(a), lang(a)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        List<TopicStat> popular = attempts.popularTopics(PageRequest.of(0, 12)).stream()
                .map(r -> new TopicStat((String) r[0], (String) r[1], ((Number) r[2]).longValue()))
                .toList();
        return new Stats(attempts.countBySubmittedTrue(), questions.count(), props.questionsPerTest(),
                props.timeLimitMinutes(), popular, props.passPercent());
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
        int[] rank = rankOf(a);
        return new ResultResponse(a.getId(), a.getEmpId(), a.getEmpName(), a.getDepartment(), a.getTopicLabel(),
                a.getLevel(), nz(a.getScore()), a.getTotal(), nzd(a.getPercentage()), nz(a.getDurationSeconds()),
                a.getSubmittedAt(), rank[0], rank[1], a.getEndReason(), review,
                a.getStartedAt(), wrong(a), skipped(a), passed(a), props.passPercent(), lang(a));
    }

    /** [rank, outOf] among everyone who took the same topic+level */
    private int[] rankOf(Attempt a) {
        if (!a.isSubmitted()) return new int[]{0, 0};
        List<Attempt> board = attempts.findByTopicKeyAndLevelAndSubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(
                a.getTopicKey(), a.getLevel(), PageRequest.of(0, 1000));
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).getId().equals(a.getId())) return new int[]{i + 1, board.size()};
        }
        return new int[]{0, board.size()};
    }

    // ---------------- admin views ----------------

    @Transactional(readOnly = true)
    public List<AdminAttemptRow> adminRows(int limit) {
        return attempts.findAllByOrderByStartedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 5000)))
                .stream().map(this::adminRow).toList();
    }

    @Transactional(readOnly = true)
    public AdminAttemptDetail adminDetail(String attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> new ApiException(404, "TEST_NOT_FOUND", "This test was not found."));
        List<AdminReviewItem> review = a.getItems().stream().map(it -> new AdminReviewItem(
                it.getPosition(), it.getQuestion().getText(), it.displayedOptions(), it.getSelectedDisplayIndex(),
                it.correctDisplayIndex(), Boolean.TRUE.equals(it.getCorrect()), it.getQuestion().getExplanation(),
                it.getTimeSpentSeconds(), it.getAnswerChanges(), it.getQuestion().getId())).toList();
        int[] rank = rankOf(a);
        return new AdminAttemptDetail(adminRow(a), rank[0], rank[1], a.getUserAgent(), review);
    }

    public AdminAttemptRow adminRow(Attempt a) {
        return new AdminAttemptRow(a.getId(), a.getEmpId(), a.getEmpName(), a.getDepartment(), a.getTopicLabel(),
                a.getLevel(), nz(a.getScore()), a.getTotal(), nzd(a.getPercentage()), nz(a.getDurationSeconds()),
                a.getStartedAt(), a.getSubmittedAt(), a.isSubmitted() ? (a.getEndReason() == null ? "MANUAL" : a.getEndReason()) : null,
                status(a), nz(a.getAnsweredCount()), wrong(a), a.isSubmitted() ? skipped(a) : 0, passed(a),
                timeLimit(a), nz(a.getWarnings()), a.getDevice(), a.getInstalledApp(), lang(a));
    }

    /** SUBMITTED, IN_PROGRESS, or ABANDONED (time limit + 2 min passed and never submitted) */
    public String status(Attempt a) {
        if (a.isSubmitted()) return "SUBMITTED";
        Instant giveUp = a.getStartedAt().plusSeconds(timeLimit(a) + 120L);
        return Instant.now().isAfter(giveUp) ? "ABANDONED" : "IN_PROGRESS";
    }

    private static String lang(Attempt a) {
        return a.getLang() == null ? "en" : a.getLang();
    }

    private int timeLimit(Attempt a) {
        return a.getTimeLimitSeconds() != null ? a.getTimeLimitSeconds() : props.timeLimitMinutes() * 60;
    }

    /** older rows (before these fields existed) are filled in from what we know */
    private static int wrong(Attempt a) {
        if (a.getWrongCount() != null) return a.getWrongCount();
        if (!a.isSubmitted()) return 0;
        int answered = (int) a.getItems().stream().filter(i -> i.getSelectedDisplayIndex() != null).count();
        return answered - nz(a.getScore());
    }

    private static int skipped(Attempt a) {
        if (a.getSkippedCount() != null) return a.getSkippedCount();
        if (!a.isSubmitted()) return a.getTotal();
        return (int) a.getItems().stream().filter(i -> i.getSelectedDisplayIndex() == null).count();
    }

    private boolean passed(Attempt a) {
        return a.isSubmitted() && nzd(a.getPercentage()) >= props.passPercent();
    }

    private static final Set<String> REASONS = Set.of("MANUAL", "TIMEOUT", "TAB_SWITCH", "COPY", "CLOSED");

    private static String cleanReason(String r) {
        return r != null && REASONS.contains(r) ? r : "MANUAL";
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
