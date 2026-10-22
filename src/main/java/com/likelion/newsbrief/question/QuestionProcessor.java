package com.likelion.newsbrief.question;

import com.likelion.newsbrief.news.AiClient;
import com.likelion.newsbrief.news.NewsArticle;
import com.likelion.newsbrief.news.NewsArticleRepository;
import com.likelion.newsbrief.news.Source;
import com.likelion.newsbrief.question.Question.Status;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 질문 하나 처리: 수집한 기사에서 관련 기사 찾기 → AI 답변 → 저장. 외부 검색 API는 쓰지 않는다 (범위 경계) */
@Component
class QuestionProcessor {

  // 검색 정확도 측정(perf/search, docs/search-eval.md)으로 정한 값.
  // 후보는 넉넉히 15개: 8개면 다른 표현으로 물은 질문의 정답을 놓쳤다 (11위).
  // 최소 점수 3: 글자 한두 개만 우연히 겹친 후보를 버린다. 맞는 기사가 3.1점으로 걸린 경우가 있어 더 올리지 않는다
  private static final int CANDIDATES = 15;
  private static final double MIN_SCORE = 3.0;

  private static final Logger log = LoggerFactory.getLogger(QuestionProcessor.class);

  private final QuestionRepository questions;
  private final NewsArticleRepository articles;
  private final AiClient ai;

  QuestionProcessor(QuestionRepository questions, NewsArticleRepository articles, AiClient ai) {
    this.questions = questions;
    this.articles = articles;
    this.ai = ai;
  }

  void process(long id) {
    // 대기열에 같은 ID가 두 번 들어와도(재투입) 한 워커만 처리한다
    if (questions.claim(id, Status.PENDING, Status.PROCESSING, Instant.now()) == 0) {
      return;
    }
    try {
      Question question = questions.findById(id).orElse(null);
      if (question == null) {
        return;
      }
      List<NewsArticle> found = articles.search(question.getQueryText(), MIN_SCORE, CANDIDATES);
      AiClient.Answer answer = ai.answer(question.getQueryText(), found);
      // 답에 실제로 쓴 기사만 근거로 보여 준다 (거절이면 빈 목록)
      List<Source> sources = answer.sources().stream().map(Source::of).toList();
      questions.finish(
          id,
          Status.PROCESSING,
          answer.rejected() ? Status.REJECTED : Status.COMPLETED,
          answer.text(),
          sources,
          Instant.now());
    } catch (Exception e) {
      log.error("질문 {} 처리 실패", id, e);
      questions.finish(id, Status.PROCESSING, Status.FAILED, null, null, Instant.now());
    }
  }
}
