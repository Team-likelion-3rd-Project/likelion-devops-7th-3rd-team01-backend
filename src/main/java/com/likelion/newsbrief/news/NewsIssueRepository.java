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

  /**
   * 새 기사가 없어 다시 요약하지 않은 분야의 최근 요약을 이번 배치 시각으로 올린다. 화면의 generatedAt이 "마지막으로 확인한 시각"이 되어, 배치가
   * 정상인데 갱신 지연 안내가 뜨지 않는다. 실패한 분야는 올리지 않아 지연으로 보인다
   */
  @Transactional
  @Modifying(clearAutomatically = true)
  @Query(
      """
      update NewsIssue i set i.generatedAt = :checkedAt
      where i.category = :category and i.generatedAt = :latest\
      """)
  int markChecked(Category category, Instant latest, Instant checkedAt);

  @Transactional
  @Modifying
  @Query(value = "delete from news_issues where generated_at < :before", nativeQuery = true)
  int deleteGeneratedBefore(Instant before);
}
