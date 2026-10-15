package com.likelion.newsbrief.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

  /** DB 정렬 규칙(utf8mb4_unicode_ci)상 대소문자를 구분하지 않는다 */
  Optional<User> findByEmail(String email);

  /** 사용자 행 잠금 (SELECT ... FOR UPDATE). 같은 사용자의 요청을 한 줄로 세운다 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.userId = :userId")
  Optional<User> findForUpdate(Long userId);
}
