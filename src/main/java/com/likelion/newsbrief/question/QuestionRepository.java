package com.likelion.newsbrief.question;

import com.likelion.newsbrief.news.Source;
import com.likelion.newsbrief.question.Question.Status;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

  /** 본인 질문만. 남의 질문은 없는 것과 똑같이 보인다 */
  Optional<Question> findByQuestionIdAndUserId(Long questionId, Long userId);

  Slice<Question> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  Slice<Question> findByUserIdAndSavedAtNotNullOrderBySavedAtDesc(Long userId, Pageable pageable);

  /** 하루 질문 횟수. 실패(FAILED)한 질문은 세지 않는다 */
  @Query(
      "select count(q) from Question q where q.userId = :userId and q.createdAt >= :since and"
          + " q.status <> :excluded")
  long countSince(Long userId, Instant since, Status excluded);

  @Query("select q.questionId from Question q where q.status = :status and q.createdAt < :before")
  List<Long> findIdsCreatedBefore(Status status, Instant before);

  // ===== 사용자 =====

  @Transactional
  @Modifying
  @Query("delete from Question q where q.questionId = :id and q.userId = :userId")
  int deleteOwned(Long id, Long userId);

  /** 이미 저장돼 있으면 그대로 둔다. 본인 질문이 아니면 0 */
  @Transactional
  @Modifying
  @Query(
      "update Question q set q.savedAt = coalesce(q.savedAt, :now) where q.questionId = :id and"
          + " q.userId = :userId")
  int markSaved(Long id, Long userId, Instant now);

  @Transactional
  @Modifying
  @Query("update Question q set q.savedAt = null where q.questionId = :id and q.userId = :userId")
  int unmarkSaved(Long id, Long userId);

  // ===== 워커 =====

  /** PENDING → PROCESSING. 다른 워커가 먼저 가져갔으면 0 (같은 질문을 두 번 처리하지 않는다) */
  @Transactional
  @Modifying
  @Query(
      """
      update Question q set q.status = :to, q.startedAt = :now
      where q.questionId = :id and q.status = :from\
      """)
  int claim(Long id, Status from, Status to, Instant now);

  /** 처리 중인 질문만 끝낸다. 그사이 타임아웃·삭제됐으면 0 */
  @Transactional
  @Modifying
  @Query(
      """
      update Question q set q.status = :status, q.answer = :answer, q.sources = :sources, q.completedAt = :now
      where q.questionId = :id and q.status = :processing\
      """)
  int finish(
      Long id, Status processing, Status status, String answer, List<Source> sources, Instant now);

  // ===== 타임아웃 =====

  @Transactional
  @Modifying
  @Query(
      """
      update Question q set q.status = :failed, q.completedAt = :now
      where q.status in :unfinished and q.createdAt < :before\
      """)
  int timeOut(Collection<Status> unfinished, Status failed, Instant before, Instant now);
}
