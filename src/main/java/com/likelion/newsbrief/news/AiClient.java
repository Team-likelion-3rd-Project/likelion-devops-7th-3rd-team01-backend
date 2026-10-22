package com.likelion.newsbrief.news;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** AI_MODE: mock(기본) / bedrock(실제 AI, AWS Bedrock) / fail(장애 시험용, 항상 실패) */
@Component
public class AiClient {

  private static final Set<String> MODES = Set.of("mock", "bedrock", "fail");

  private static final Logger log = LoggerFactory.getLogger(AiClient.class);

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
  private final String modelId;
  private final BedrockRuntimeClient bedrock;

  /**
   * mock이 실제 AI처럼 응답을 늦추는 시간 (AI_MOCK_DELAY, 예: 3s). 부하 테스트에서 실제 AI 응답 시간을 흉내 내고(M4-9 측정값), 타임아웃보다
   * 길게 주면 장애 시나리오 B4를 시험한다. 기본 0
   */
  @Value("${ai.mock-delay:0s}")
  Duration mockDelay = Duration.ZERO;

  @Autowired
  AiClient(
      @Value("${ai.mode:mock}") String mode,
      @Value("${bedrock.model-id:apac.amazon.nova-lite-v1:0}") String modelId,
      ObjectProvider<BedrockRuntimeClient> bedrock) {
    this(mode, modelId, bedrock.getIfAvailable());
  }

  AiClient(String mode, String modelId, BedrockRuntimeClient bedrock) {
    // 오타나 옛 이름(real)으로 켜지면 mock 데이터가 실제처럼 보일 수 있어 시작할 때 막는다
    if (!MODES.contains(mode)) {
      throw new IllegalStateException("AI_MODE는 mock, bedrock, fail 중 하나여야 합니다: " + mode);
    }
    if ("bedrock".equals(mode) && bedrock == null) {
      throw new IllegalStateException("AI_MODE=bedrock인데 Bedrock 클라이언트가 없습니다 (BedrockConfig)");
    }
    this.mode = mode;
    this.modelId = modelId;
    this.bedrock = bedrock;
  }

  public boolean isMock() {
    return !"bedrock".equals(mode);
  }

  /** 분야 기사들을 이슈 최대 {@code max}개로 묶어 요약한다 */
  public List<IssueDraft> summarize(Category category, List<NewsArticle> articles, int max) {
    return switch (mode) {
      // 최신 기사 하나를 이슈 하나로 쓴다. 화면에는 "샘플 데이터" 띠가 뜬다
      case "mock" -> {
        mockWait();
        yield articles.stream().limit(max).map(AiClient::mockDraft).toList();
      }
      case "fail" -> throw new IllegalStateException("AI_MODE=fail: 장애 시험용 실패");
      default -> bedrockSummarize(category, articles, max);
    };
  }

  /** 질문 답변 결과. rejected면 뉴스와 무관한 질문. sources는 답에 실제로 쓴 기사 */
  public record Answer(boolean rejected, String text, List<NewsArticle> sources) {}

  /** 주어진 기사만 근거로 답한다. 관련 기사가 없으면 AI를 부르지 않고 "찾지 못했다"고 답한다 */
  public Answer answer(String query, List<NewsArticle> articles) {
    if ("mock".equals(mode) && MOCK_REJECT.matcher(query).find()) {
      mockWait();
      return new Answer(true, "뉴스와 관련된 질문에만 답할 수 있어요.", List.of());
    }
    if (articles.isEmpty()) {
      return new Answer(false, "관련 기사를 찾지 못했어요. 다른 표현으로 다시 질문해 주세요.", List.of());
    }
    return switch (mode) {
      case "mock" -> {
        mockWait();
        yield new Answer(
            false,
            "[샘플 답변] '%s'와 관련된 기사 %d건을 찾았어요. 가장 관련 있는 기사는 「%s」예요."
                .formatted(query, articles.size(), articles.get(0).getTitle()),
            // 실제 AI처럼 후보 중 일부만 근거로 쓴다 (관련도 높은 순 상위 3개)
            articles.subList(0, Math.min(MOCK_SOURCES, articles.size())));
      }
      case "fail" -> throw new IllegalStateException("AI_MODE=fail: 장애 시험용 실패");
      default -> bedrockAnswer(query, articles);
    };
  }

  // ===== bedrock =====

  private static final String SUMMARY_PROMPT = prompt("prompts/summary.txt");
  private static final String ANSWER_PROMPT = prompt("prompts/answer.txt");

  // 모델이 모르는 필드를 덧붙여도 실패하지 않게 한다
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  record SummaryOut(List<IssueOut> issues) {}

  record IssueOut(
      String title,
      String summary,
      List<String> keyPoints,
      List<NewsIssue.Term> glossary,
      List<Integer> sources) {}

  record AnswerOut(boolean rejected, String answer, List<Integer> sources) {}

  private List<IssueDraft> bedrockSummarize(
      Category category, List<NewsArticle> articles, int max) {
    String user =
        "분야: %s\n이슈 최대 개수: %d\n\n기사 목록\n%s"
            .formatted(category.displayName(), max, numbered(articles));
    SummaryOut out = parse(converse(SUMMARY_PROMPT, user, 2000), SummaryOut.class);
    if (out.issues() == null) {
      return List.of();
    }
    return out.issues().stream()
        .map(
            i ->
                new IssueDraft(
                    i.title(),
                    i.summary(),
                    orEmpty(i.keyPoints()),
                    orEmpty(i.glossary()),
                    pick(articles, i.sources())))
        // 근거 기사가 없는 이슈는 버린다 (기사에 없는 내용일 수 있다)
        .filter(d -> d.title() != null && d.summary() != null && !d.sources().isEmpty())
        .limit(max)
        .toList();
  }

  private Answer bedrockAnswer(String query, List<NewsArticle> articles) {
    String user = "질문: %s\n\n기사 목록\n%s".formatted(query, numbered(articles));
    AnswerOut out = parse(converse(ANSWER_PROMPT, user, 800), AnswerOut.class);
    if (out.answer() == null || out.answer().isBlank()) {
      throw new IllegalStateException("AI 답변이 비어 있습니다");
    }
    return new Answer(
        out.rejected(), out.answer(), out.rejected() ? List.of() : pick(articles, out.sources()));
  }

  /** 기사에 [1]부터 번호를 붙인다. AI는 이 번호로 근거를 가리킨다 */
  static String numbered(List<NewsArticle> articles) {
    return IntStream.range(0, articles.size())
        .mapToObj(
            i -> {
              NewsArticle a = articles.get(i);
              String date =
                  a.getPublishedAt() == null
                      ? "날짜 없음"
                      : a.getPublishedAt().atOffset(Source.KST).format(DATE);
              String description = a.getDescription() == null ? "" : a.getDescription();
              return "[%d] (%s, %s) %s\n%s"
                  .formatted(i + 1, date, a.getPress(), a.getTitle(), description);
            })
        .reduce((x, y) -> x + "\n\n" + y)
        .orElse("");
  }

  /** AI가 준 번호로 기사를 고른다. 목록에 없는 번호와 중복은 버린다 */
  private static List<NewsArticle> pick(List<NewsArticle> articles, List<Integer> numbers) {
    return orEmpty(numbers).stream()
        .filter(n -> n != null && n >= 1 && n <= articles.size())
        .distinct()
        .map(n -> articles.get(n - 1))
        .toList();
  }

  private String converse(String system, String user, int maxTokens) {
    ConverseResponse response =
        bedrock.converse(
            r ->
                r.modelId(modelId)
                    .system(SystemContentBlock.fromText(system))
                    .messages(
                        Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(user))
                            .build())
                    .inferenceConfig(
                        InferenceConfiguration.builder()
                            .maxTokens(maxTokens)
                            .temperature(0.2f)
                            .build()));
    // 비용 추적용 (CL 토큰 추정과 비교)
    if (response.usage() != null) {
      log.info(
          "Bedrock 호출: 입력 {} 토큰, 출력 {} 토큰",
          response.usage().inputTokens(),
          response.usage().outputTokens());
    }
    return response.output().message().content().stream()
        .map(ContentBlock::text)
        .filter(t -> t != null)
        .reduce("", String::concat);
  }

  /** 모델이 JSON 앞뒤에 설명이나 ```json 표시를 붙여도 가운데 JSON만 읽는다. 읽을 수 없으면 예외 (요약은 직전 것 유지, 질문은 FAILED) */
  static <T> T parse(String text, Class<T> type) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new IllegalStateException("AI 응답에 JSON이 없습니다: " + truncate(text, 200));
    }
    return JSON.readValue(text.substring(start, end + 1), type);
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static String prompt(String path) {
    try (var in = new ClassPathResource(path).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ===== mock =====

  private void mockWait() {
    if (mockDelay.isZero()) {
      return;
    }
    try {
      Thread.sleep(mockDelay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** mock에서 거절 화면을 확인할 수 있게, 계획서의 거절 예시(코드 작성·번역·잡담)를 단어로 흉내 낸다 */
  private static final Pattern MOCK_REJECT = Pattern.compile("코드|번역|잡담");

  private static final int MOCK_SOURCES = 3;

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
