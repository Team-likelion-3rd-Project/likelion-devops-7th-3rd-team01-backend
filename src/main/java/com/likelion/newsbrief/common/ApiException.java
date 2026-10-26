package com.likelion.newsbrief.common;

import org.springframework.http.HttpStatus;

/** 명세의 오류 응답 {@code {"error": message, "code": code}}로 바뀌는 예외 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
