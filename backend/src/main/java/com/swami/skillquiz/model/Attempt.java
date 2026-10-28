package com.swami.skillquiz.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One test taken by one employee. Every attempt is kept (retakes allowed). */
@Entity
@Table(name = "attempts",
        indexes = {
                @Index(name = "idx_a_emp", columnList = "emp_id"),
                @Index(name = "idx_a_topic", columnList = "topic_key,quiz_level")
        })
public class Attempt implements Persistable<String> {

    /** UUID so nobody can guess another person's attempt id */
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 30)
    private String empId;

    @Column(nullable = false, length = 80)
    private String empName;

    @Column(length = 80)
    private String department;

    @Column(nullable = false, length = 60)
    private String topicKey;

    /** what the user typed, for display ("Spring Boot") */
    @Column(nullable = false, length = 60)
    private String topicLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_level", nullable = false, length = 20)
    private Level level;

    @Column(nullable = false)
    private int total;

    private Integer score;
    private Double percentage;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant submittedAt;
    private Integer durationSeconds;

    @Column(nullable = false)
    private boolean submitted = false;

    /** MANUAL, TIMEOUT, TAB_SWITCH, COPY, CLOSED */
    @Column(length = 20)
    private String endReason;

    // ----- extra detail for leaderboard / admin -----
    private Integer answeredCount;
    private Integer wrongCount;
    private Integer skippedCount;
    /** time limit that applied when this test started */
    private Integer timeLimitSeconds;
    /** how many times the person left the exam screen and got a warning */
    private Integer warnings;
    /** e.g. "Android, Chrome" */
    @Column(length = 60)
    private String device;
    /** true = opened from the installed app, false = in a browser */
    private Boolean installedApp;
    @Column(length = 400)
    private String userAgent;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<AttemptItem> items = new ArrayList<>();

    /** id is assigned in Java (UUID), so tell Spring Data when the row is new -> persist, not merge */
    @Transient
    private boolean isNew = true;

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }

    @Override
    public boolean isNew() { return isNew; }

    // --- getters / setters ---
    @Override
    public String getId() { return id; }
    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }
    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTopicKey() { return topicKey; }
    public void setTopicKey(String topicKey) { this.topicKey = topicKey; }
    public String getTopicLabel() { return topicLabel; }
    public void setTopicLabel(String topicLabel) { this.topicLabel = topicLabel; }
    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Double getPercentage() { return percentage; }
    public void setPercentage(Double percentage) { this.percentage = percentage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public boolean isSubmitted() { return submitted; }
    public String getEndReason() { return endReason; }
    public Integer getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(Integer v) { this.answeredCount = v; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer v) { this.wrongCount = v; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer v) { this.skippedCount = v; }
    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(Integer v) { this.timeLimitSeconds = v; }
    public Integer getWarnings() { return warnings; }
    public void setWarnings(Integer v) { this.warnings = v; }
    public String getDevice() { return device; }
    public void setDevice(String v) { this.device = v; }
    public Boolean getInstalledApp() { return installedApp; }
    public void setInstalledApp(Boolean v) { this.installedApp = v; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { this.userAgent = v; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    public List<AttemptItem> getItems() { return items; }
}
