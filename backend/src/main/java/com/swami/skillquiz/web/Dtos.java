package com.swami.skillquiz.web;

import com.swami.skillquiz.model.Level;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request / response shapes. Correct answers only appear in result objects, after submit. */
public final class Dtos {

    private Dtos() {}

    public record StartRequest(
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_./-]+$", message = "Employee ID mein sirf letters, numbers, - _ . / allowed hain")
            String empId,
            @NotBlank @Size(max = 80) String empName,
            @Size(max = 80) String department,
            @NotBlank @Size(max = 40) String topic,
            @NotNull Level level) {}

    public record PaperQuestion(long itemId, int number, String text, List<String> options) {}

    public record StartResponse(String attemptId, String topic, Level level, int timeLimitSeconds,
                                Instant startedAt, List<PaperQuestion> questions) {}

    /**
     * answers: itemId -> index (0..3) of the option as DISPLAYED to this user.
     * reason: why the exam ended - MANUAL, TIMEOUT, TAB_SWITCH, COPY, CLOSED (null = MANUAL)
     */
    public record SubmitRequest(@NotNull Map<Long, Integer> answers, String reason) {}

    public record ReviewItem(int number, String text, List<String> options, Integer selected,
                             int correct, boolean isCorrect, String explanation) {}

    public record ResultResponse(String attemptId, String empId, String empName, String department,
                                 String topic, Level level, int score, int total, double percentage,
                                 int durationSeconds, Instant submittedAt, int rank, int rankOutOf,
                                 String endReason, List<ReviewItem> review) {}

    /** public leaderboard row - no employee id */
    public record LeaderRow(int rank, String empName, String department, String topic, Level level,
                            int score, int total, double percentage, int durationSeconds, Instant submittedAt) {}

    public record HistoryRow(String attemptId, String topic, Level level, int score, int total,
                             double percentage, int durationSeconds, Instant submittedAt) {}

    public record TopicStat(String key, String label, long attempts) {}

    public record Stats(long totalTests, long totalQuestions, int questionsPerTest, int timeLimitMinutes,
                        List<TopicStat> popular) {}

    public record AiModelStatus(String model, int used, int limit, int remaining, boolean exhausted) {}

    /** activeModel = the model the next AI call will use (null = all used up today) */
    public record AiStatus(String activeModel, boolean demo, Instant resetsAt, List<AiModelStatus> models) {}

    /** admin only */
    public record AdminAttemptRow(String attemptId, String empId, String empName, String department,
                                  String topic, Level level, int score, int total, double percentage,
                                  int durationSeconds, Instant startedAt, Instant submittedAt, String endReason) {}

    public record AdminQuestionRow(long id, String topicKey, Level level, String text, List<String> options,
                                   int correctIndex, String explanation, boolean active, Instant createdAt) {}

    public record ErrorBody(String error) {}
}
