package com.likelion.newsbrief.question;

import com.likelion.newsbrief.news.Source;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 질문 기록. 상태는 항상 DB가 기준이다. 생성 후의 변경은 모두 QuestionRepository의 조건부 UPDATE로 한다 (워커·타임아웃·사용자 저장이 서로 덮어쓰지
 * 않게).
 */
@Entity
@Table(name = "questions")
public class Question {

  public enum Status {
    PENDING,
    PROCESSING,
    COMPLETED,
    REJECTED,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long questionId;

  private Long userId;

  private String queryText;

  @Enumerated(EnumType.STRING)
  private Status status;

  private String answer;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<Source> sources;

  /** 질문 저장(별). null이면 저장 안 함 */
  private Instant savedAt;

  private Instant createdAt;

  private Instant startedAt;

  private Instant completedAt;

  protected Question() {}

  public Question(Long userId, String queryText, Instant createdAt) {
    this.userId = userId;
    this.queryText = queryText;
    this.status = Status.PENDING;
    this.createdAt = createdAt;
  }

  public Long getQuestionId() {
    return questionId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getQueryText() {
    return queryText;
  }

  public Status getStatus() {
    return status;
  }

  public String getAnswer() {
    return answer;
  }

  public List<Source> getSources() {
    return sources;
  }

  public Instant getSavedAt() {
    return savedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
