package com.likelion.newsbrief.news;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다
// M5-10 확장 기준 지표가 등록되고 값이 나오는지 (Prometheus로 내보내는 건 설정만이라 여기서는 안 본다)
@SpringBootTest
class MetricsTests {

  @Autowired MeterRegistry meters;

  @Autowired StringRedisTemplate redis;

  @AfterEach
  void cleanUp() {
    redis.delete(SummaryBatch.LAST_RUN_KEY);
  }

  @Test
  void questionMetrics() {
    assertThat(meters.get("newsbrief.questions.queue.length").gauge().value())
        .isGreaterThanOrEqualTo(0);
    assertThat(meters.get("newsbrief.questions.failed").tag("reason", "error").counter())
        .isNotNull();
    assertThat(meters.get("newsbrief.questions.failed").tag("reason", "timeout").counter())
        .isNotNull();
  }

  @Test
  void batchMetricsReadLastRunFromRedis() {
    redis.delete(SummaryBatch.LAST_RUN_KEY);
    assertThat(meters.get("newsbrief.batch.last.duration").gauge().value()).isNaN();

    // 배치(다른 파드)가 남긴 기록
    redis
        .opsForHash()
        .putAll(
            SummaryBatch.LAST_RUN_KEY,
            Map.of("durationSeconds", "87.5", "finishedAt", "1761200000", "failedCategories", "1"));

    assertThat(meters.get("newsbrief.batch.last.duration").gauge().value()).isEqualTo(87.5);
    assertThat(meters.get("newsbrief.batch.last.finished").gauge().value()).isEqualTo(1761200000);
    assertThat(meters.get("newsbrief.batch.last.failed.categories").gauge().value()).isEqualTo(1);
  }
}
