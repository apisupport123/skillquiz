package com.swami.skillquiz.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * How many AI calls this app made to one model on one (Pacific-time) day.
 * Stored in the DB so the count survives Render's free-tier sleep/restart.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    /** "2026-10-28|gemini-3.1-flash-lite" */
    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "usage_day", nullable = false)
    private LocalDate day;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(nullable = false)
    private int calls;

    /** true when Google itself said today's quota is used up */
    @Column(nullable = false)
    private boolean exhausted;

    protected AiUsage() {}

    public AiUsage(LocalDate day, String model) {
        this.id = day + "|" + model;
        this.day = day;
        this.model = model;
    }

    public String getId() { return id; }
    public LocalDate getDay() { return day; }
    public String getModel() { return model; }
    public int getCalls() { return calls; }
    public void setCalls(int calls) { this.calls = calls; }
    public boolean isExhausted() { return exhausted; }
    public void setExhausted(boolean exhausted) { this.exhausted = exhausted; }
}
