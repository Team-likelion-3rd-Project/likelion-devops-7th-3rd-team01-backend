package com.likelion.newsbrief.news;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * api 파드가 트래픽을 받기 전에 조회 API를 미리 불러 JIT 컴파일·DB/Redis 연결·요약 캐시를 채운다 (성능 테스트 병목 후보 1: 파드 시작 직후 p95
 * 1.29초 → 예열 뒤 70ms). ApplicationRunner가 끝나야 readiness가 UP이 되므로 그동안 새 파드로 요청이 가지 않는다. 실패해도 시작은 계속한다
 */
@Component
@ConditionalOnExpression("'${app.role:}' == 'api'")
class NewsWarmUp implements ApplicationRunner {

  private static final Duration LIMIT = Duration.ofSeconds(30);

  private static final Logger log = LoggerFactory.getLogger(NewsWarmUp.class);

  private final Environment env;
  private final int rounds;

  NewsWarmUp(Environment env, @Value("${warmup.rounds:20}") int rounds) {
    this.env = env;
    this.rounds = rounds;
  }

  @Override
  public void run(ApplicationArguments args) {
    SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
    timeouts.setConnectTimeout(Duration.ofSeconds(2));
    timeouts.setReadTimeout(Duration.ofSeconds(5));
    RestClient client =
        RestClient.builder()
            .baseUrl("http://localhost:" + env.getProperty("local.server.port"))
            .requestFactory(timeouts)
            .build();
    Instant start = Instant.now();
    Instant deadline = start.plus(LIMIT);
    int calls = 0;
    try {
      for (int i = 0; i < rounds && Instant.now().isBefore(deadline); i++) {
        client.get().uri("/api/news/home").retrieve().toBodilessEntity();
        calls++;
        for (Category category : Category.values()) {
          client.get().uri("/api/news/categories/{c}", category).retrieve().toBodilessEntity();
          calls++;
        }
      }
      log.info(
          "조회 API 예열 완료: {}회, {}ms", calls, Duration.between(start, Instant.now()).toMillis());
    } catch (Exception e) {
      log.warn("조회 API 예열 중단 ({}회 후): {}", calls, e.toString());
    }
  }
}
