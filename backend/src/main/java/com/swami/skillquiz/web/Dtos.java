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
            @Pattern(regexp = "^[A-Za-z0-9_./-]+$", message = "ID_CHARS")
            String empId,
            @NotBlank @Size(max = 80) String empName,
            @Size(max = 80) String department,
            @NotBlank @Size(max = 60) String topic,
            @NotNull Level level,
            Boolean installed,
            String lang) {}

    public record PaperQuestion(long itemId, int number, String text, List<String> options) {}

    public record StartResponse(String attemptId, String topic, Level level, int timeLimitSeconds,
                                Instant startedAt, List<PaperQuestion> questions, String lang) {}

    /**
     * answers: itemId -> index (0..3) of the option as DISPLAYED to this user.
     * reason: why the exam ended - MANUAL, TIMEOUT, TAB_SWITCH, COPY, CLOSED (null = MANUAL)
     */
    public record SubmitRequest(@NotNull Map<Long, Integer> answers, String reason,
                                Map<Long, Integer> timings, Map<Long, Integer> changes, Integer warnings) {}

    public record ReviewItem(int number, String text, List<String> options, Integer selected,
                             int correct, boolean isCorrect, String explanation) {}

    public record ResultResponse(String attemptId, String empId, String empName, String department,
                                 String topic, Level level, int score, int total, double percentage,
                                 int durationSeconds, Instant submittedAt, int rank, int rankOutOf,
                                 String endReason, List<ReviewItem> review,
                                 Instant startedAt, int wrong, int skipped, boolean passed, int passPercent, String lang) {}

    /** leaderboard row (visible to everyone who has the link) */
    public record LeaderRow(int rank, String empId, String empName, String department, String topic, Level level,
                            int score, int total, double percentage, int durationSeconds, Instant submittedAt,
                            int wrong, int skipped, Instant startedAt, String endReason, boolean passed, String lang) {}

    public record HistoryRow(String attemptId, String topic, Level level, int score, int total,
                             double percentage, int durationSeconds, Instant submittedAt,
                             Instant startedAt, int wrong, int skipped, String endReason, boolean passed, String lang) {}

    public record TopicStat(String key, String label, long attempts) {}

    public record Stats(long totalTests, long totalQuestions, int questionsPerTest, int timeLimitMinutes,
                        List<TopicStat> popular, int passPercent) {}

    public record AiModelStatus(String model, int used, int limit, int remaining, boolean exhausted) {}

    /** activeModel = the model the next AI call will use (null = all used up today) */
    public record AiStatus(String activeModel, boolean demo, Instant resetsAt, List<AiModelStatus> models) {}

    /** admin only. status = SUBMITTED, IN_PROGRESS or ABANDONED (started but never submitted) */
    public record AdminAttemptRow(String attemptId, String empId, String empName, String department,
                                  String topic, Level level, int score, int total, double percentage,
                                  int durationSeconds, Instant startedAt, Instant submittedAt, String endReason,
                                  String status, int answered, int wrong, int skipped, boolean passed,
                                  int timeLimitSeconds, int warnings, String device, Boolean installedApp, String lang) {}

    public record AdminReviewItem(int number, String text, List<String> options, Integer selected, int correct,
                                  boolean isCorrect, String explanation, Integer timeSpentSeconds,
                                  Integer answerChanges, long questionId) {}

    public record AdminAttemptDetail(AdminAttemptRow row, int rank, int rankOutOf, String userAgent,
                                     List<AdminReviewItem> review) {}

    public record AdminQuestionRow(long id, String topicKey, Level level, String text, List<String> options,
                                   int correctIndex, String explanation, boolean active, Instant createdAt, String lang) {}

    /** error = English text, code = stable key the app translates, params = values for {placeholders} */
    public record ErrorBody(String error, String code, Map<String, String> params) {}
}
