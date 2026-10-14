package com.likelion.newsbrief;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다. M3에서 Testcontainers로 바꾼다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsbriefApplicationTests {

  @LocalServerPort int port;

  @Test
  void probesAreUp() {
    RestClient client = RestClient.create("http://localhost:" + port);
    assertThat(client.get().uri("/actuator/health/liveness").retrieve().body(String.class))
        .contains("\"UP\"");
    assertThat(client.get().uri("/actuator/health/readiness").retrieve().body(String.class))
        .contains("\"UP\"");
  }
}
