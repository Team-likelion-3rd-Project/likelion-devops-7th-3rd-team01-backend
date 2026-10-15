package com.likelion.newsbrief.article;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.likelion.newsbrief.auth.JwtTokens;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다. M3에서 Testcontainers로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
class SavedArticleApiTests {

  private static final String URL = "https://www.etnews.com/20261015000031";

  private static final String ARTICLE =
      """
      {"press":"전자신문","title":"티오리, AI 해킹 도구 탐지 룰셋 무료 공개",
       "url":"%s","publishedAt":"2026-10-15T09:00:00+09:00"}\
      """
          .formatted(URL);

  @Autowired MockMvc mvc;

  @Test
  void saveListUnsave() throws Exception {
    Cookie me = signup();

    // 여러 번 저장해도 하나만 남는다
    save(me, ARTICLE).andExpect(status().isNoContent());
    save(me, ARTICLE).andExpect(status().isNoContent());

    mvc.perform(get("/api/saved-articles/urls").cookie(me))
        .andExpect(jsonPath("$.urls").value(Matchers.contains(URL)));
    mvc.perform(get("/api/mypage/saved-articles").cookie(me))
        .andExpect(jsonPath("$.articles.length()").value(1))
        .andExpect(jsonPath("$.articles[0].title").value("티오리, AI 해킹 도구 탐지 룰셋 무료 공개"))
        .andExpect(jsonPath("$.articles[0].press").value("전자신문"))
        .andExpect(jsonPath("$.articles[0].publishedAt").value("2026-10-15T09:00:00+09:00"))
        .andExpect(jsonPath("$.articles[0].savedAt").value(Matchers.endsWith("+09:00")))
        .andExpect(jsonPath("$.hasNext").value(false));

    // 다른 사용자에게는 보이지 않고, 지울 수도 없다
    Cookie other = signup();
    mvc.perform(get("/api/mypage/saved-articles").cookie(other))
        .andExpect(jsonPath("$.articles.length()").value(0));
    mvc.perform(delete("/api/saved-articles").param("url", URL).cookie(other))
        .andExpect(status().isNotFound());

    mvc.perform(delete("/api/saved-articles").param("url", URL).cookie(me))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/saved-articles/urls").cookie(me))
        .andExpect(jsonPath("$.urls.length()").value(0));
  }

  @Test
  void rejectsNonHttpUrl() throws Exception {
    // 화면에서 링크로 그리므로 javascript: 주소는 저장하지 않는다
    save(
            signup(),
            """
            {"title":"제목","url":"javascript:alert(1)"}\
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void requiresLogin() throws Exception {
    mvc.perform(put("/api/saved-articles").contentType(MediaType.APPLICATION_JSON).content(ARTICLE))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/mypage/saved-articles")).andExpect(status().isUnauthorized());
  }

  private ResultActions save(Cookie who, String body) throws Exception {
    return mvc.perform(
        put("/api/saved-articles")
            .cookie(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Cookie signup() throws Exception {
    String email = "a-" + UUID.randomUUID() + "@test.com";
    return mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"password1","nickname":"저장자"}\
                    """
                        .formatted(email)))
        .andReturn()
        .getResponse()
        .getCookie(JwtTokens.COOKIE);
  }
}
