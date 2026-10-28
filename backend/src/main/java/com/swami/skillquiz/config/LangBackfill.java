package com.swami.skillquiz.config;

import com.swami.skillquiz.repo.AttemptRepository;
import com.swami.skillquiz.repo.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-time on startup: questions and tests saved before languages existed are marked English. */
@Component
public class LangBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LangBackfill.class);

    private final QuestionRepository questions;
    private final AttemptRepository attempts;

    public LangBackfill(QuestionRepository questions, AttemptRepository attempts) {
        this.questions = questions;
        this.attempts = attempts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int q = questions.backfillLang();
        int a = attempts.backfillLang();
        if (q + a > 0) log.info("Marked {} questions and {} tests as English", q, a);
    }
}
