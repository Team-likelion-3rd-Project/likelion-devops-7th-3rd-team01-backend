package com.likelion.newsbrief.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.WebUtils;

@Configuration
class SecurityConfig {

  private static final String UNAUTHORIZED_BODY =
      "{\"error\":\"로그인이 필요합니다.\",\"code\":\"UNAUTHORIZED\"}";

  @Bean
  @ConditionalOnWebApplication // APP_ROLE=batch는 웹 서버가 없다
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokens tokens) throws Exception {
    return http
        // CSRF는 SameSite=Lax 쿠키 + JSON 요청만 받는 것으로 막는다 (docs/api-spec.md)
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/me", "/api/questions/**", "/api/mypage/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, ex) -> {
                      response.setStatus(401);
                      response.setContentType("application/json;charset=UTF-8");
                      response.getWriter().write(UNAUTHORIZED_BODY);
                    }))
        .addFilterBefore(jwtCookieFilter(tokens), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /** 쿠키의 JWT가 유효하면 로그인 상태로 만든다. 잘못되거나 만료된 쿠키는 무시해서 공개 API는 그대로 통과한다 */
  private Filter jwtCookieFilter(JwtTokens tokens) {
    return (request, response, chain) -> {
      Cookie cookie = WebUtils.getCookie((HttpServletRequest) request, JwtTokens.COOKIE);
      Long userId = cookie == null ? null : tokens.verify(cookie.getValue());
      if (userId != null) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
      }
      chain.doFilter(request, response);
    };
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** 화면이 다른 출처일 때(AWS app.{도메인}, 로컬 FE dev 서버)만 쓴다. 온프렘은 같은 출처라 비워 둔다 */
  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${cors.allowed-origins:}") List<String> origins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Content-Type"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
