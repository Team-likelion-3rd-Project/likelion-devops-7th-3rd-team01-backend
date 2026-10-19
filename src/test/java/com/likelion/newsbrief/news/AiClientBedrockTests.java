package com.likelion.newsbrief.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

/** 실제 AWS 대신 정해 둔 응답을 돌려주는 가짜(stub) Bedrock으로 bedrock 모드를 검증한다 */
class AiClientBedrockTests {

  private static final String MODEL = "apac.amazon.nova-lite-v1:0";

  private final List<NewsArticle> articles =
      List.of(
          article("AI 기본법 시행령 국무회의 의결", "정부가 13일 국무회의에서 시행령을 의결했다."),
          article("고영향 AI 기준 나왔다", "채용·의료·금융 AI가 고영향으로 분류된다."),
          article("국산 LLM 오픈소스 공개", "국내 기업이 700억 파라미터 모델을 공개했다."));

  @Test
  void summarizeGroupsArticlesByNumber() {
    // 모델이 ```json 표시를 붙이고, 없는 번호(99)·중복 번호를 섞고, 근거 없는 이슈를 만든 경우
    Stub stub =
        new Stub(
            """
            ```json
            {"issues":[
              {"title":"AI 기본법 시행령 확정","summary":"고영향 AI 사업자 의무가 구체화됐어요.",
               "keyPoints":["고영향 AI 범위 명시","계도 기간 1년"],
               "glossary":[{"term":"고영향 AI","description":"생명·안전에 영향을 주는 AI예요."}],
               "sources":[1,2,2,99]},
              {"title":"근거 없는 이슈","summary":"지어낸 내용이에요.","keyPoints":[],"glossary":[],"sources":[]},
              {"title":"국산 LLM 공개","summary":"한국어 모델이 공개됐어요.","sources":[3]}
            ]}
            ```\
            """);
    AiClient ai = new AiClient("bedrock", MODEL, stub);

    List<AiClient.IssueDraft> drafts = ai.summarize(Category.AI, articles, 5);

    assertThat(drafts).hasSize(2);
    AiClient.IssueDraft first = drafts.get(0);
    assertThat(first.title()).isEqualTo("AI 기본법 시행령 확정");
    assertThat(first.sources()).containsExactly(articles.get(0), articles.get(1));
    assertThat(first.glossary())
        .containsExactly(new NewsIssue.Term("고영향 AI", "생명·안전에 영향을 주는 AI예요."));
    // 모델이 빠뜨린 필드는 빈 목록
    assertThat(drafts.get(1).keyPoints()).isEmpty();
    assertThat(drafts.get(1).glossary()).isEmpty();
    assertThat(ai.isMock()).isFalse();

    ConverseRequest request = stub.requests.get(0);
    assertThat(request.modelId()).isEqualTo(MODEL);
    assertThat(request.system().get(0).text()).contains("기사 목록만 근거로");
    String user = request.messages().get(0).content().get(0).text();
    assertThat(user).contains("분야: AI", "이슈 최대 개수: 5", "[1] (", "[3] (");
  }

  @Test
  void summarizeKeepsAtMostMaxIssues() {
    Stub stub =
        new Stub(
            """
            {"issues":[{"title":"a","summary":"a","sources":[1]},{"title":"b","summary":"b","sources":[2]}]}\
            """);

    assertThat(new AiClient("bedrock", MODEL, stub).summarize(Category.AI, articles, 1)).hasSize(1);
  }

  @Test
  void answerKeepsOnlyUsedSources() {
    Stub stub =
        new Stub(
            """
            {"rejected":false,"answer":"고영향 AI는 위험 관리 의무를 져요.","sources":[2,2,7]}\
            """);

    AiClient.Answer answer =
        new AiClient("bedrock", MODEL, stub).answer("고영향 AI 의무가 뭐야?", articles);

    assertThat(answer.rejected()).isFalse();
    assertThat(answer.text()).isEqualTo("고영향 AI는 위험 관리 의무를 져요.");
    assertThat(answer.sources()).containsExactly(articles.get(1));
    assertThat(stub.requests.get(0).system().get(0).text()).contains("뉴스와 무관한 요청");
  }

  @Test
  void rejectedAnswerHasNoSources() {
    Stub stub =
        new Stub(
            """
            {"rejected":true,"answer":"뉴스와 관련된 질문에만 답할 수 있어요.","sources":[1]}\
            """);

    AiClient.Answer answer = new AiClient("bedrock", MODEL, stub).answer("이 문장 영어로 바꿔줘", articles);

    assertThat(answer.rejected()).isTrue();
    assertThat(answer.sources()).isEmpty();
  }

  @Test
  void noArticlesMeansNoAiCall() {
    Stub stub = new Stub("호출되면 안 됨");

    AiClient.Answer answer = new AiClient("bedrock", MODEL, stub).answer("아무 질문", List.of());

    assertThat(answer.text()).contains("관련 기사를 찾지 못했어요");
    assertThat(stub.requests).isEmpty();
  }

  @Test
  void unreadableResponseFails() {
    // 요약은 그 분야만 실패해 직전 요약을 유지하고, 질문은 FAILED가 된다
    AiClient ai = new AiClient("bedrock", MODEL, new Stub("죄송하지만 도와드릴 수 없습니다"));

    assertThatThrownBy(() -> ai.summarize(Category.AI, articles, 5))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> ai.answer("질문", articles)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void bedrockModeNeedsClient() {
    assertThatThrownBy(() -> new AiClient("bedrock", MODEL, (BedrockRuntimeClient) null))
        .isInstanceOf(IllegalStateException.class);
  }

  private static NewsArticle article(String title, String description) {
    return new NewsArticle(
        Category.AI,
        title,
        description,
        "전자신문",
        "https://www.etnews.com/" + title.hashCode(),
        Instant.parse("2026-10-14T00:00:00Z"));
  }

  /** 받은 요청을 기록하고 정해 둔 글을 돌려주는 가짜 Bedrock */
  private static class Stub implements BedrockRuntimeClient {

    final List<ConverseRequest> requests = new ArrayList<>();
    private final String reply;

    Stub(String reply) {
      this.reply = reply;
    }

    @Override
    public ConverseResponse converse(ConverseRequest request) {
      requests.add(request);
      return ConverseResponse.builder()
          .output(
              ConverseOutput.fromMessage(
                  Message.builder()
                      .role(ConversationRole.ASSISTANT)
                      .content(ContentBlock.fromText(reply))
                      .build()))
          .build();
    }

    @Override
    public String serviceName() {
      return "bedrock-runtime";
    }

    @Override
    public void close() {}
  }
}
