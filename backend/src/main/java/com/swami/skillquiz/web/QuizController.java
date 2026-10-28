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
    public StartResponse start(@Valid @RequestBody StartRequest req) {
        return quiz.start(req);
    }

    @PostMapping("/quiz/{attemptId}/submit")
    public ResultResponse submit(@PathVariable String attemptId, @Valid @RequestBody SubmitRequest req) {
        return quiz.submit(attemptId, req.answers(), req.reason());
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
            throw new ApiException(400, "Request sahi format mein nahi hai.");
        }
        quiz.submit(attemptId, req.answers() == null ? java.util.Map.of() : req.answers(),
                req.reason() == null ? "CLOSED" : req.reason());
    }

    @GetMapping("/quiz/{attemptId}/result")
    public ResultResponse result(@PathVariable String attemptId) {
        return quiz.result(attemptId);
    }

    @GetMapping("/leaderboard")
    public List<LeaderRow> leaderboard(@RequestParam(required = false) String topic,
                                       @RequestParam(required = false) Level level,
                                       @RequestParam(defaultValue = "20") int limit) {
        return quiz.leaderboard(topic, level, limit);
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
