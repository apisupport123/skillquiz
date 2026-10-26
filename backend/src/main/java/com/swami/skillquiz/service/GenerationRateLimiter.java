package com.swami.skillquiz.service;

import com.swami.skillquiz.config.QuizProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps us under the AI free-tier requests-per-minute. Sliding 60-second window.
 * Single Render instance, so in-memory is enough.
 */
@Component
public class GenerationRateLimiter {

    private final int maxPerMinute;
    private final Deque<Long> calls = new ArrayDeque<>();

    public GenerationRateLimiter(QuizProperties props) {
        this.maxPerMinute = Math.max(1, props.maxGenerationsPerMinute());
    }

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        while (!calls.isEmpty() && now - calls.peekFirst() > 60_000) {
            calls.pollFirst();
        }
        if (calls.size() >= maxPerMinute) return false;
        calls.addLast(now);
        return true;
    }
}
