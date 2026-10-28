package com.swami.skillquiz.service;

import com.swami.skillquiz.model.AiUsage;
import com.swami.skillquiz.repo.AiUsageRepository;
import com.swami.skillquiz.web.Dtos.AiModelStatus;
import com.swami.skillquiz.web.Dtos.AiStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Counts AI calls per model per day and knows each model's free daily limit.
 * Google does not expose "remaining quota" through the API, so this is the app's own count
 * (accurate as long as this API key is used only by this app).
 */
@Service
public class AiUsageService {

    /** Google resets the free daily quota at midnight Pacific time (~12:30 / 13:30 IST). */
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    private final AiUsageRepository repo;
    private final List<String> models;
    private final Map<String, Integer> limits = new LinkedHashMap<>();
    private final boolean demo;

    public AiUsageService(AiUsageRepository repo,
                          @Value("${gemini.models:gemini-3.1-flash-lite,gemini-3.6-flash}") String models,
                          @Value("${gemini.daily-limits:gemini-3.1-flash-lite:500,gemini-3.6-flash:20}") String dailyLimits,
                          @Value("${quiz.demo-mode:false}") boolean demo) {
        this.repo = repo;
        this.demo = demo;
        this.models = Arrays.stream(models.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (String pair : dailyLimits.split(",")) {
            int i = pair.lastIndexOf(':');
            if (i > 0) {
                try {
                    limits.put(pair.substring(0, i).trim(), Integer.parseInt(pair.substring(i + 1).trim()));
                } catch (NumberFormatException ignored) { /* bad config entry: skip */ }
            }
        }
    }

    public List<String> models() {
        return models;
    }

    public int limit(String model) {
        return limits.getOrDefault(model, 20); // unknown model: assume the strict free limit
    }

    /** false once our own count hits the limit, or Google said the day's quota is gone */
    public synchronized boolean canUse(String model) {
        AiUsage u = repo.findById(id(model)).orElse(null);
        return u == null || (!u.isExhausted() && u.getCalls() < limit(model));
    }

    public synchronized void recordCall(String model) {
        AiUsage u = repo.findById(id(model)).orElseGet(() -> new AiUsage(today(), model));
        u.setCalls(u.getCalls() + 1);
        repo.save(u);
    }

    public synchronized void markExhausted(String model) {
        AiUsage u = repo.findById(id(model)).orElseGet(() -> new AiUsage(today(), model));
        u.setExhausted(true);
        repo.save(u);
    }

    public AiStatus status() {
        List<AiModelStatus> rows = new ArrayList<>();
        String active = null;
        for (String m : models) {
            AiUsage u = repo.findById(id(m)).orElse(null);
            int used = u == null ? 0 : u.getCalls();
            int limit = limit(m);
            boolean exhausted = u != null && (u.isExhausted() || used >= limit);
            int remaining = exhausted ? 0 : Math.max(0, limit - used);
            if (active == null && !exhausted) active = m;
            rows.add(new AiModelStatus(m, used, limit, remaining, exhausted));
        }
        return new AiStatus(demo ? "demo" : active, demo, nextReset(), rows);
    }

    private static LocalDate today() {
        return ZonedDateTime.now(PACIFIC).toLocalDate();
    }

    private static String id(String model) {
        return today() + "|" + model;
    }

    private static Instant nextReset() {
        return today().plusDays(1).atStartOfDay(PACIFIC).toInstant();
    }
}
