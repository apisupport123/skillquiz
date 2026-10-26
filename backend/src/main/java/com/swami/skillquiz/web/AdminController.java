package com.swami.skillquiz.web;

import com.swami.skillquiz.config.QuizProperties;
import com.swami.skillquiz.model.Attempt;
import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import com.swami.skillquiz.repo.AttemptRepository;
import com.swami.skillquiz.repo.QuestionRepository;
import com.swami.skillquiz.service.ApiException;
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

/** Everything here needs header X-Admin-Key = ADMIN_KEY env var. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final DateTimeFormatter IST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final QuizProperties props;

    public AdminController(AttemptRepository attempts, QuestionRepository questions, QuizProperties props) {
        this.attempts = attempts;
        this.questions = questions;
        this.props = props;
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
        sb.append("Employee ID,Name,Department,Topic,Level,Score,Total,Percentage,Time (min),Submitted (IST)\n");
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
              .append(a.getSubmittedAt() == null ? "" : IST.format(a.getSubmittedAt()))
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
                a.getDurationSeconds() == null ? 0 : a.getDurationSeconds(), a.getStartedAt(), a.getSubmittedAt());
    }

    /** quote a CSV cell; also neutralise =,+,-,@ so Excel doesn't run formulas from user input */
    private static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
