package com.likelion.newsbrief.question;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 대기열에서 질문을 꺼내 처리한다. APP_ROLE=worker(쿠버네티스, KEDA로 확장) 또는 APP_ROLE 없음(로컬·compose 한 프로세스)일 때 켜진다. 종료
 * 신호를 받으면 새 질문은 꺼내지 않고 처리 중인 질문을 끝낸 뒤 내려간다.
 */
@Component
@ConditionalOnExpression("'${app.role:}' == '' or '${app.role:}' == 'worker'")
class QuestionWorker implements SmartLifecycle {

  private static final Duration POLL = Duration.ofSeconds(1);
  // 워커 terminationGracePeriodSeconds(60초)보다 짧게. 평소 답변은 10초 안에 끝난다
  private static final Duration STOP_WAIT = Duration.ofSeconds(40);

  private static final Logger log = LoggerFactory.getLogger(QuestionWorker.class);

  private final QuestionQueue queue;
  private final QuestionProcessor processor;
  private final int threads;

  private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

  private volatile boolean running;
  private ExecutorService executor;

  QuestionWorker(
      QuestionQueue queue, QuestionProcessor processor, @Value("${worker.threads:4}") int threads) {
    this.queue = queue;
    this.processor = processor;
    this.threads = threads;
  }

  @Override
  public void start() {
    running = true;
    executor = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      executor.submit(this::loop);
    }
    log.info("질문 워커 시작 (스레드 {}개)", threads);
  }

  private void loop() {
    while (running) {
      try {
        Long id = queue.pop(POLL);
        if (id != null) {
          inFlight.add(id);
          try {
            processor.process(id);
          } finally {
            inFlight.remove(id);
          }
        }
      } catch (Exception e) {
        // Redis 장애 등. 잠시 쉬고 다시 시도한다
        log.warn("질문 워커 오류: {}", e.toString());
        sleep();
      }
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(POLL.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void stop() {
    running = false;
    executor.shutdown();
    try {
      if (!executor.awaitTermination(STOP_WAIT.toSeconds(), TimeUnit.SECONDS)) {
        // 기다려도 안 끝난 질문(AI 응답이 느림)은 PENDING으로 돌려 다른 워커가 처리하게 한다.
        // 그냥 죽으면 PROCESSING으로 남아 60초 뒤 FAILED가 된다
        inFlight.forEach(processor::release);
        log.warn(
            "종료 대기 {}초 초과, 처리 중이던 질문 {}건을 대기열로 돌려놓았습니다.",
            STOP_WAIT.toSeconds(),
            inFlight.size());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
