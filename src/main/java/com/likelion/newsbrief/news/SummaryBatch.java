package com.likelion.newsbrief.news;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 30분마다 CronJob(APP_ROLE=batch)으로 한 번 실행: 분야별 RSS 수집 → 요약 → DB 저장 → 캐시 비우기 */
@Component
public class SummaryBatch {

  static final int MAX_ISSUES = 5;

  // 게임처럼 하루 기사가 적은 분야도 이슈가 채워지도록 48시간
  private static final Duration ARTICLE_WINDOW = Duration.ofHours(48);
  private static final Duration RETENTION = Duration.ofDays(7);

  private static final Logger log = LoggerFactory.getLogger(SummaryBatch.class);

  private final RssCollector collector;
  private final AiClient ai;
  private final NewsArticleRepository articles;
  private final NewsIssueRepository issues;
  private final NewsCache cache;

  SummaryBatch(
      RssCollector collector,
      AiClient ai,
      NewsArticleRepository articles,
      NewsIssueRepository issues,
      NewsCache cache) {
    this.collector = collector;
    this.ai = ai;
    this.articles = articles;
    this.issues = issues;
    this.cache = cache;
  }

  /**
   * 한 분야가 실패해도 나머지는 계속한다. 실패한 분야는 새 요약을 만들지 않아 직전 요약이 그대로 보인다.
   *
   * @return 실패한 분야 수
   */
  public int run() {
    Instant now = Instant.now();
    int failed = 0;
    for (Category category : Category.collectOrder()) {
      try {
        int saved = collector.collect(category);
        int made = summarize(category, now);
        log.info("{}: 새 기사 {}건, 이슈 {}건", category, saved, made);
      } catch (Exception e) {
        failed++;
        log.error("{} 요약 갱신 실패. 직전 요약을 유지합니다.", category, e);
      }
    }
    articles.deleteCollectedBefore(now.minus(RETENTION));
    issues.deleteGeneratedBefore(now.minus(RETENTION));
    // 새 요약이 DB에 들어갔으니 캐시를 비운다. 다음 조회가 DB에서 새 요약을 읽어 다시 캐시한다
    cache.evictAll();
    return failed;
  }

  private int summarize(Category category, Instant now) {
    List<NewsArticle> recent =
        articles.findTop30ByCategoryAndPublishedAtAfterOrderByPublishedAtDesc(
            category, now.minus(ARTICLE_WINDOW));
    if (recent.isEmpty()) {
      return 0;
    }
    List<AiClient.IssueDraft> drafts = ai.summarize(category, recent, MAX_ISSUES);
    List<NewsIssue> batch =
        IntStream.range(0, Math.min(drafts.size(), MAX_ISSUES))
            .mapToObj(
                i -> {
                  AiClient.IssueDraft d = drafts.get(i);
                  return new NewsIssue(
                      category,
                      now,
                      i + 1,
                      d.title(),
                      d.summary(),
                      d.sources().stream().map(Source::of).toList(),
                      ai.isMock());
                })
            .toList();
    // saveAll은 한 트랜잭션이라 반쯤 만든 요약이 화면에 보이지 않는다
    issues.saveAll(batch);
    return batch.size();
  }
}
