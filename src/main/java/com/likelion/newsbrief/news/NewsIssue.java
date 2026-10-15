package com.likelion.newsbrief.news;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
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
  private List<String> keyPoints;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<TimelineEntry> timeline;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<Term> glossary;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<Source> sources;

  private boolean mock;

  /** 전개 흐름 한 줄. date는 YYYY-MM-DD (KST) */
  public record TimelineEntry(String date, String text) implements Serializable {

    /** 근거 기사의 발행일·제목으로 만든다 (오래된 순). AI가 기사에 없는 날짜·사건을 지어내지 않게 AI에게 맡기지 않는다. 발행일 없는 기사는 뺀다 */
    static List<TimelineEntry> from(List<Source> sources) {
      return sources.stream()
          .filter(s -> s.publishedAt() != null)
          .sorted(Comparator.comparing(Source::publishedAt))
          .map(
              s ->
                  new TimelineEntry(
                      s.publishedAt().atZoneSameInstant(Source.KST).toLocalDate().toString(),
                      s.title()))
          .toList();
    }
  }

  /** 용어 풀이 */
  public record Term(String term, String description) implements Serializable {}

  protected NewsIssue() {}

  public NewsIssue(
      Category category,
      Instant generatedAt,
      int rankNo,
      String title,
      String summary,
      List<String> keyPoints,
      List<Term> glossary,
      List<Source> sources,
      boolean mock) {
    this.category = category;
    this.generatedAt = generatedAt;
    this.rankNo = rankNo;
    this.title = title;
    this.summary = summary;
    this.keyPoints = keyPoints;
    this.timeline = TimelineEntry.from(sources);
    this.glossary = glossary;
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

  public List<String> getKeyPoints() {
    return keyPoints;
  }

  public List<TimelineEntry> getTimeline() {
    return timeline;
  }

  public List<Term> getGlossary() {
    return glossary;
  }

  public List<Source> getSources() {
    return sources;
  }

  public boolean isMock() {
    return mock;
  }
}
