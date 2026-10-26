package com.swami.skillquiz.service;

import com.swami.skillquiz.model.Level;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Separate bean so @Async actually runs in the background (Spring proxies don't work on self-calls). */
@Component
public class PoolRefiller {

    private final QuestionPoolService pool;

    public PoolRefiller(QuestionPoolService pool) {
        this.pool = pool;
    }

    @Async
    public void refill(String topicKey, String topicLabel, Level level, int target) {
        pool.topUp(topicKey, topicLabel, level, target);
    }
}
