package com.swami.skillquiz.repo;

import com.swami.skillquiz.model.Level;
import com.swami.skillquiz.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicKeyAndLevelAndLangAndActiveTrue(String topicKey, Level level, String lang);

    long countByTopicKeyAndLevelAndLangAndActiveTrue(String topicKey, Level level, String lang);

    List<Question> findByTopicKeyOrderByIdDesc(String topicKey);

    @Query("select q.fingerprint from Question q where q.topicKey = :topicKey and q.level = :level and q.lang = :lang")
    List<String> fingerprints(@Param("topicKey") String topicKey, @Param("level") Level level, @Param("lang") String lang);

    @Query("select q.text from Question q where q.topicKey = :topicKey and q.level = :level and q.lang = :lang")
    List<String> allTexts(@Param("topicKey") String topicKey, @Param("level") Level level, @Param("lang") String lang);

    @Query("select q.text from Question q where q.topicKey = :topicKey and q.level = :level and q.lang = :lang order by q.id desc")
    List<String> recentTexts(@Param("topicKey") String topicKey, @Param("level") Level level, @Param("lang") String lang,
                             org.springframework.data.domain.Pageable page);

    /** rows created before languages existed are English */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Question q set q.lang = 'en' where q.lang is null")
    int backfillLang();
}
