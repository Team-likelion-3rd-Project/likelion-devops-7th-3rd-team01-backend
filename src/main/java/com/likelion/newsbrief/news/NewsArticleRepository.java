package com.likelion.newsbrief.news;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

  @Query("select a.url from NewsArticle a where a.url in :urls")
  Set<String> findExistingUrls(Collection<String> urls);

  List<NewsArticle> findTop30ByCategoryAndPublishedAtAfterOrderByPublishedAtDesc(
      Category category, Instant after);

  /**
   * 질문과 관련된 기사 후보. FULLTEXT ngram 인덱스의 관련도 점수가 minScore 이상인 것만, 높은 순으로 limit개. 최종 근거는 AI가 이 중에서 고른다
   * (AiClient)
   */
  @Query(
      value =
          """
          select * from news_articles
          where match(title, description) against (:query in natural language mode) >= :minScore
          order by match(title, description) against (:query in natural language mode) desc
          limit :limit\
          """,
      nativeQuery = true)
  List<NewsArticle> search(String query, double minScore, int limit);

  /** 그 분야의 마지막 요약을 저장한 뒤에 수집한 기사 수. 두 시각 모두 DB 기본값이라 앱·DB 시계 차이와 무관하다 */
  @Query(
      value =
          """
          select count(*) from news_articles a
          where a.category = :category
            and a.collected_at > (select max(i.created_at) from news_issues i where i.category = :category)\
          """,
      nativeQuery = true)
  long countCollectedAfterLatestSummary(String category);

  @Transactional
  @Modifying
  @Query(value = "delete from news_articles where collected_at < :before", nativeQuery = true)
  int deleteCollectedBefore(Instant before);
}
