package com.swami.skillquiz.repo;

import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicKeyAndLevelAndActiveTrue(String topicKey, Level level);

    long countByTopicKeyAndLevelAndActiveTrue(String topicKey, Level level);

    List<Question> findByTopicKeyOrderByIdDesc(String topicKey);

    @Query("select q.fingerprint from Question q where q.topicKey = :topicKey and q.level = :level")
    List<String> fingerprints(@Param("topicKey") String topicKey, @Param("level") Level level);

    @Query("select q.text from Question q where q.topicKey = :topicKey and q.level = :level order by q.id desc")
    List<String> recentTexts(@Param("topicKey") String topicKey, @Param("level") Level level,
                             org.springframework.data.domain.Pageable page);
}
