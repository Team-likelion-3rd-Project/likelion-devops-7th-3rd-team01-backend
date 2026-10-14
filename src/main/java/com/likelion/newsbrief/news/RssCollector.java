package com.likelion.newsbrief.news;

import java.io.StringReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** 분야별 RSS를 읽어 새 기사만 저장한다 */
@Component
public class RssCollector {

  private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!#?\\w+;)");

  private final NewsArticleRepository articles;
  private final RestClient http;

  RssCollector(NewsArticleRepository articles) {
    this.articles = articles;
    // 뉴스 사이트가 느려도 배치가 멈추지 않게 타임아웃을 둔다
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(10));
    this.http =
        RestClient.builder()
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.USER_AGENT, "newsbrief/1.0")
            .build();
  }

  /**
   * @return 새로 저장한 기사 수. 피드를 하나라도 못 읽으면 예외
   */
  public int collect(Category category) {
    Map<String, NewsArticle> byUrl = new LinkedHashMap<>();
    for (String feed : category.feeds()) {
      byte[] xml = http.get().uri(feed).retrieve().body(byte[].class);
      // 여러 피드에 같은 기사가 있을 수 있다
      parse(xml, category).forEach(a -> byUrl.putIfAbsent(a.getUrl(), a));
    }
    if (byUrl.isEmpty()) {
      return 0;
    }
    Set<String> existing = articles.findExistingUrls(byUrl.keySet());
    List<NewsArticle> fresh =
        byUrl.values().stream().filter(a -> !existing.contains(a.getUrl())).toList();
    articles.saveAll(fresh);
    return fresh.size();
  }

  static List<NewsArticle> parse(byte[] xml, Category category) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // 외부에서 받은 XML이라 DTD를 막는다 (XXE 방지)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      // 피드에 이스케이프 안 된 &(주소의 ?a=1&b=2)가 섞여 있을 수 있어 고쳐서 읽는다
      String text =
          BARE_AMPERSAND.matcher(new String(xml, StandardCharsets.UTF_8)).replaceAll("&amp;");
      Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
      NodeList items = doc.getElementsByTagName("item");
      List<NewsArticle> result = new ArrayList<>();
      for (int i = 0; i < items.getLength(); i++) {
        Element item = (Element) items.item(i);
        String title = clean(text(item, "title"));
        String url = text(item, "link").trim();
        if (title.isEmpty() || !url.startsWith("http") || url.length() > 512) {
          continue;
        }
        result.add(
            new NewsArticle(
                category,
                truncate(title, 255),
                clean(text(item, "description")),
                category.press(),
                url,
                parseDate(text(item, "pubDate"))));
      }
      return result;
    } catch (Exception e) {
      throw new IllegalStateException(category + " RSS를 해석하지 못했습니다.", e);
    }
  }

  private static String text(Element item, String tag) {
    NodeList nodes = item.getElementsByTagName(tag);
    return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
  }

  /** HTML 태그와 &amp;quot; 같은 엔티티를 지우고 공백을 정리한다 */
  static String clean(String s) {
    if (s == null) {
      return "";
    }
    String noTags = s.replaceAll("<[^>]*>", " ").replace("&apos;", "'");
    return HtmlUtils.htmlUnescape(noTags).replaceAll("\\s+", " ").trim();
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  private static Instant parseDate(String s) {
    try {
      return ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
