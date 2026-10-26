package com.likelion.newsbrief.news;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NewsIssueRepository extends JpaRepository<NewsIssue, Long> {

  /** 분야마다 가장 최근 배치의 이슈들. 갱신에 실패한 분야는 직전 배치가 그대로 나온다 */
  @Query(
      """
      select i from NewsIssue i
      where i.generatedAt = (select max(j.generatedAt) from NewsIssue j where j.category = i.category)
      order by i.category, i.rankNo\
      """)
  List<NewsIssue> findLatest();

  @Query(
      """
      select i from NewsIssue i
      where i.category = :category
        and i.generatedAt = (select max(j.generatedAt) from NewsIssue j where j.category = :category)
      order by i.rankNo\
      """)
  List<NewsIssue> findLatest(Category category);

  @Transactional
  @Modifying
  @Query(value = "delete from news_issues where generated_at < :before", nativeQuery = true)
  int deleteGeneratedBefore(Instant before);
}
