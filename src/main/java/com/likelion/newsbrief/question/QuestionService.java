package com.likelion.newsbrief.question;

import com.likelion.newsbrief.common.ApiException;
import com.likelion.newsbrief.news.Source;
import com.likelion.newsbrief.question.Question.Status;
import com.likelion.newsbrief.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class QuestionService {

  static final int DAILY_LIMIT = 10;

  private final QuestionRepository questions;
  private final UserRepository users;

  QuestionService(QuestionRepository questions, UserRepository users) {
    this.questions = questions;
    this.users = users;
  }

  /** 하루 10회 제한을 확인하고 PENDING으로 기록한다 */
  @Transactional
  Question create(long userId, String query) {
    // 사용자 행을 잠가 같은 사용자의 동시 요청이 제한을 넘지 못하게 한다
    users
        .findForUpdate(userId)
        .orElseThrow(
            () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
    Instant todayKst = LocalDate.now(Source.KST).atStartOfDay(Source.KST).toInstant();
    if (questions.countSince(userId, todayKst, Status.FAILED) >= DAILY_LIMIT) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "RATE_LIMIT_EXCEEDED",
          "오늘 질문 횟수(" + DAILY_LIMIT + "회)를 모두 사용했어요. 내일 다시 질문해 주세요.");
    }
    return questions.save(new Question(userId, query, Instant.now()));
  }
}
