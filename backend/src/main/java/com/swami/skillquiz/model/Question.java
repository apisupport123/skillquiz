package com.swami.skillquiz.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

/**
 * One AI-generated question in the shared pool for (topicKey, level).
 * The correct answer never leaves the server until the paper is submitted.
 */
@Entity
@Table(name = "questions",
        indexes = @Index(name = "idx_q_topic_level", columnList = "topic_key,quiz_level,active"))
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** normalized topic, e.g. "spring boot" */
    @Column(nullable = false, length = 60)
    private String topicKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_level", nullable = false, length = 20)
    private Level level;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String text;

    @Column(nullable = false, length = 400) private String optionA;
    @Column(nullable = false, length = 400) private String optionB;
    @Column(nullable = false, length = 400) private String optionC;
    @Column(nullable = false, length = 400) private String optionD;

    /** 0..3 index into A..D */
    @Column(nullable = false)
    private int correctIndex;

    @Column(length = 1000)
    private String explanation;

    /** used for duplicate detection */
    @Column(nullable = false, length = 300)
    private String fingerprint;

    /** admin can switch off a bad question */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public List<String> options() {
        return List.of(optionA, optionB, optionC, optionD);
    }

    // --- getters / setters ---
    public Long getId() { return id; }
    public String getTopicKey() { return topicKey; }
    public void setTopicKey(String topicKey) { this.topicKey = topicKey; }
    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }
    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }
    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }
    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }
    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
