package com.likelion.newsbrief.article;

import com.likelion.newsbrief.common.ApiException;
import com.likelion.newsbrief.news.Source;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 근거 기사 저장(별)과 마이페이지 저장한 기사 목록. 모두 로그인 필요 (SecurityConfig) */
@RestController
class SavedArticleController {

  private static final int PAGE_SIZE = 20;

  private final SavedArticleRepository saved;

  SavedArticleController(SavedArticleRepository saved) {
    this.saved = saved;
  }

  /** 분야 응답의 sources 항목을 그대로 보낸다 */
  record SaveRequest(
      @NotBlank(message = "기사 제목이 없습니다.") @Size(max = 255, message = "기사 제목이 너무 깁니다.") String title,
      // 화면에서 링크로 그리므로 javascript: 같은 주소는 받지 않는다
      @NotBlank(message = "기사 주소가 없습니다.")
          @Size(max = 512, message = "기사 주소가 너무 깁니다.")
          @Pattern(regexp = "https?://.+", message = "기사 주소가 올바르지 않습니다.")
          String url,
      @Size(max = 100, message = "언론사 이름이 너무 깁니다.") String press,
      OffsetDateTime publishedAt) {}

  record UrlsResponse(List<String> urls) {}

  record Item(
      Long savedArticleId,
      String press,
      String title,
      String url,
      OffsetDateTime publishedAt,
      OffsetDateTime savedAt) {}

  record ListResponse(List<Item> articles, boolean hasNext) {

    static ListResponse from(Slice<SavedArticle> slice) {
      return new ListResponse(
          slice
              .map(
                  a ->
                      new Item(
                          a.getSavedArticleId(),
                          a.getPress(),
                          a.getTitle(),
                          a.getUrl(),
                          kst(a.getPublishedAt()),
                          kst(a.getCreatedAt())))
              .getContent(),
          slice.hasNext());
    }
  }

  /** 여러 번 저장해도 결과가 같다 (이미 저장한 기사면 그대로 성공) */
  @PutMapping("/api/saved-articles")
  ResponseEntity<Void> save(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody SaveRequest request) {
    if (!saved.existsByUserIdAndUrl(userId, request.url())) {
      try {
        saved.saveAndFlush(
            new SavedArticle(
                userId,
                request.title(),
                request.url(),
                request.press(),
                request.publishedAt() == null ? null : request.publishedAt().toInstant()));
      } catch (DataIntegrityViolationException e) {
        // 같은 기사를 동시에 두 번 저장한 경우. UNIQUE(user_id, url)가 하나만 남긴다
      }
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/saved-articles")
  ResponseEntity<Void> unsave(@AuthenticationPrincipal Long userId, @RequestParam String url) {
    if (saved.deleteOwned(userId, url) == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "저장한 기사가 아닙니다.");
    }
    return ResponseEntity.noContent().build();
  }

  // ponytail: 사용자당 저장 수 제한 없음. 수천 건이 되면 분야 화면의 URL만 묻는 방식으로 바꾼다
  @GetMapping("/api/saved-articles/urls")
  UrlsResponse urls(@AuthenticationPrincipal Long userId) {
    return new UrlsResponse(saved.findUrls(userId));
  }

  @GetMapping("/api/mypage/saved-articles")
  ListResponse list(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "0") int page) {
    return ListResponse.from(
        saved.findByUserIdOrderByCreatedAtDescSavedArticleIdDesc(
            userId, PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
  }

  private static OffsetDateTime kst(Instant t) {
    return t == null ? null : t.atOffset(Source.KST);
  }
}
