package com.likelion.newsbrief.news;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 30분마다 CronJob(APP_ROLE=batch)으로 한 번 실행: 분야별 RSS 수집 → 요약 → DB 저장 → 캐시 덮어쓰기 */
@Component
public class SummaryBatch {

  static final int MAX_ISSUES = 5;
  static final String LAST_RUN_KEY = "batch:last-run";

  // 하루 기사가 적은 분야도 이슈가 채워지도록 48시간
  private static final Duration ARTICLE_WINDOW = Duration.ofHours(48);
  private static final Duration RETENTION = Duration.ofDays(7);

  private static final Logger log = LoggerFactory.getLogger(SummaryBatch.class);

  private final RssCollector collector;
  private final AiClient ai;
  private final NewsArticleRepository articles;
  private final NewsIssueRepository issues;
  // 캐시에 넣을 응답 모양을 컨트롤러가 갖고 있어 그대로 쓴다. batch에도 컨트롤러 빈은 만들어진다 (웹 서버만 없음)
  private final NewsController news;
  private final StringRedisTemplate redis;

  SummaryBatch(
      RssCollector collector,
      AiClient ai,
      NewsArticleRepository articles,
      NewsIssueRepository issues,
      NewsController news,
      StringRedisTemplate redis) {
    this.collector = collector;
    this.ai = ai;
    this.articles = articles;
    this.issues = issues;
    this.news = news;
    this.redis = redis;
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
        List<NewsIssue> latest = issues.findLatest(category);
        if (isCurrent(latest, category)) {
          issues.markChecked(category, latest.get(0).getGeneratedAt(), now);
          log.info("{}: 새 기사 0건, 직전 요약 유지 (AI 호출 안 함)", category);
          continue;
        }
        int made = summarize(category, now);
        log.info("{}: 새 기사 {}건, 이슈 {}건", category, saved, made);
      } catch (Exception e) {
        failed++;
        log.error("{} 요약 갱신 실패. 직전 요약을 유지합니다.", category, e);
      }
    }
    articles.deleteCollectedBefore(now.minus(RETENTION));
    issues.deleteGeneratedBefore(now.minus(RETENTION));
    // 새 요약이 DB에 들어갔으니 캐시를 새 응답으로 덮어쓴다
    news.refreshCache();
    recordLastRun(Duration.between(now, Instant.now()), failed);
    return failed;
  }

  /**
   * CronJob 파드는 끝나면 사라져 Prometheus가 지표를 가져갈 수 없다. 마지막 실행 결과를 Redis에 남기면 API 서버가 지표로 보여 준다
   * (BatchMetrics). Redis가 죽어 있어도 배치는 성공으로 끝낸다
   */
  private void recordLastRun(Duration took, int failed) {
    try {
      redis
          .opsForHash()
          .putAll(
              LAST_RUN_KEY,
              Map.of(
                  "durationSeconds", String.valueOf(took.toMillis() / 1000.0),
                  "finishedAt", String.valueOf(Instant.now().getEpochSecond()),
                  "failedCategories", String.valueOf(failed)));
    } catch (Exception e) {
      log.warn("배치 실행 기록 저장 실패: {}", e.toString());
    }
  }

  /**
   * 마지막 요약 뒤에 들어온 기사가 없으면 다시 요약해도 같은 내용이라 실제 AI 비용만 든다. 이번 회차의 새 기사 수가 아니라 마지막 요약과 비교해서, 요약
   * 도중 AI 실패나 Pod 삭제로 끝난 회차의 기사도 다음 배치가 다시 요약한다 (장애 시험 B3·B9). 요약이 아직 없거나 AI 모드가 바뀌었으면(mock 요약이
   * 남은 채 bedrock으로 전환 등) 다시 만든다.
   */
  // ponytail: 48시간 창에서 기사가 빠지기만 한 경우는 다음 새 기사가 들어올 때까지 갱신되지 않는다
  private boolean isCurrent(List<NewsIssue> latest, Category category) {
    return !latest.isEmpty()
        && latest.get(0).isMock() == ai.isMock()
        && articles.countCollectedAfterLatestSummary(category.name()) == 0;
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
                      d.keyPoints(),
                      d.glossary(),
                      d.sources().stream().map(Source::of).toList(),
                      ai.isMock());
                })
            .toList();
    // saveAll은 한 트랜잭션이라 반쯤 만든 요약이 화면에 보이지 않는다
    issues.saveAll(batch);
    return batch.size();
  }
}
