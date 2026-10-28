package com.swami.skillquiz.model;

public enum Level {
    // Shown in the app as Easy / Medium / Hard
    BEGINNER("easy: core facts, definitions and basic concepts a beginner should know"),
    INTERMEDIATE("medium: typical exam-level questions that need understanding and application, like most real papers"),
    ADVANCED("hard: tricky, multi-step or analytical questions, like the toughest previous-year questions or expert-level practice");

    private final String promptHint;

    Level(String promptHint) {
        this.promptHint = promptHint;
    }

    public String promptHint() {
        return promptHint;
    }
}
