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

  /** 질문과 관련된 기사. FULLTEXT ngram 인덱스로 관련도 높은 순 */
  @Query(
      value =
          """
          select * from news_articles
          where match(title, description) against (:query in natural language mode)
          limit :limit\
          """,
      nativeQuery = true)
  List<NewsArticle> search(String query, int limit);

  @Transactional
  @Modifying
  @Query(value = "delete from news_articles where collected_at < :before", nativeQuery = true)
  int deleteCollectedBefore(Instant before);
}
