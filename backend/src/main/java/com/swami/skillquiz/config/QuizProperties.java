package com.swami.skillquiz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables live here so you can change them from Render env vars
 * without touching code. Example: QUIZ_QUESTIONS_PER_TEST=15
 */
@ConfigurationProperties(prefix = "quiz")
public record QuizProperties(
        int questionsPerTest,      // questions in one paper
        int timeLimitMinutes,      // countdown shown on UI; server records duration
        int passPercent,           // score at or above this % = Pass
        int minPoolSize,           // pool must have at least this many before a paper is drawn
        int targetPoolSize,        // background top-up aims for this
        int maxPoolSize,           // stop generating after this
        int batchSize,             // questions asked from AI per call
        int maxGenerationsPerMinute, // protects the free-tier AI rate limit
        String adminKey,           // password for admin page
        String allowedOrigins,     // comma separated, e.g. https://yourname.github.io
        boolean demoMode           // true = no AI, fake questions (local testing only)
) {}
