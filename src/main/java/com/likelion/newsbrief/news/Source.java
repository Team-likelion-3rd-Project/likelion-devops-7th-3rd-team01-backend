package com.likelion.newsbrief.news;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 근거 기사. 요약(news_issues)과 질문(questions)의 sources JSON에 API 응답 형식 그대로 저장한다. 뉴스 응답과 함께 Redis에 캐시되므로
 * Serializable.
 */
public record Source(String press, String title, String url, OffsetDateTime publishedAt)
    implements Serializable {

  public static final ZoneOffset KST = ZoneOffset.ofHours(9);

  public static Source of(NewsArticle article) {
    return new Source(
        article.getPress(),
        article.getTitle(),
        article.getUrl(),
        article.getPublishedAt() == null ? null : article.getPublishedAt().atOffset(KST));
  }

  /** DB의 JSON에서 읽으면 UTC(Z)로 바뀌어 있어 응답 전에 +09:00으로 되돌린다 */
  public Source inKst() {
    return new Source(
        press, title, url, publishedAt == null ? null : publishedAt.withOffsetSameInstant(KST));
  }
}
