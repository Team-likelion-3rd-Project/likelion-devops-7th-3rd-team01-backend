package com.likelion.newsbrief.auth;

import com.likelion.newsbrief.common.ApiException;
import com.likelion.newsbrief.user.User;
import com.likelion.newsbrief.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokens tokens;

  AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtTokens tokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
  }

  record SignupRequest(
      @NotBlank(message = "이메일을 입력해 주세요.")
          @Email(message = "이메일 형식이 올바르지 않습니다.")
          @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
          String email,
      @NotNull(message = "비밀번호를 입력해 주세요.")
          @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
          String password,
      @NotBlank(message = "닉네임을 입력해 주세요.") @Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
          String nickname) {}

  record LoginRequest(
      @NotBlank(message = "이메일을 입력해 주세요.") String email,
      @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {}

  record UserResponse(Long userId, String email, String nickname) {

    static UserResponse from(User user) {
      return new UserResponse(user.getUserId(), user.getEmail(), user.getNickname());
    }
  }

  /** 가입 즉시 로그인 상태가 된다 */
  @PostMapping("/signup")
  ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    User user;
    try {
      // 이메일 UNIQUE 제약으로 중복을 막는다 (동시에 가입해도 하나만 성공)
      user =
          users.saveAndFlush(
              new User(
                  request.email(), passwordEncoder.encode(request.password()), request.nickname()));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다.");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, tokens.issue(user.getUserId()).toString())
        .body(UserResponse.from(user));
  }

  @PostMapping("/login")
  ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
    User user =
        users
            .findByEmail(request.email())
            .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, tokens.issue(user.getUserId()).toString())
        .body(UserResponse.from(user));
  }

  /** 로그인 상태가 아니어도 항상 성공한다 */
  @PostMapping("/logout")
  ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, tokens.expire().toString())
        .build();
  }

  @GetMapping("/me")
  UserResponse me(@AuthenticationPrincipal Long userId) {
    // 토큰은 유효하지만 사용자가 없는 경우 (DB 초기화 등)
    return users
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(
            () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
  }
}
