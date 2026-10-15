package com.likelion.newsbrief.news;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AI_MODE: mock(기본) / bedrock(실제 AI, AWS Bedrock) / fail(장애 시험용, 항상 실패) */
@Component
public class AiClient {

  private static final Set<String> MODES = Set.of("mock", "bedrock", "fail");

  /**
   * AI가 만든 이슈 하나. summary는 한 줄 요약, keyPoints는 3개 정도. 전개 흐름(timeline)은 AI가 아니라 근거 기사로 만든다
   * (NewsIssue.TimelineEntry)
   */
  public record IssueDraft(
      String title,
      String summary,
      List<String> keyPoints,
      List<NewsIssue.Term> glossary,
      List<NewsArticle> sources) {}

  private final String mode;

  AiClient(@Value("${ai.mode:mock}") String mode) {
    // 오타나 옛 이름(real)으로 켜지면 mock 데이터가 실제처럼 보일 수 있어 시작할 때 막는다
    if (!MODES.contains(mode)) {
      throw new IllegalStateException("AI_MODE는 mock, bedrock, fail 중 하나여야 합니다: " + mode);
    }
    this.mode = mode;
  }

  public boolean isMock() {
    return !"bedrock".equals(mode);
  }

  /** 분야 기사들을 이슈 최대 {@code max}개로 묶어 요약한다 */
  public List<IssueDraft> summarize(Category category, List<NewsArticle> articles, int max) {
    return switch (mode) {
      // 최신 기사 하나를 이슈 하나로 쓴다. 화면에는 "샘플 데이터" 띠가 뜬다
      case "mock" -> articles.stream().limit(max).map(AiClient::mockDraft).toList();
      case "fail" -> throw new IllegalStateException("AI_MODE=fail: 장애 시험용 실패");
      // ponytail: Bedrock 호출은 팀 AWS 계정의 모델 사용 가능 여부 확인 후 붙인다
      default -> throw new UnsupportedOperationException("아직 연결하지 않은 AI_MODE: " + mode);
    };
  }

  /** 질문 답변 결과. rejected면 뉴스와 무관한 질문 */
  public record Answer(boolean rejected, String text) {}

  /** 주어진 기사만 근거로 답한다. 관련 기사가 없으면 AI를 부르지 않고 "찾지 못했다"고 답한다 */
  public Answer answer(String query, List<NewsArticle> articles) {
    if ("mock".equals(mode) && MOCK_REJECT.matcher(query).find()) {
      return new Answer(true, "뉴스와 관련된 질문에만 답할 수 있어요.");
    }
    if (articles.isEmpty()) {
      return new Answer(false, "관련 기사를 찾지 못했어요. 다른 표현으로 다시 질문해 주세요.");
    }
    return switch (mode) {
      case "mock" ->
          new Answer(
              false,
              "[샘플 답변] '%s'와 관련된 기사 %d건을 찾았어요. 가장 관련 있는 기사는 「%s」예요."
                  .formatted(query, articles.size(), articles.get(0).getTitle()));
      case "fail" -> throw new IllegalStateException("AI_MODE=fail: 장애 시험용 실패");
      default -> throw new UnsupportedOperationException("아직 연결하지 않은 AI_MODE: " + mode);
    };
  }

  /** mock에서 거절 화면을 확인할 수 있게, 계획서의 거절 예시(코드 작성·번역·잡담)를 단어로 흉내 낸다 */
  private static final Pattern MOCK_REJECT = Pattern.compile("코드|번역|잡담");

  private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

  /** 소개글 첫 문장을 한 줄 요약으로, 이어지는 문장(최대 3개)을 핵심 포인트로 쓴다. 용어 풀이는 화면 확인용 고정 예시 */
  static IssueDraft mockDraft(NewsArticle article) {
    String description = article.getDescription() == null ? "" : article.getDescription();
    List<String> sentences =
        SENTENCE_END
            .splitAsStream(description)
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .toList();
    String first = sentences.isEmpty() ? article.getTitle() : sentences.get(0);
    // RSS 소개글은 중간에서 잘려 있어 마지막 문장이 끊긴 경우가 많다. 문장 부호로 끝나는 문장만 쓴다
    List<String> rest =
        sentences.stream().skip(1).filter(s -> s.matches(".*[.!?]$")).limit(3).toList();
    return new IssueDraft(
        article.getTitle(),
        "[샘플] " + truncate(first, 100),
        (rest.isEmpty() ? List.of(first) : rest).stream().map(s -> truncate(s, 100)).toList(),
        List.of(new NewsIssue.Term("[샘플] 용어", "실제 AI를 연결하면 기사에 나온 어려운 용어를 풀어 줍니다.")),
        List.of(article));
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }
}
