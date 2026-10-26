package com.likelion.newsbrief.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.likelion.newsbrief.auth.JwtTokens;
import com.likelion.newsbrief.question.Question.Status;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다. M3에서 Testcontainers로 바꾼다
// APP_ROLE이 없으면 워커도 같은 프로세스에서 돌아서, 질문이 실제로 처리되는 것까지 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class QuestionApiTests {

  @Autowired MockMvc mvc;

  @Autowired QuestionRepository questions;

  @Autowired QuestionProcessor processor;

  @Test
  void askSaveListDelete() throws Exception {
    Cookie me = signup();
    long id = ask(me, "반도체 수출 전망 알려줘");

    String done = waitUntilFinished(me, id);
    assertThat(JsonPath.<String>read(done, "$.status")).isEqualTo("COMPLETED");
    assertThat(JsonPath.<String>read(done, "$.answer")).isNotBlank();
    assertThat(JsonPath.<Boolean>read(done, "$.saved")).isFalse();

    // 남의 질문은 없는 질문과 똑같이 404
    Cookie other = signup();
    mvc.perform(get("/api/questions/" + id).cookie(other)).andExpect(status().isNotFound());
    mvc.perform(put("/api/questions/" + id + "/saved").cookie(other))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/questions/" + id).cookie(other)).andExpect(status().isNotFound());

    // 저장은 여러 번 해도 같다
    mvc.perform(put("/api/questions/" + id + "/saved").cookie(me))
        .andExpect(status().isNoContent());
    mvc.perform(put("/api/questions/" + id + "/saved").cookie(me))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/questions/" + id).cookie(me)).andExpect(jsonPath("$.saved").value(true));
    mvc.perform(get("/api/mypage/saved-questions").cookie(me))
        .andExpect(jsonPath("$.questions[0].questionId").value(id))
        .andExpect(jsonPath("$.hasNext").value(false));

    mvc.perform(delete("/api/questions/" + id + "/saved").cookie(me))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/mypage/saved-questions").cookie(me))
        .andExpect(jsonPath("$.questions.length()").value(0));
    mvc.perform(get("/api/mypage/questions").cookie(me))
        .andExpect(jsonPath("$.questions[0].questionId").value(id));

    mvc.perform(delete("/api/questions/" + id).cookie(me)).andExpect(status().isNoContent());
    mvc.perform(get("/api/questions/" + id).cookie(me)).andExpect(status().isNotFound());
  }

  @Test
  void offTopicIsRejected() throws Exception {
    Cookie me = signup();
    long id = ask(me, "이 문장 영어로 번역해줘");
    assertThat(JsonPath.<String>read(waitUntilFinished(me, id), "$.status")).isEqualTo("REJECTED");
  }

  @Test
  void dailyLimitSkipsFailedQuestions() throws Exception {
    Cookie me = signup();
    long userId =
        JsonPath.<Integer>read(
            mvc.perform(get("/api/auth/me").cookie(me))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.userId");
    // 실패한 질문은 하루 10회에 들어가지 않는다
    for (int i = 0; i < 3; i++) {
      Question failed = new Question(userId, "실패한 질문", Instant.now());
      ReflectionTestUtils.setField(failed, "status", Status.FAILED);
      questions.save(failed);
    }
    for (int i = 0; i < QuestionService.DAILY_LIMIT; i++) {
      ask(me, "질문 " + i);
    }
    mvc.perform(
            post("/api/questions")
                .cookie(me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"query":"11번째 질문"}\
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  void workerShutdownReleasesUnfinishedQuestion() throws Exception {
    Cookie me = signup();
    long userId =
        JsonPath.<Integer>read(
            mvc.perform(get("/api/auth/me").cookie(me))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.userId");
    // 종료 중인 워커가 처리하다 만 질문
    Question stuck = new Question(userId, "반도체 수출 전망 알려줘", Instant.now());
    ReflectionTestUtils.setField(stuck, "status", Status.PROCESSING);
    long id = questions.save(stuck).getQuestionId();

    processor.release(id);

    // PENDING으로 돌아가 대기열에 들어갔으니 (같은 프로세스의) 다른 워커가 끝낸다
    String done = waitUntilFinished(me, id);
    assertThat(JsonPath.<String>read(done, "$.status")).isEqualTo("COMPLETED");
  }

  @Test
  void validation() throws Exception {
    Cookie me = signup();
    mvc.perform(
            post("/api/questions")
                .cookie(me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"query":"  "}\
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(
            post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"query":"로그인 없이"}\
                    """))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/questions/abc").cookie(me)).andExpect(status().isBadRequest());
  }

  private Cookie signup() throws Exception {
    String email = "q-" + UUID.randomUUID() + "@test.com";
    return mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"password1","nickname":"질문자"}\
                    """
                        .formatted(email)))
        .andReturn()
        .getResponse()
        .getCookie(JwtTokens.COOKIE);
  }

  private long ask(Cookie me, String query) throws Exception {
    String body =
        mvc.perform(
                post("/api/questions")
                    .cookie(me)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"%s\"}".formatted(query)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<Integer>read(body, "$.questionId");
  }

  /** FE처럼 결과를 폴링한다 (테스트라 0.2초 간격) */
  private String waitUntilFinished(Cookie me, long id) throws Exception {
    for (int i = 0; i < 50; i++) {
      String body =
          mvc.perform(get("/api/questions/" + id).cookie(me))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String status = JsonPath.read(body, "$.status");
      if (!status.equals("PENDING") && !status.equals("PROCESSING")) {
        return body;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("질문이 10초 안에 끝나지 않았습니다.");
  }
}
