package com.likelion.newsbrief.news;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 저장된 요약만 돌려준다. AI를 호출하지 않는다. Redis 캐시를 먼저 보고, 없으면 DB에서 읽는다 */
@RestController
@RequestMapping("/api/news")
class NewsController {

  private static final int HOME_ISSUES = 3;

  private final NewsIssueRepository issues;
  private final NewsCache cache;

  NewsController(NewsIssueRepository issues, NewsCache cache) {
    this.issues = issues;
    this.cache = cache;
  }

  // 응답은 Redis에 통째로 캐시하므로 Serializable
  record HomeIssue(Long issueId, String title, String summary) implements Serializable {}

  record HomeCategory(Category category, String categoryName, List<HomeIssue> issues)
      implements Serializable {}

  record HomeResponse(OffsetDateTime generatedAt, boolean mock, List<HomeCategory> categories)
      implements Serializable {}

  record Issue(
      Long issueId,
      String title,
      String summary,
      List<String> keyPoints,
      List<NewsIssue.TimelineEntry> timeline,
      List<NewsIssue.Term> glossary,
      List<Source> sources)
      implements Serializable {}

  record CategoryResponse(
      OffsetDateTime generatedAt,
      boolean mock,
      Category category,
      String categoryName,
      List<Issue> issues)
      implements Serializable {}

  @GetMapping("/home")
  HomeResponse home() {
    return cache.get(NewsCache.homeKey(), this::loadHome);
  }

  @GetMapping("/categories/{category}")
  CategoryResponse category(@PathVariable String category) {
    // 없는 분야는 캐시에 닿기 전에 404
    Category c = Category.parse(category);
    return cache.get(NewsCache.categoryKey(c), () -> loadCategory(c));
  }

  /**
   * 요약 배치가 끝나면 부른다. 캐시를 지우지 않고 새 응답으로 덮어쓴다. 지우면 그 순간 모든 조회가 DB로 몰려 연결 풀이 차고 readiness가 실패한다
   * (HPA 스파이크 시험 10/29: api 4개가 동시에 빠져 503 3.3%)
   */
  void refreshCache() {
    cache.put(NewsCache.homeKey(), loadHome());
    for (Category c : Category.values()) {
      cache.put(NewsCache.categoryKey(c), loadCategory(c));
    }
  }

  private HomeResponse loadHome() {
    List<NewsIssue> latest = issues.findLatest();
    Map<Category, List<NewsIssue>> byCategory =
        latest.stream().collect(Collectors.groupingBy(NewsIssue::getCategory));
    List<HomeCategory> categories =
        Arrays.stream(Category.values())
            .map(
                c ->
                    new HomeCategory(
                        c,
                        c.displayName(),
                        byCategory.getOrDefault(c, List.of()).stream()
                            .limit(HOME_ISSUES)
                            .map(i -> new HomeIssue(i.getIssueId(), i.getTitle(), i.getSummary()))
                            .toList()))
            .toList();
    return new HomeResponse(oldestGeneratedAt(latest), anyMock(latest), categories);
  }

  private CategoryResponse loadCategory(Category c) {
    List<NewsIssue> latest = issues.findLatest(c);
    List<Issue> body =
        latest.stream()
            .map(
                i ->
                    new Issue(
                        i.getIssueId(),
                        i.getTitle(),
                        i.getSummary(),
                        i.getKeyPoints(),
                        i.getTimeline(),
                        i.getGlossary(),
                        i.getSources().stream().map(Source::inKst).toList()))
            .toList();
    return new CategoryResponse(
        oldestGeneratedAt(latest), anyMock(latest), c, c.displayName(), body);
  }

  /** 한 분야라도 갱신이 밀렸으면 그 시각을 준다. FE는 이 값이 1시간 넘게 지났으면 갱신 지연 안내를 띄운다 */
  private static OffsetDateTime oldestGeneratedAt(List<NewsIssue> latest) {
    return latest.stream()
        .map(NewsIssue::getGeneratedAt)
        .min(Comparator.naturalOrder())
        .map((Instant t) -> t.atOffset(Source.KST))
        .orElse(null);
  }

  private static boolean anyMock(List<NewsIssue> latest) {
    return latest.stream().anyMatch(NewsIssue::isMock);
  }
}
