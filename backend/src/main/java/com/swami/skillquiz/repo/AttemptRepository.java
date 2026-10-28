package com.swami.skillquiz.repo;

import com.swami.skillquiz.model.Attempt;
import com.swami.skillquiz.model.Level;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, String>, JpaSpecificationExecutor<Attempt> {

    /** every attempt, including ones that were started but never submitted (admin view) */
    List<Attempt> findAllByOrderByStartedAtDesc(Pageable page);

    List<Attempt> findByEmpIdIgnoreCaseAndSubmittedTrueOrderBySubmittedAtDesc(String empId);

    List<Attempt> findBySubmittedTrueOrderBySubmittedAtDesc(Pageable page);

    // Leaderboard: best % first, then fastest. Three variants instead of "(:x is null or ...)"
    // because Postgres cannot infer the type of a null parameter.
    List<Attempt> findBySubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(Pageable page);

    List<Attempt> findByTopicKeyAndSubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(
            String topicKey, Pageable page);

    List<Attempt> findByTopicKeyAndLevelAndSubmittedTrueOrderByPercentageDescDurationSecondsAscSubmittedAtAsc(
            String topicKey, Level level, Pageable page);

    /** question ids this employee has already seen for a topic+level (to avoid repeats) */
    @Query("""
           select i.question.id from AttemptItem i
           where lower(i.attempt.empId) = lower(:empId)
             and i.attempt.topicKey = :topicKey
             and i.attempt.level = :level
           """)
    List<Long> seenQuestionIds(@Param("empId") String empId,
                               @Param("topicKey") String topicKey,
                               @Param("level") Level level);

    /** popular topics for the chips on the home page: [topicLabel, count] */
    @Query("""
           select a.topicKey, max(a.topicLabel), count(a)
           from Attempt a where a.submitted = true
           group by a.topicKey order by count(a) desc
           """)
    List<Object[]> popularTopics(Pageable page);

    long countBySubmittedTrue();

    /** rows created before languages existed are English */
    @Modifying
    @Query("update Attempt a set a.lang = 'en' where a.lang is null")
    int backfillLang();

    // ---------- admin delete (answers first, then the attempts) ----------

    long countByStartedAtBefore(Instant before);

    long countByEmpIdIgnoreCase(String empId);

    @Modifying
    @Query("delete from AttemptItem i where i.attempt.id in (select a.id from Attempt a where a.startedAt < :before)")
    int deleteItemsBefore(@Param("before") Instant before);

    @Modifying
    @Query("delete from Attempt a where a.startedAt < :before")
    int deleteAttemptsBefore(@Param("before") Instant before);

    @Modifying
    @Query("delete from AttemptItem i where i.attempt.id in (select a.id from Attempt a where lower(a.empId) = lower(:empId))")
    int deleteItemsOfEmployee(@Param("empId") String empId);

    @Modifying
    @Query("delete from Attempt a where lower(a.empId) = lower(:empId)")
    int deleteAttemptsOfEmployee(@Param("empId") String empId);

    @Modifying
    @Query("delete from AttemptItem i")
    int deleteAllItems();

    @Modifying
    @Query("delete from Attempt a")
    int deleteAllAttempts();
}
