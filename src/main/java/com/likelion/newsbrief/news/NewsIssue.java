package com.likelion.newsbrief.news;

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

/** 요약 배치가 만든 분야별 이슈. 같은 배치의 이슈는 generatedAt이 같다 */
@Entity
@Table(name = "news_issues")
public class NewsIssue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long issueId;

  @Enumerated(EnumType.STRING)
  private Category category;

  private Instant generatedAt;

  private int rankNo;

  private String title;

  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<Source> sources;

  private boolean mock;

  protected NewsIssue() {}

  public NewsIssue(
      Category category,
      Instant generatedAt,
      int rankNo,
      String title,
      String summary,
      List<Source> sources,
      boolean mock) {
    this.category = category;
    this.generatedAt = generatedAt;
    this.rankNo = rankNo;
    this.title = title;
    this.summary = summary;
    this.sources = sources;
    this.mock = mock;
  }

  public Long getIssueId() {
    return issueId;
  }

  public Category getCategory() {
    return category;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public int getRankNo() {
    return rankNo;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public List<Source> getSources() {
    return sources;
  }

  public boolean isMock() {
    return mock;
  }
}
