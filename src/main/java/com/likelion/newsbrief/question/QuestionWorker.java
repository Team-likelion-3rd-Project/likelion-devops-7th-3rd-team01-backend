package com.likelion.newsbrief.question;

import java.time.Duration;
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

  private static final Logger log = LoggerFactory.getLogger(QuestionWorker.class);

  private final QuestionQueue queue;
  private final QuestionProcessor processor;
  private final int threads;

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
          processor.process(id);
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
      executor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
