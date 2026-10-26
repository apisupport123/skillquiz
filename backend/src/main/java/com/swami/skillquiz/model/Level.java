package com.swami.skillquiz.model;

public enum Level {
    BEGINNER("basic concepts, syntax and common usage that someone with under 1 year experience should know"),
    INTERMEDIATE("practical, real-project scenarios, best practices, common pitfalls and debugging, for 2-5 years experience"),
    ADVANCED("internals, performance, architecture, edge cases and tricky behaviour, for senior engineers");

    private final String promptHint;

    Level(String promptHint) {
        this.promptHint = promptHint;
    }

    public String promptHint() {
        return promptHint;
    }
}
