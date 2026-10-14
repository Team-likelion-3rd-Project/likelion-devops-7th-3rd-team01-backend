package com.likelion.newsbrief;

import com.likelion.newsbrief.news.SummaryBatch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NewsbriefApplication {

  public static void main(String[] args) {
    // APP_ROLE=batch: 웹 서버 없이 요약을 한 번 만들고 끝낸다 (CronJob). 한 분야라도 실패하면 종료 코드 1
    if ("batch".equals(System.getenv("APP_ROLE"))) {
      SpringApplication app = new SpringApplication(NewsbriefApplication.class);
      app.setWebApplicationType(WebApplicationType.NONE);
      ConfigurableApplicationContext context = app.run(args);
      int failed = context.getBean(SummaryBatch.class).run();
      System.exit(SpringApplication.exit(context, () -> failed == 0 ? 0 : 1));
    }
    SpringApplication.run(NewsbriefApplication.class, args);
  }
}
