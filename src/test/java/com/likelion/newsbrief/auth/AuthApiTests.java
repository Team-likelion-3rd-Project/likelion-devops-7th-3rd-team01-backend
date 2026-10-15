package com.likelion.newsbrief.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다. M3에서 Testcontainers로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

  @Autowired MockMvc mvc;

  @Test
  void signupMeLoginLogout() throws Exception {
    String email = "t-" + UUID.randomUUID() + "@test.com";
    String signup =
        """
        {"email":"%s","password":"password1","nickname":"테스터"}\
        """
            .formatted(email);

    // 가입하면 바로 로그인 쿠키가 나온다
    Cookie cookie =
        mvc.perform(json(post("/api/auth/signup"), signup))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
            .andReturn()
            .getResponse()
            .getCookie(JwtTokens.COOKIE);

    mvc.perform(get("/api/auth/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("테스터"));

    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(get("/api/auth/me").cookie(new Cookie(JwtTokens.COOKIE, "broken")))
        .andExpect(status().isUnauthorized());

    mvc.perform(json(post("/api/auth/signup"), signup))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

    mvc.perform(
            json(
                post("/api/auth/login"),
                """
                {"email":"%s","password":"wrong-password"}\
                """
                    .formatted(email)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    // 깨진 쿠키가 남아 있어도 공개 API(로그인)는 막히지 않는다
    mvc.perform(
            json(
                    post("/api/auth/login"),
                    """
                    {"email":"%s","password":"password1"}\
                    """
                        .formatted(email))
                .cookie(new Cookie(JwtTokens.COOKIE, "broken")))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.SET_COOKIE));

    mvc.perform(post("/api/auth/logout"))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
  }

  @Test
  void signupValidation() throws Exception {
    mvc.perform(
            json(
                post("/api/auth/signup"),
                """
                {"email":"not-an-email","password":"short","nickname":""}\
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void unknownPathIs404() throws Exception {
    mvc.perform(get("/api/nothing-here"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
