package com.likelion.newsbrief.news;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** RSS로 모은 기사. 요약과 질문 답변의 근거. collected_at은 DB 기본값 */
@Entity
@Table(name = "news_articles")
public class NewsArticle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long articleId;

  @Enumerated(EnumType.STRING)
  private Category category;

  private String title;

  private String description;

  private String press;

  private String url;

  private Instant publishedAt;

  protected NewsArticle() {}

  public NewsArticle(
      Category category,
      String title,
      String description,
      String press,
      String url,
      Instant publishedAt) {
    this.category = category;
    this.title = title;
    this.description = description;
    this.press = press;
    this.url = url;
    this.publishedAt = publishedAt;
  }

  public Category getCategory() {
    return category;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getPress() {
    return press;
  }

  public String getUrl() {
    return url;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}
