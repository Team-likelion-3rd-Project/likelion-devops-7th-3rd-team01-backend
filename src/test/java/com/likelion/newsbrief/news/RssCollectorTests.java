package com.likelion.newsbrief.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssCollectorTests {

  @Test
  void parsesRssItems() {
    // CDATA·HTML 태그·엔티티, 이스케이프 안 된 &, 링크·날짜가 이상한 항목을 함께 넣는다
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel>
          <item>
            <title><![CDATA[엔씨, &apos;신작&apos; 글로벌 출시]]></title>
            <link>https://www.etnews.com/20261008000001</link>
            <pubDate>Thu, 8 Oct 2026 12:31:59 +0900</pubDate>
            <description><![CDATA[<b>기자</b> = &quot;본문&quot;   요약]]></description>
          </item>
          <item><title>링크 없는 기사</title><media:content url="https://youtube.com/embed/x?v=1&feature=youtu.be"/></item>
          <item><title>날짜 이상</title><link>https://x.com/2</link><pubDate>어제</pubDate></item>
        </channel></rss>\
        """;

    List<NewsArticle> articles =
        RssCollector.parse(xml.getBytes(StandardCharsets.UTF_8), Category.GAME);

    assertThat(articles).hasSize(2);
    NewsArticle first = articles.get(0);
    assertThat(first.getTitle()).isEqualTo("엔씨, '신작' 글로벌 출시");
    assertThat(first.getDescription()).isEqualTo("기자 = \"본문\" 요약");
    assertThat(first.getUrl()).isEqualTo("https://www.etnews.com/20261008000001");
    assertThat(first.getPress()).isEqualTo("전자신문");
    assertThat(first.getPublishedAt()).isEqualTo(Instant.parse("2026-10-08T03:31:59Z"));
    assertThat(articles.get(1).getPublishedAt()).isNull();
  }

  @Test
  void rejectsDoctype() {
    String xml =
        """
        <?xml version="1.0"?>
        <!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>
        <rss><channel><item><title>&x;</title><link>https://x.com</link></item></channel></rss>\
        """;

    assertThatThrownBy(
            () -> RssCollector.parse(xml.getBytes(StandardCharsets.UTF_8), Category.GAME))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void generalItIsCollectedLast() {
    // 상위 피드를 쓰는 IT 일반이 먼저 모으면 세부 분야 기사를 가져가 버린다
    assertThat(Category.collectOrder()).hasSize(6).last().isEqualTo(Category.IT_GENERAL);
  }
}
