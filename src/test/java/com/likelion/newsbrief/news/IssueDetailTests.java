package com.likelion.newsbrief.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class IssueDetailTests {

  @Test
  void mockSplitsDescriptionIntoSummaryAndKeyPoints() {
    NewsArticle article =
        new NewsArticle(
            Category.AI, "제목", "첫 문장이다. 둘째 문장이다. 셋째! 넷째? 다섯째.", "전자신문", "https://x.com/1", null);

    AiClient.IssueDraft draft = AiClient.mockDraft(article);

    assertThat(draft.summary()).isEqualTo("[샘플] 첫 문장이다.");
    assertThat(draft.keyPoints()).containsExactly("둘째 문장이다.", "셋째!", "넷째?");
    assertThat(draft.glossary()).hasSize(1);
  }

  @Test
  void mockSkipsCutOffSentence() {
    NewsArticle article =
        new NewsArticle(
            Category.AI, "제목", "첫 문장이다. 둘째 문장이다. 중간에서 잘린 문", "전자신문", "https://x.com/1", null);

    assertThat(AiClient.mockDraft(article).keyPoints()).containsExactly("둘째 문장이다.");
  }

  @Test
  void mockWithoutDescriptionStillHasSummaryAndKeyPoint() {
    NewsArticle article =
        new NewsArticle(Category.AI, "제목만 있는 기사", null, "전자신문", "https://x.com/1", null);

    AiClient.IssueDraft draft = AiClient.mockDraft(article);

    assertThat(draft.summary()).isEqualTo("[샘플] 제목만 있는 기사");
    assertThat(draft.keyPoints()).containsExactly("제목만 있는 기사");
  }

  @Test
  void timelineComesFromSourcesOldestFirstInKst() {
    List<Source> sources =
        List.of(
            source("나중 기사", OffsetDateTime.of(2026, 10, 14, 16, 0, 0, 0, ZoneOffset.UTC)),
            source("날짜 없는 기사", null),
            source("먼저 기사", OffsetDateTime.of(2026, 10, 13, 1, 0, 0, 0, ZoneOffset.UTC)));

    assertThat(NewsIssue.TimelineEntry.from(sources))
        .containsExactly(
            new NewsIssue.TimelineEntry("2026-10-13", "먼저 기사"),
            // 16:00Z는 KST로 다음 날
            new NewsIssue.TimelineEntry("2026-10-15", "나중 기사"));
  }

  @Test
  void mockDelayOnlyWhenAiWouldBeCalled() {
    AiClient ai = new AiClient("mock", "model", (BedrockRuntimeClient) null);
    ai.mockDelay = Duration.ofMillis(300);
    NewsArticle article =
        new NewsArticle(Category.AI, "제목", "첫 문장이다.", "전자신문", "https://x.com/1", null);

    long start = System.nanoTime();
    ai.answer("AI 소식 알려줘", List.of(article));
    assertThat(Duration.ofNanos(System.nanoTime() - start))
        .isGreaterThanOrEqualTo(Duration.ofMillis(300));

    // 관련 기사가 없으면 AI를 부르지 않으므로 지연도 없다
    start = System.nanoTime();
    ai.answer("AI 소식 알려줘", List.of());
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(300));
  }

  @Test
  void mockAnswerUsesTopThreeSources() {
    AiClient ai = new AiClient("mock", "model", (BedrockRuntimeClient) null);
    List<NewsArticle> found =
        java.util.stream.IntStream.rangeClosed(1, 15)
            .mapToObj(
                i ->
                    new NewsArticle(
                        Category.AI, "기사 " + i, "소개.", "전자신문", "https://x.com/" + i, null))
            .toList();

    assertThat(ai.answer("AI 소식", found).sources()).containsExactlyElementsOf(found.subList(0, 3));
  }

  private static Source source(String title, OffsetDateTime publishedAt) {
    return new Source("전자신문", title, "https://x.com/" + title.hashCode(), publishedAt);
  }
}
