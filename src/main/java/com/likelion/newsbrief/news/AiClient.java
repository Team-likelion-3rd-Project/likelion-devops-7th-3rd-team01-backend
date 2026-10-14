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

  /** AI가 만든 이슈 하나 */
  public record IssueDraft(String title, String summary, List<NewsArticle> sources) {}

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
      case "mock" ->
          articles.stream()
              .limit(max)
              .map(a -> new IssueDraft(a.getTitle(), mockSummary(a), List.of(a)))
              .toList();
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

  private static String mockSummary(NewsArticle article) {
    String description = article.getDescription() == null ? "" : article.getDescription();
    return "[샘플 요약] "
        + (description.length() > 200 ? description.substring(0, 200) + "…" : description);
  }
}
