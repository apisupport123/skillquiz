package com.swami.skillquiz.web;

import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.repo.QuestionRepository;
import com.swami.skillquiz.service.QuizService;
import com.swami.skillquiz.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quiz;
    private final QuestionRepository questions;

    public QuizController(QuizService quiz, QuestionRepository questions) {
        this.quiz = quiz;
        this.questions = questions;
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
        return quiz.submit(attemptId, req.answers());
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
