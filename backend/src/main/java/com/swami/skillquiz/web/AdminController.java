package com.swami.skillquiz.web;

import com.swami.skillquiz.config.QuizProperties;
import com.swami.skillquiz.model.Attempt;
import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import com.swami.skillquiz.repo.AttemptRepository;
import com.swami.skillquiz.repo.QuestionRepository;
import com.swami.skillquiz.service.ApiException;
import com.swami.skillquiz.service.Langs;
import com.swami.skillquiz.service.QuestionPoolService;
import com.swami.skillquiz.service.QuizService;
import com.swami.skillquiz.service.Topics;
import com.swami.skillquiz.web.Dtos.AdminAttemptDetail;
import com.swami.skillquiz.web.Dtos.AdminAttemptRow;
import com.swami.skillquiz.web.Dtos.AdminQuestionRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Everything here needs header X-Admin-Key = ADMIN_KEY env var. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final DateTimeFormatter IST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final QuizProperties props;
    private final QuestionPoolService pool;
    private final QuizService quiz;

    public AdminController(AttemptRepository attempts, QuestionRepository questions, QuizProperties props,
                           QuestionPoolService pool, QuizService quiz) {
        this.attempts = attempts;
        this.questions = questions;
        this.props = props;
        this.pool = pool;
        this.quiz = quiz;
    }

    /**
     * Delete test results. scope = before (date, IST) | employee (empId) | all.
     * dryRun=true only counts, so the UI can show "N results will be deleted" before confirming.
     * AI questions are never deleted here.
     */
    @DeleteMapping("/attempts")
    @Transactional
    public Map<String, Object> deleteAttempts(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                              @RequestParam String scope,
                                              @RequestParam(required = false) String before,
                                              @RequestParam(required = false) String empId,
                                              @RequestParam(defaultValue = "true") boolean dryRun) {
        auth(key);
        long count;
        switch (scope) {
            case "before" -> {
                java.time.Instant cutoff;
                try {
                    cutoff = java.time.LocalDate.parse(before == null ? "" : before)
                            .atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
                } catch (java.time.format.DateTimeParseException e) {
                    throw new ApiException(400, "BAD_DATE", "Give the date as YYYY-MM-DD.");
                }
                count = attempts.countByStartedAtBefore(cutoff);
                if (!dryRun && count > 0) {
                    attempts.deleteItemsBefore(cutoff);
                    attempts.deleteAttemptsBefore(cutoff);
                }
            }
            case "employee" -> {
                String id = empId == null ? "" : empId.trim();
                if (id.isEmpty()) throw new ApiException(400, "ID_REQUIRED", "Enter an ID.");
                count = attempts.countByEmpIdIgnoreCase(id);
                if (!dryRun && count > 0) {
                    attempts.deleteItemsOfEmployee(id);
                    attempts.deleteAttemptsOfEmployee(id);
                }
            }
            case "all" -> {
                count = attempts.count();
                if (!dryRun && count > 0) {
                    attempts.deleteAllItems();
                    attempts.deleteAllAttempts();
                }
            }
            default -> throw new ApiException(400, "BAD_REQUEST", "scope must be before, employee or all");
        }
        return Map.of("count", count, "deleted", !dryRun);
    }

    /**
     * Build a topic's question pool BEFORE test day, so employees use zero AI calls.
     * One call per level; run it the day before for each planned topic.
     */
    @PostMapping("/prepare")
    public Map<String, Object> prepare(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                       @RequestParam String topic, @RequestParam Level level,
                                       @RequestParam(defaultValue = "en") String lang) {
        auth(key);
        String cleaned = Topics.clean(topic);
        String k = Topics.key(cleaned);
        String label = Topics.label(cleaned);
        String l = Langs.normalize(lang);
        long before = pool.size(k, level, l);
        pool.ensureAtLeast(k, label, level, l, Math.max(props.minPoolSize(), props.questionsPerTest()));
        // one extra round toward the target size for more variety
        pool.topUp(k, label, level, l, props.targetPoolSize());
        long after = pool.size(k, level, l);
        return Map.of("topic", label, "level", level, "lang", l, "before", before, "after", after);
    }

    @GetMapping("/check")
    public String check(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        auth(key);
        return "ok";
    }

    /** every attempt, newest first - including tests that were started but not submitted */
    @GetMapping("/attempts")
    public List<AdminAttemptRow> attempts(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                          @RequestParam(defaultValue = "1000") int limit) {
        auth(key);
        return quiz.adminRows(limit);
    }

    /** everything about one attempt, incl. time spent per question */
    @GetMapping("/attempts/{id}")
    public AdminAttemptDetail attempt(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                      @PathVariable String id) {
        auth(key);
        return quiz.adminDetail(id);
    }

    /** Excel-friendly download of every attempt with full detail */
    @GetMapping(value = "/attempts.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> csv(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        auth(key);
        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM so Excel opens UTF-8 correctly
        sb.append("ID,Name,Team,Topic,Language,Level,Status,Score,Total,Correct,Wrong,Skipped,Percentage,Result,")
          .append("Started (IST),Finished (IST),Time taken (min),Time limit (min),How it ended,Warnings,Device,Opened from,Attempt ID\n");
        for (Attempt a : attempts.findAllByOrderByStartedAtDesc(PageRequest.of(0, 100_000))) {
            AdminAttemptRow r = quiz.adminRow(a);
            boolean done = "SUBMITTED".equals(r.status());
            sb.append(cell(r.empId())).append(',')
              .append(cell(r.empName())).append(',')
              .append(cell(r.department())).append(',')
              .append(cell(r.topic())).append(',')
              .append(Langs.of(r.lang()).english()).append(',')
              .append(levelLabel(r.level())).append(',')
              .append(statusLabel(r.status())).append(',')
              .append(done ? String.valueOf(r.score()) : "").append(',')
              .append(r.total()).append(',')
              .append(done ? String.valueOf(r.score()) : "").append(',')
              .append(done ? String.valueOf(r.wrong()) : "").append(',')
              .append(done ? String.valueOf(r.skipped()) : "").append(',')
              .append(done ? String.valueOf(r.percentage()) : "").append(',')
              .append(done ? (r.passed() ? "Pass" : "Fail") : "").append(',')
              .append(r.startedAt() == null ? "" : IST.format(r.startedAt())).append(',')
              .append(r.submittedAt() == null ? "" : IST.format(r.submittedAt())).append(',')
              .append(done ? String.format("%.1f", r.durationSeconds() / 60.0) : "").append(',')
              .append(r.timeLimitSeconds() / 60).append(',')
              .append(done ? endLabel(r.endReason()) : "").append(',')
              .append(r.warnings()).append(',')
              .append(cell(r.device())).append(',')
              .append(r.installedApp() == null ? "" : (r.installedApp() ? "App" : "Browser")).append(',')
              .append(r.attemptId())
              .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillquiz-results.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String levelLabel(Level l) {
        return switch (l) {
            case BEGINNER -> "Easy";
            case INTERMEDIATE -> "Medium";
            case ADVANCED -> "Hard";
        };
    }

    private static String statusLabel(String s) {
        return switch (s) {
            case "SUBMITTED" -> "Submitted";
            case "IN_PROGRESS" -> "In progress";
            default -> "Abandoned";
        };
    }

    /** See the AI questions for a topic so you can switch off wrong ones */
    @GetMapping("/questions")
    public List<AdminQuestionRow> questions(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                            @RequestParam String topic,
                                            @RequestParam(required = false) Level level,
                                            @RequestParam(required = false) String lang) {
        auth(key);
        String k = Topics.key(Topics.clean(topic));
        String l = lang == null || lang.isBlank() ? null : Langs.normalize(lang);
        return questions.findByTopicKeyOrderByIdDesc(k).stream()
                .filter(q -> level == null || q.getLevel() == level)
                .filter(q -> l == null || l.equals(q.getLang() == null ? "en" : q.getLang()))
                .map(q -> new AdminQuestionRow(q.getId(), q.getTopicKey(), q.getLevel(), q.getText(), q.options(),
                        q.getCorrectIndex(), q.getExplanation(), q.isActive(), q.getCreatedAt(),
                        q.getLang() == null ? "en" : q.getLang()))
                .toList();
    }

    @PatchMapping("/questions/{id}")
    public String toggle(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                         @PathVariable long id, @RequestParam boolean active) {
        auth(key);
        Question q = questions.findById(id).orElseThrow(() -> new ApiException(404, "QUESTION_NOT_FOUND", "Question not found."));
        q.setActive(active);
        questions.save(q);
        return "ok";
    }

    // ---------------- helpers ----------------

    private void auth(String key) {
        String expected = props.adminKey();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(403, "ADMIN_DISABLED", "Admin is turned off. Set ADMIN_KEY on the server.");
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (key == null ? "" : key).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            throw new ApiException(401, "ADMIN_WRONG", "Wrong admin password.");
        }
    }

    private static String endLabel(String r) {
        if (r == null) return "Submitted";
        return switch (r) {
            case "TIMEOUT" -> "Time over";
            case "TAB_SWITCH" -> "Auto-submitted: left exam screen";
            case "COPY" -> "Auto-submitted: tried to copy";
            case "CLOSED" -> "Auto-submitted: closed tab";
            default -> "Submitted";
        };
    }

    /** quote a CSV cell; also neutralise =,+,-,@ so Excel doesn't run formulas from user input */
    private static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
