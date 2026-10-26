package com.likelion.newsbrief.news;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SummaryBatch가 Redis에 남긴 마지막 실행 결과를 API 서버의 지표로 보여 준다 (M5-10). 아직 한 번도 안 돌았거나 Redis가 죽어 있으면
 * NaN
 */
@Component
@ConditionalOnExpression("'${app.role:}' == '' or '${app.role:}' == 'api'")
class BatchMetrics {

  private final StringRedisTemplate redis;

  BatchMetrics(StringRedisTemplate redis, MeterRegistry meters) {
    this.redis = redis;
    Gauge.builder("newsbrief.batch.last.duration", () -> read("durationSeconds"))
        .baseUnit("seconds")
        .description("마지막 요약 배치 소요 시간")
        .register(meters);
    Gauge.builder("newsbrief.batch.last.finished", () -> read("finishedAt"))
        .description("마지막 요약 배치가 끝난 시각 (epoch 초)")
        .register(meters);
    Gauge.builder("newsbrief.batch.last.failed.categories", () -> read("failedCategories"))
        .description("마지막 요약 배치에서 실패한 분야 수")
        .register(meters);
  }

  private double read(String field) {
    try {
      Object value = redis.opsForHash().get(SummaryBatch.LAST_RUN_KEY, field);
      return value == null ? Double.NaN : Double.parseDouble(value.toString());
    } catch (Exception e) {
      return Double.NaN;
    }
  }
}
