package com.likelion.newsbrief.news;

import com.likelion.newsbrief.common.ApiException;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

/** IT 안의 6개 분야와 각 분야의 RSS 주소 (모두 전자신문). 선언 순서가 홈 화면 카드 순서다. 주소 목록: https://www.etnews.com/rss */
public enum Category {
  MOBILE_TELECOM("모바일/통신", "https://rss.etnews.com/03033.xml"),
  // 상위 피드라 다른 분야 기사도 섞여 있다. collectOrder()에서 맨 마지막에 모아 남은 기사만 갖는다
  IT_GENERAL(
      "IT 일반",
      "https://rss.etnews.com/03.xml",
      "https://rss.etnews.com/04.xml",
      "https://rss.etnews.com/06.xml"),
  COMPUTER("컴퓨터", "https://rss.etnews.com/04043.xml"),
  // 세부 '플랫폼' 피드(60028)는 갱신이 멈춰 있어 상위 '플랫폼/유통' 피드를 쓴다
  INTERNET("인터넷", "https://rss.etnews.com/60.xml"),
  GAME("게임", "https://rss.etnews.com/03104.xml"),
  // 세부 '과학' 피드(20020)는 갱신이 멈춰 있어 상위 '과학' 피드를 쓴다
  SCIENCE("과학", "https://rss.etnews.com/20.xml");

  private static final String PRESS = "전자신문";

  private final String displayName;
  private final List<String> feeds;

  Category(String displayName, String... feeds) {
    this.displayName = displayName;
    this.feeds = List.of(feeds);
  }

  public String displayName() {
    return displayName;
  }

  public String press() {
    return PRESS;
  }

  public List<String> feeds() {
    return feeds;
  }

  /**
   * 수집 순서. 같은 기사(URL)는 먼저 모은 분야에 들어가므로, 상위 피드를 쓰는 IT 일반을 맨 마지막에 모아 세부 분야에 들어가지 않은 기사만 IT 일반이 갖게 한다.
   */
  public static List<Category> collectOrder() {
    return Stream.concat(Stream.of(values()).filter(c -> c != IT_GENERAL), Stream.of(IT_GENERAL))
        .toList();
  }

  /** 없는 분야 코드면 404 */
  public static Category parse(String code) {
    try {
      return valueOf(code);
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "없는 분야입니다.");
    }
  }
}
