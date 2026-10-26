package com.likelion.newsbrief.news;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// ponytail: 로컬 compose(MySQL·Redis)가 떠 있어야 통과한다. M3에서 Testcontainers로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 테스트가 넣은 요약은 끝나면 되돌린다
class NewsApiTests {

  @Autowired MockMvc mvc;

  @Autowired NewsIssueRepository issues;

  @Autowired NewsCache cache;

  // 캐시에 남은 응답 때문에 테스트 데이터가 안 보이거나, 되돌린 테스트 데이터가 캐시에 남지 않게 한다
  @BeforeEach
  void clearCacheBefore() {
    cache.evictAll();
  }

  @AfterEach
  void clearCacheAfter() {
    cache.evictAll();
  }

  @Test
  void latestBatchPerCategory() throws Exception {
    Instant old = future();
    Instant latest = old.plus(30, ChronoUnit.MINUTES);
    save(old, 1, "지난 이슈");
    for (int rank = 1; rank <= 5; rank++) {
      save(latest, rank, "이슈 " + rank);
    }
    issues.flush();

    mvc.perform(get("/api/news/categories/AI"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categoryName").value("AI"))
        .andExpect(jsonPath("$.mock").value(true))
        .andExpect(jsonPath("$.issues.length()").value(5))
        .andExpect(jsonPath("$.issues[0].title").value("이슈 1"))
        .andExpect(jsonPath("$.issues[0].keyPoints[0]").value("핵심"))
        // 전개 흐름은 근거 기사 발행일(KST 날짜)과 제목으로 만든다. 16:00Z는 KST로 다음 날
        .andExpect(jsonPath("$.issues[0].timeline[0].date").value("2026-10-15"))
        .andExpect(jsonPath("$.issues[0].timeline[0].text").value("원문"))
        .andExpect(jsonPath("$.issues[0].glossary[0].term").value("용어"))
        .andExpect(jsonPath("$.issues[0].sources[0].press").value("전자신문"))
        .andExpect(
            jsonPath("$.issues[0].sources[0].publishedAt")
                .value(org.hamcrest.Matchers.endsWith("+09:00")))
        .andExpect(jsonPath("$.generatedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));

    mvc.perform(get("/api/news/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(6))
        .andExpect(jsonPath("$.categories[0].category").value("AI"))
        .andExpect(jsonPath("$.categories[4].category").value("SECURITY"))
        .andExpect(jsonPath("$.categories[4].categoryName").value("보안"))
        .andExpect(jsonPath("$.categories[?(@.category=='AI')].issues.length()").value(3))
        .andExpect(jsonPath("$.categories[?(@.category=='AI')].issues[0].title").value("이슈 1"))
        .andExpect(jsonPath("$.categories[?(@.category=='AI')].issues[0].summary").value("요약"));
  }

  @Test
  void servesCacheUntilBatchEvicts() throws Exception {
    Instant first = future();
    save(first, 1, "첫 요약");
    issues.flush();
    mvc.perform(get("/api/news/categories/AI"))
        .andExpect(jsonPath("$.issues[0].title").value("첫 요약"));

    // 새 요약이 DB에 들어와도 캐시가 남아 있으면 이전 응답 (DB를 다시 읽지 않는다)
    save(first.plus(30, ChronoUnit.MINUTES), 1, "새 요약");
    issues.flush();
    mvc.perform(get("/api/news/categories/AI"))
        .andExpect(jsonPath("$.issues[0].title").value("첫 요약"));

    // 배치가 끝나며 캐시를 비우면 새 요약
    cache.evictAll();
    mvc.perform(get("/api/news/categories/AI"))
        .andExpect(jsonPath("$.issues[0].title").value("새 요약"));
  }

  @Test
  void unknownCategoryIs404() throws Exception {
    // 이제 다루지 않는 옛 분야 코드도 404
    mvc.perform(get("/api/news/categories/POLITICS"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    // 분야 개편으로 뺀 게임·인터넷도 404
    mvc.perform(get("/api/news/categories/GAME")).andExpect(status().isNotFound());
    mvc.perform(get("/api/news/categories/INTERNET")).andExpect(status().isNotFound());
  }

  /** 미래 시각으로 넣어 로컬 DB에 이미 있는 요약보다 최신이 되게 한다 */
  private static Instant future() {
    return Instant.now().plus(1, ChronoUnit.DAYS);
  }

  private void save(Instant generatedAt, int rank, String title) {
    List<Source> sources =
        List.of(
            new Source(
                "전자신문",
                "원문",
                "https://x.com/1",
                OffsetDateTime.of(2026, 10, 14, 16, 0, 0, 0, ZoneOffset.UTC)));
    issues.save(
        new NewsIssue(
            Category.AI,
            generatedAt,
            rank,
            title,
            "요약",
            List.of("핵심"),
            List.of(new NewsIssue.Term("용어", "풀이")),
            sources,
            true));
  }
}
