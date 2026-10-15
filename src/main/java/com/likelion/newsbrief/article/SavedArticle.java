package com.likelion.newsbrief.article;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사용자가 저장한 근거 기사. 원본 기사는 7일 뒤 지워지므로 내용을 복사해 둔다. created_at(저장 시각)은 DB 기본값 */
@Entity
@Table(name = "saved_articles")
public class SavedArticle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long savedArticleId;

  private Long userId;

  private String title;

  private String url;

  private String press;

  private Instant publishedAt;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  protected SavedArticle() {}

  public SavedArticle(Long userId, String title, String url, String press, Instant publishedAt) {
    this.userId = userId;
    this.title = title;
    this.url = url;
    this.press = press;
    this.publishedAt = publishedAt;
  }

  public Long getSavedArticleId() {
    return savedArticleId;
  }

  public String getTitle() {
    return title;
  }

  public String getUrl() {
    return url;
  }

  public String getPress() {
    return press;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
