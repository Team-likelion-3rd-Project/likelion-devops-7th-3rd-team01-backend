package com.likelion.newsbrief.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** AWS 운영 설정(화면 app.{도메인}, API api.{도메인}, HTTPS)으로 띄웠을 때 CORS·쿠키가 운영 기준인지 확인한다 */
// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다
@SpringBootTest(properties = {"cors.allowed-origins=https://app.example.com", "cookie.secure=true"})
@AutoConfigureMockMvc
class ProdConfigTests {

  private static final String APP = "https://app.example.com";
  private static final String OTHER = "https://evil.example.com";

  @Autowired MockMvc mvc;

  @Test
  void allowsOnlyAppOriginWithCredentials() throws Exception {
    // 화면 주소에서 오는 쿠키 포함 요청은 허용 (preflight)
    mvc.perform(
            options("/api/auth/me")
                .header(HttpHeaders.ORIGIN, APP)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, APP))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

    // 다른 출처는 preflight와 실제 요청 모두 막는다
    mvc.perform(
            options("/api/auth/me")
                .header(HttpHeaders.ORIGIN, OTHER)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    mvc.perform(get("/api/news/home").header(HttpHeaders.ORIGIN, OTHER))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginCookieIsSecureHttpOnlyLax() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .header(HttpHeaders.ORIGIN, APP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"p-%s@test.com","password":"password1","nickname":"운영"}\
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    allOf(
                        containsString(JwtTokens.COOKIE + "="),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"))));
  }

  @Test
  void rejectsWildcardOrigin() {
    // 쿠키를 함께 보내는 API를 모든 출처에 여는 설정은 시작할 때 막는다
    assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(List.of("*")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> new SecurityConfig().corsConfigurationSource(List.of("https://*.example.com")))
        .isInstanceOf(IllegalStateException.class);
  }
}
