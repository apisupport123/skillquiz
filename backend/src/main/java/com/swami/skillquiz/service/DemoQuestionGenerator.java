package com.swami.skillquiz.service;

import com.swami.skillquiz.model.Level;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Local testing only (QUIZ_DEMO_MODE=true). Makes placeholder questions so you can
 * click through the whole app without an AI key. Never enable this in production.
 */
@Component
@ConditionalOnProperty(name = "quiz.demo-mode", havingValue = "true")
public class DemoQuestionGenerator implements QuestionGenerator {

    @Override
    public Result generate(String topicLabel, Level level, int count, List<String> avoid) {
        if (topicLabel.toLowerCase().contains("asdf")) {
            return new Result(false, "Not a real technology", List.of());
        }
        List<Generated> list = new ArrayList<>();
        int seed = ThreadLocalRandom.current().nextInt(100_000);
        for (int i = 0; i < count; i++) {
            int n = seed + i;
            list.add(new Generated(
                    "[DEMO] " + topicLabel + " (" + level + ") question #" + n + ": which statement is correct?",
                    List.of("Correct statement " + n, "Wrong statement A" + n, "Wrong statement B" + n, "Wrong statement C" + n),
                    0,
                    "Demo explanation for question #" + n));
        }
        return new Result(true, "", list);
    }
}
