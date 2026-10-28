package com.swami.skillquiz.web;

import com.swami.skillquiz.model.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swami.skillquiz.repo.QuestionRepository;
import com.swami.skillquiz.service.AiUsageService;
import com.swami.skillquiz.service.ApiException;
import com.swami.skillquiz.service.QuizService;
import com.swami.skillquiz.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quiz;
    private final QuestionRepository questions;
    private final AiUsageService aiUsage;
    private final ObjectMapper mapper;

    public QuizController(QuizService quiz, QuestionRepository questions, AiUsageService aiUsage, ObjectMapper mapper) {
        this.quiz = quiz;
        this.questions = questions;
        this.aiUsage = aiUsage;
        this.mapper = mapper;
    }

    /** which AI model is in use and how much of today's free limit is left */
    @GetMapping("/ai/status")
    public AiStatus aiStatus() {
        return aiUsage.status();
    }

    /** UI calls this on page load to wake Render up; also touches the DB so Supabase stays active */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "questions", questions.count());
    }

    @PostMapping("/quiz/start")
    public StartResponse start(@Valid @RequestBody StartRequest req,
                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return quiz.start(req, userAgent);
    }

    @PostMapping("/quiz/{attemptId}/submit")
    public ResultResponse submit(@PathVariable String attemptId, @Valid @RequestBody SubmitRequest req) {
        return quiz.submit(attemptId, req);
    }

    /**
     * Used by navigator.sendBeacon when the employee closes the tab / leaves the exam.
     * Beacons are sent as text/plain (no CORS preflight), so the JSON arrives as a string.
     */
    @PostMapping(value = "/quiz/{attemptId}/beacon", consumes = MediaType.ALL_VALUE)
    public void beacon(@PathVariable String attemptId, @RequestBody String body) {
        SubmitRequest req;
        try {
            req = mapper.readValue(body, SubmitRequest.class);
        } catch (Exception e) {
            throw new ApiException(400, "BAD_REQUEST", "The request was not in the right format.");
        }
        quiz.submit(attemptId, new SubmitRequest(req.answers() == null ? Map.of() : req.answers(),
                req.reason() == null ? "CLOSED" : req.reason(), req.timings(), req.changes(), req.warnings()));
    }

    @GetMapping("/quiz/{attemptId}/result")
    public ResultResponse result(@PathVariable String attemptId) {
        return quiz.result(attemptId);
    }

    /** days = last N days only (empty = all time); best = one row per employee (their best attempt) */
    @GetMapping("/leaderboard")
    public List<LeaderRow> leaderboard(@RequestParam(required = false) String topic,
                                       @RequestParam(required = false) Level level,
                                       @RequestParam(required = false) Integer days,
                                       @RequestParam(defaultValue = "true") boolean best,
                                       @RequestParam(defaultValue = "50") int limit) {
        return quiz.leaderboard(topic, level, days, best, limit);
    }

    @GetMapping("/employees/{empId}/history")
    public List<HistoryRow> history(@PathVariable String empId) {
        return quiz.history(empId);
    }

    @GetMapping("/stats")
    public Stats stats() {
        return quiz.stats();
    }
}
