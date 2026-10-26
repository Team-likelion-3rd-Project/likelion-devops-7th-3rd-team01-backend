package com.likelion.newsbrief.question;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 대기열(질문 ID). 사라져도 되는 데이터라 실패해도 질문은 DB에 PENDING으로 남는다 */
@Component
class QuestionQueue {

  static final String KEY = "questions:queue";

  private static final Logger log = LoggerFactory.getLogger(QuestionQueue.class);

  private final StringRedisTemplate redis;

  QuestionQueue(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** Redis가 죽어 있어도 예외를 내지 않는다. 30초 뒤 QuestionSweeper가 다시 넣는다 */
  void push(long questionId) {
    try {
      redis.opsForList().leftPush(KEY, String.valueOf(questionId));
    } catch (Exception e) {
      log.warn("질문 {} 대기열 등록 실패: {}", questionId, e.toString());
    }
  }

  /** 지표용. Redis가 죽어 있으면 NaN (Prometheus에 값 없음으로 보인다) */
  double size() {
    try {
      Long size = redis.opsForList().size(KEY);
      return size == null ? Double.NaN : size;
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /** 최대 timeout 동안 기다린다. 비어 있으면 null */
  Long pop(Duration timeout) {
    String value = redis.opsForList().rightPop(KEY, timeout);
    return value == null ? null : Long.valueOf(value);
  }
}
