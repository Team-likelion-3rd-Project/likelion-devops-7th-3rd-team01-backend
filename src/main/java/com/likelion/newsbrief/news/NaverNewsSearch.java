package com.likelion.newsbrief.news;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DB에 관련 기사가 없을 때 쓰는 네이버 뉴스 검색. 키가 없거나 실패하면 빈 목록 (질문은 FAILED가 아니라 "찾지 못했다" 안내). 검색 결과에는 분야가 없어
 * news_articles에 저장하지 않고 답변에만 쓴다.
 */
@Component
public class NaverNewsSearch {

  private static final Logger log = LoggerFactory.getLogger(NaverNewsSearch.class);

  private final RestClient http;
  private final boolean enabled;

  record Response(List<Item> items) {}

  record Item(String title, String originallink, String link, String description, String pubDate) {}

  NaverNewsSearch(
      @Value("${naver.client-id:}") String clientId,
      @Value("${naver.client-secret:}") String clientSecret) {
    this.enabled = !clientId.isBlank() && !clientSecret.isBlank();
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.http =
        RestClient.builder()
            .requestFactory(factory)
            .baseUrl("https://openapi.naver.com")
            .defaultHeader("X-Naver-Client-Id", clientId)
            .defaultHeader("X-Naver-Client-Secret", clientSecret)
            .build();
  }

  public List<NewsArticle> search(String query, int limit) {
    if (!enabled) {
      return List.of();
    }
    try {
      Response response =
          http.get()
              .uri(
                  b ->
                      b.path("/v1/search/news.json")
                          .queryParam("query", query)
                          .queryParam("display", limit)
                          .queryParam("sort", "sim")
                          .build())
              .retrieve()
              .body(Response.class);
      return response == null || response.items() == null
          ? List.of()
          : response.items().stream().map(NaverNewsSearch::toArticle).toList();
    } catch (Exception e) {
      log.warn("네이버 뉴스 검색 실패: {}", e.toString());
      return List.of();
    }
  }

  static NewsArticle toArticle(Item item) {
    String url =
        item.originallink() == null || item.originallink().isBlank()
            ? item.link()
            : item.originallink();
    return new NewsArticle(
        null,
        RssCollector.clean(item.title()),
        RssCollector.clean(item.description()),
        press(url),
        url,
        parseDate(item.pubDate()));
  }

  /** 네이버 검색 결과에는 언론사 이름이 없어 원문 주소의 도메인을 쓴다 */
  private static String press(String url) {
    try {
      return URI.create(url).getHost().replaceFirst("^www\\.", "");
    } catch (Exception e) {
      return null;
    }
  }

  private static Instant parseDate(String s) {
    try {
      return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException | NullPointerException e) {
      return null;
    }
  }
}
