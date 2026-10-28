package com.swami.skillquiz.service;

import com.swami.skillquiz.model.Level;

import java.util.List;

/** Something that can invent MCQs for any topic. Real one = Gemini, test one = Demo. */
public interface QuestionGenerator {

    /** lang = language code from Langs (en, hi, mr, ...) */
    Result generate(String topicLabel, Level level, String lang, int count, List<String> avoidQuestions);

    record Generated(String question, List<String> options, int correctIndex, String explanation) {}

    /** validTopic=false when the AI decides the input is not a real technology/skill */
    record Result(boolean validTopic, String reason, List<Generated> questions) {}
}
