package com.likelion.newsbrief.question;

import com.likelion.newsbrief.common.ApiException;
import com.likelion.newsbrief.news.Source;
import com.likelion.newsbrief.question.Question.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 질문과 마이페이지 질문 목록. 모두 로그인 필요 (SecurityConfig) */
@RestController
class QuestionController {

  private static final int PAGE_SIZE = 20;

  private final QuestionService service;
  private final QuestionRepository questions;
  private final QuestionQueue queue;

  QuestionController(QuestionService service, QuestionRepository questions, QuestionQueue queue) {
    this.service = service;
    this.questions = questions;
    this.queue = queue;
  }

  record CreateRequest(
      @NotBlank(message = "질문을 입력해 주세요.") @Size(max = 500, message = "질문은 500자 이하로 입력해 주세요.")
          String query) {}

  record CreateResponse(Long questionId, Status status) {}

  /** PENDING·PROCESSING이면 answer, sources, completedAt은 null */
  record QuestionResponse(
      Long questionId,
      Status status,
      String query,
      String answer,
      List<Source> sources,
      boolean saved,
      OffsetDateTime createdAt,
      OffsetDateTime completedAt) {

    static QuestionResponse from(Question q) {
      List<Source> sources =
          q.getSources() == null ? null : q.getSources().stream().map(Source::inKst).toList();
      return new QuestionResponse(
          q.getQuestionId(),
          q.getStatus(),
          q.getQueryText(),
          q.getAnswer(),
          sources,
          q.getSavedAt() != null,
          kst(q.getCreatedAt()),
          kst(q.getCompletedAt()));
    }
  }

  record ListItem(
      Long questionId, String query, Status status, boolean saved, OffsetDateTime createdAt) {}

  record ListResponse(List<ListItem> questions, boolean hasNext) {

    static ListResponse from(Slice<Question> slice) {
      return new ListResponse(
          slice
              .map(
                  q ->
                      new ListItem(
                          q.getQuestionId(),
                          q.getQueryText(),
                          q.getStatus(),
                          q.getSavedAt() != null,
                          kst(q.getCreatedAt())))
              .getContent(),
          slice.hasNext());
    }
  }

  @PostMapping("/api/questions")
  ResponseEntity<CreateResponse> create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateRequest request) {
    Question question = service.create(userId, request.query());
    // DB 커밋 뒤에 대기열에 넣는다 (워커가 아직 커밋 안 된 질문을 찾지 못하는 일을 막는다)
    queue.push(question.getQuestionId());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new CreateResponse(question.getQuestionId(), question.getStatus()));
  }

  @GetMapping("/api/questions/{id}")
  QuestionResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return questions
        .findByQuestionIdAndUserId(id, userId)
        .map(QuestionResponse::from)
        .orElseThrow(this::notFound);
  }

  @DeleteMapping("/api/questions/{id}")
  ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return noContentOr404(questions.deleteOwned(id, userId));
  }

  @PutMapping("/api/questions/{id}/saved")
  ResponseEntity<Void> save(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return noContentOr404(questions.markSaved(id, userId, Instant.now()));
  }

  @DeleteMapping("/api/questions/{id}/saved")
  ResponseEntity<Void> unsave(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return noContentOr404(questions.unmarkSaved(id, userId));
  }

  @GetMapping("/api/mypage/questions")
  ListResponse history(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "0") int page) {
    return ListResponse.from(questions.findByUserIdOrderByCreatedAtDesc(userId, page(page)));
  }

  @GetMapping("/api/mypage/saved-questions")
  ListResponse saved(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "0") int page) {
    return ListResponse.from(
        questions.findByUserIdAndSavedAtNotNullOrderBySavedAtDesc(userId, page(page)));
  }

  private static Pageable page(int page) {
    return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
  }

  private ResponseEntity<Void> noContentOr404(int updated) {
    if (updated == 0) {
      throw notFound();
    }
    return ResponseEntity.noContent().build();
  }

  /** 없는 질문과 남의 질문을 구분하지 않는다 (존재 여부를 숨긴다) */
  private ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "질문을 찾을 수 없습니다.");
  }

  private static OffsetDateTime kst(Instant t) {
    return t == null ? null : t.atOffset(Source.KST);
  }
}
