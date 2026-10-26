package com.likelion.newsbrief.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  record ErrorBody(String error, String code) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(new ErrorBody(e.getMessage(), e.code()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("입력값을 확인해 주세요.");
    return ResponseEntity.badRequest().body(new ErrorBody(message, "VALIDATION_ERROR"));
  }

  /** 숫자 자리에 글자가 오는 등 (예: /api/questions/abc) */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorBody> unreadable() {
    return ResponseEntity.badRequest().body(new ErrorBody("요청 형식이 올바르지 않습니다.", "VALIDATION_ERROR"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> other(Exception e) {
    // 없는 주소(404), 허용 안 된 메서드(405) 같은 Spring MVC 표준 오류는 상태 코드를 그대로 쓴다
    if (e instanceof ErrorResponse response) {
      HttpStatusCode status = response.getStatusCode();
      if (status.value() == 400) {
        return ResponseEntity.badRequest().body(new ErrorBody("입력값을 확인해 주세요.", "VALIDATION_ERROR"));
      }
      String code = HttpStatus.valueOf(status.value()).name();
      String message = status.value() == 404 ? "찾을 수 없습니다." : "잘못된 요청입니다.";
      return ResponseEntity.status(status).body(new ErrorBody(message, code));
    }
    log.error("처리하지 못한 오류", e);
    return ResponseEntity.internalServerError()
        .body(new ErrorBody("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "INTERNAL_SERVER_ERROR"));
  }
}
