package com.likelion.newsbrief.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** 로그인 쿠키(ACCESS_TOKEN)에 담는 JWT 발급·검증 */
@Component
public class JwtTokens {

  public static final String COOKIE = "ACCESS_TOKEN";

  private static final Duration TTL = Duration.ofHours(24);

  private static final Logger log = LoggerFactory.getLogger(JwtTokens.class);

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final boolean secureCookie;

  JwtTokens(
      @Value("${jwt.secret:}") String secret,
      @Value("${cookie.secure:true}") boolean secureCookie) {
    if (secret.isBlank()) {
      // 로컬 실행 편의용. 재시작하면 로그인이 풀리고, 레플리카끼리 토큰이 안 맞는다
      byte[] random = new byte[32];
      new SecureRandom().nextBytes(random);
      secret = Base64.getEncoder().encodeToString(random);
      log.warn("JWT_SECRET이 없어 임시 키를 만들었습니다. 서버 환경에서는 반드시 JWT_SECRET을 넣으세요.");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET은 32자 이상이어야 합니다.");
    }
    SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.decoder = NimbusJwtDecoder.withSecretKey(key).build();
    this.secureCookie = secureCookie;
  }

  /** 로그인 쿠키 발급 */
  public ResponseCookie issue(long userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiresAt(now.plus(TTL))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return cookie(token, TTL);
  }

  /** 로그아웃: 쿠키 삭제 */
  public ResponseCookie expire() {
    return cookie("", Duration.ZERO);
  }

  /** 토큰이 유효하면 사용자 ID, 잘못됐거나 만료됐으면 null */
  public Long verify(String token) {
    try {
      return Long.valueOf(decoder.decode(token).getSubject());
    } catch (JwtException | NumberFormatException e) {
      return null;
    }
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(COOKIE, value)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
  }
}
