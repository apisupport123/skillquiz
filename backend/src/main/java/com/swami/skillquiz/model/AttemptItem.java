package com.swami.skillquiz.model;

import jakarta.persistence.*;

import java.util.Arrays;
import java.util.List;

/**
 * One question inside one attempt. Options are shuffled per person:
 * optionOrder "2,0,3,1" means the user sees original option C first, A second, ...
 */
@Entity
@Table(name = "attempt_items")
public class AttemptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "seq_no", nullable = false)
    private int position;

    @Column(nullable = false, length = 20)
    private String optionOrder;

    /** index the user clicked, in DISPLAYED order; null = skipped */
    private Integer selectedDisplayIndex;

    private Boolean correct;

    public List<Integer> order() {
        return Arrays.stream(optionOrder.split(",")).map(Integer::parseInt).toList();
    }

    /** where the correct answer appears on this person's screen */
    public int correctDisplayIndex() {
        return order().indexOf(question.getCorrectIndex());
    }

    public List<String> displayedOptions() {
        List<String> original = question.options();
        return order().stream().map(original::get).toList();
    }

    // --- getters / setters ---
    public Long getId() { return id; }
    public Attempt getAttempt() { return attempt; }
    public void setAttempt(Attempt attempt) { this.attempt = attempt; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getOptionOrder() { return optionOrder; }
    public void setOptionOrder(String optionOrder) { this.optionOrder = optionOrder; }
    public Integer getSelectedDisplayIndex() { return selectedDisplayIndex; }
    public void setSelectedDisplayIndex(Integer selectedDisplayIndex) { this.selectedDisplayIndex = selectedDisplayIndex; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
}
