package com.likelion.newsbrief.article;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SavedArticleRepository extends JpaRepository<SavedArticle, Long> {

  boolean existsByUserIdAndUrl(Long userId, String url);

  /** 최근에 저장한 순. 같은 초에 저장했으면 나중에 저장한 것이 먼저 */
  Slice<SavedArticle> findByUserIdOrderByCreatedAtDescSavedArticleIdDesc(
      Long userId, Pageable pageable);

  /** 분야 화면에서 저장 버튼을 채워 그리기 위한 URL 목록 */
  @Query("select a.url from SavedArticle a where a.userId = :userId")
  List<String> findUrls(Long userId);

  @Transactional
  @Modifying
  @Query("delete from SavedArticle a where a.userId = :userId and a.url = :url")
  int deleteOwned(Long userId, String url);
}
