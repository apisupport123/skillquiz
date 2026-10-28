package com.swami.skillquiz.web;

import com.swami.skillquiz.config.QuizProperties;
import com.swami.skillquiz.model.Attempt;
import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import com.swami.skillquiz.repo.AttemptRepository;
import com.swami.skillquiz.repo.QuestionRepository;
import com.swami.skillquiz.service.ApiException;
import com.swami.skillquiz.service.QuestionPoolService;
import com.swami.skillquiz.service.Topics;
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

    public AdminController(AttemptRepository attempts, QuestionRepository questions, QuizProperties props,
                           QuestionPoolService pool) {
        this.attempts = attempts;
        this.questions = questions;
        this.props = props;
        this.pool = pool;
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
                    throw new ApiException(400, "Date sahi format mein do (YYYY-MM-DD).");
                }
                count = attempts.countByStartedAtBefore(cutoff);
                if (!dryRun && count > 0) {
                    attempts.deleteItemsBefore(cutoff);
                    attempts.deleteAttemptsBefore(cutoff);
                }
            }
            case "employee" -> {
                String id = empId == null ? "" : empId.trim();
                if (id.isEmpty()) throw new ApiException(400, "Employee ID daalo.");
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
            default -> throw new ApiException(400, "scope must be before, employee or all");
        }
        return Map.of("count", count, "deleted", !dryRun);
    }

    /**
     * Build a topic's question pool BEFORE test day, so employees use zero AI calls.
     * One call per level; run it the day before for each planned topic.
     */
    @PostMapping("/prepare")
    public Map<String, Object> prepare(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                       @RequestParam String topic, @RequestParam Level level) {
        auth(key);
        String cleaned = Topics.clean(topic);
        String k = Topics.key(cleaned);
        String label = Topics.label(cleaned);
        long before = pool.size(k, level);
        pool.ensureAtLeast(k, label, level, Math.max(props.minPoolSize(), props.questionsPerTest()));
        // one extra round toward the target size for more variety
        pool.topUp(k, label, level, props.targetPoolSize());
        long after = pool.size(k, level);
        return Map.of("topic", label, "level", level, "before", before, "after", after);
    }

    @GetMapping("/check")
    public String check(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        auth(key);
        return "ok";
    }

    @GetMapping("/attempts")
    @Transactional(readOnly = true)
    public List<AdminAttemptRow> attempts(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                          @RequestParam(defaultValue = "500") int limit) {
        auth(key);
        return attempts.findBySubmittedTrueOrderBySubmittedAtDesc(PageRequest.of(0, Math.min(limit, 5000)))
                .stream().map(AdminController::row).toList();
    }

    /** Excel-friendly download of all results */
    @GetMapping(value = "/attempts.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> csv(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        auth(key);
        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel opens UTF-8 correctly
        sb.append("Employee ID,Name,Department,Topic,Level,Score,Total,Percentage,Time (min),Submitted (IST),How it ended\n");
        for (Attempt a : attempts.findBySubmittedTrueOrderBySubmittedAtDesc(PageRequest.of(0, 100_000))) {
            sb.append(cell(a.getEmpId())).append(',')
              .append(cell(a.getEmpName())).append(',')
              .append(cell(a.getDepartment())).append(',')
              .append(cell(a.getTopicLabel())).append(',')
              .append(a.getLevel()).append(',')
              .append(a.getScore()).append(',')
              .append(a.getTotal()).append(',')
              .append(a.getPercentage()).append(',')
              .append(String.format("%.1f", (a.getDurationSeconds() == null ? 0 : a.getDurationSeconds()) / 60.0)).append(',')
              .append(a.getSubmittedAt() == null ? "" : IST.format(a.getSubmittedAt())).append(',')
              .append(endLabel(a.getEndReason()))
              .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillquiz-results.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** See the AI questions for a topic so you can switch off wrong ones */
    @GetMapping("/questions")
    public List<AdminQuestionRow> questions(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                            @RequestParam String topic,
                                            @RequestParam(required = false) Level level) {
        auth(key);
        String k = Topics.key(Topics.clean(topic));
        return questions.findByTopicKeyOrderByIdDesc(k).stream()
                .filter(q -> level == null || q.getLevel() == level)
                .map(q -> new AdminQuestionRow(q.getId(), q.getTopicKey(), q.getLevel(), q.getText(), q.options(),
                        q.getCorrectIndex(), q.getExplanation(), q.isActive(), q.getCreatedAt()))
                .toList();
    }

    @PatchMapping("/questions/{id}")
    public String toggle(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                         @PathVariable long id, @RequestParam boolean active) {
        auth(key);
        Question q = questions.findById(id).orElseThrow(() -> new ApiException(404, "Question nahi mila"));
        q.setActive(active);
        questions.save(q);
        return "ok";
    }

    // ---------------- helpers ----------------

    private void auth(String key) {
        String expected = props.adminKey();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(403, "Admin disabled hai. Server pe ADMIN_KEY set karo.");
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (key == null ? "" : key).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            throw new ApiException(401, "Admin password galat hai.");
        }
    }

    private static AdminAttemptRow row(Attempt a) {
        return new AdminAttemptRow(a.getId(), a.getEmpId(), a.getEmpName(), a.getDepartment(), a.getTopicLabel(),
                a.getLevel(), a.getScore() == null ? 0 : a.getScore(), a.getTotal(),
                a.getPercentage() == null ? 0 : a.getPercentage(),
                a.getDurationSeconds() == null ? 0 : a.getDurationSeconds(), a.getStartedAt(), a.getSubmittedAt(),
                a.getEndReason() == null ? "MANUAL" : a.getEndReason());
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
