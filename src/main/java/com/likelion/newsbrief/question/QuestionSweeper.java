package com.likelion.newsbrief.question;

import com.likelion.newsbrief.question.Question.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * API 서버(레플리카 2개 이상, 항상 떠 있음)에서 10초마다 돈다. 워커는 KEDA로 0개가 될 수 있어 여기 둔다. 여러 레플리카가 동시에 돌아도 괜찮다 (조건부
 * UPDATE, 중복 재투입은 claim이 걸러 냄).
 */
@Component
@ConditionalOnExpression("'${app.role:}' == '' or '${app.role:}' == 'api'")
class QuestionSweeper {

  private static final Duration REQUEUE_AFTER = Duration.ofSeconds(30);
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private static final Logger log = LoggerFactory.getLogger(QuestionSweeper.class);

  private final QuestionRepository questions;
  private final QuestionQueue queue;

  QuestionSweeper(QuestionRepository questions, QuestionQueue queue) {
    this.questions = questions;
    this.queue = queue;
  }

  @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
  void sweep() {
    Instant now = Instant.now();
    int timedOut =
        questions.timeOut(
            List.of(Status.PENDING, Status.PROCESSING), Status.FAILED, now.minus(TIMEOUT), now);
    if (timedOut > 0) {
      log.warn("60초 안에 끝나지 않은 질문 {}건을 FAILED로 바꿨습니다.", timedOut);
    }
    // Redis 장애 전환 등으로 대기열에서 사라진 질문을 다시 넣는다
    questions.findIdsCreatedBefore(Status.PENDING, now.minus(REQUEUE_AFTER)).forEach(queue::push);
  }
}
