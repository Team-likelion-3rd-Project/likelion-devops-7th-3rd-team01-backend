package com.likelion.newsbrief.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다
// 새 기사가 없으면 실제 AI를 다시 부르지 않는다 (AWS에서 bedrock을 켜 둔 채 30분마다 같은 요약을 만들던 비용)
@SpringBootTest
@Transactional
class SummaryBatchTests {

  @Autowired SummaryBatch batch;

  @Autowired NewsIssueRepository issues;

  @Autowired NewsArticleRepository articles;

  @Autowired JdbcTemplate jdbc;

  @Autowired NewsCache cache;

  @Autowired StringRedisTemplate redis;

  // RSS를 실제로 읽지 않는다. 기본값 0 = 모든 분야 새 기사 없음
  @MockitoBean RssCollector collector;

  @MockitoSpyBean AiClient ai;

  @BeforeEach
  void recentArticle() {
    // 요약할 기사가 있어야 AI를 부르는 경로까지 간다
    articles.save(
        new NewsArticle(
            Category.AI,
            "제목",
            "설명",
            "전자신문",
            "https://x.com/" + UUID.randomUUID(),
            Instant.now()));
  }

  @Test
  void noNewArticlesKeepsCurrentSummary() {
    saveLatestIssue(true); // 테스트는 AI_MODE=mock

    batch.run();

    verify(ai, never()).summarize(eq(Category.AI), anyList(), anyInt());
    // 건너뛴 분야도 generatedAt이 이번 배치 시각이 되어 홈에 갱신 지연이 뜨지 않는다 (직전 값은 하루 뒤)
    assertThat(issues.findLatest(Category.AI).get(0).getGeneratedAt())
        .isBefore(Instant.now().plus(1, ChronoUnit.HOURS));
  }

  @Test
  void aiModeChangedResummarizesEvenWithoutNewArticles() {
    saveLatestIssue(false); // bedrock으로 만든 요약이 남은 채 mock으로 바뀜

    batch.run();

    verify(ai).summarize(eq(Category.AI), anyList(), anyInt());
  }

  @Test
  void articleCollectedAfterLastSummaryIsSummarizedOnRetry() {
    // 직전 회차가 기사만 저장하고 요약 전에 죽음 (B9): 이번 회차의 새 기사는 0건이지만 요약은 기사보다 오래됐다
    saveLatestIssue(true);
    jdbc.update("update news_issues set created_at = created_at - interval 1 day");

    batch.run();

    verify(ai).summarize(eq(Category.AI), anyList(), anyInt());
  }

  @Test
  void batchFillsCacheInsteadOfEmptyingIt() {
    // 배치가 끝난 순간 캐시가 비어 있으면 모든 조회가 DB로 몰린다 (HPA 스파이크 시험 10/29)
    cache.evictAll();

    batch.run();

    assertThat(redis.hasKey(NewsCache.homeKey())).isTrue();
    assertThat(redis.hasKey(NewsCache.categoryKey(Category.AI))).isTrue();
    cache.evictAll(); // 되돌린 테스트 데이터가 캐시에 남지 않게
  }

  private void saveLatestIssue(boolean mock) {
    issues.saveAndFlush(
        new NewsIssue(
            Category.AI,
            // DB(DATETIME)는 초 단위라, 같은 트랜잭션에서 비교할 값도 초로 맞춘다
            Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS),
            1,
            "직전 이슈",
            "요약",
            List.of("핵심"),
            List.of(),
            List.of(),
            mock));
  }
}
