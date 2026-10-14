package com.likelion.newsbrief.news;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

/**
 * 홈·분야 응답을 Redis에 캐시한다 (캐시 우선, 없으면 DB). 요약은 30분마다 바뀌므로 TTL도 30분이고, 요약 배치가 끝나면 캐시를 비워 새 요약이 바로 보이게
 * 한다. Redis가 죽어도 예외를 내지 않고 DB에서 읽는다 (장애 시나리오 B1). MySQL이 데이터의 기준이다.
 */
@Component
public class NewsCache {

  static final Duration TTL = Duration.ofMinutes(30);

  // 응답 모양이 바뀌면 v2로 올려 옛 캐시를 무시한다
  private static final String PREFIX = "news:v1:";

  private static final Logger log = LoggerFactory.getLogger(NewsCache.class);

  private final RedisTemplate<String, Object> redis;

  NewsCache(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(
        new JdkSerializationRedisSerializer(NewsCache.class.getClassLoader()));
    template.afterPropertiesSet();
    this.redis = template;
  }

  static String homeKey() {
    return PREFIX + "home";
  }

  static String categoryKey(Category category) {
    return PREFIX + "category:" + category.name();
  }

  /** 캐시에 있으면 그 값을, 없거나 Redis를 못 쓰면 loader(DB)로 읽고 캐시에 넣는다 */
  @SuppressWarnings("unchecked")
  <T extends Serializable> T get(String key, Supplier<T> loader) {
    try {
      Object cached = redis.opsForValue().get(key);
      if (cached != null) {
        return (T) cached;
      }
    } catch (Exception e) {
      log.warn("뉴스 캐시 읽기 실패, DB에서 읽습니다: {}", e.toString());
    }
    T value = loader.get();
    try {
      redis.opsForValue().set(key, value, TTL);
    } catch (Exception e) {
      log.warn("뉴스 캐시 저장 실패: {}", e.toString());
    }
    return value;
  }

  /** 요약 배치가 끝난 뒤 호출한다. 실패해도 TTL(30분)이 지나면 새 요약이 보인다 */
  public void evictAll() {
    List<String> keys = new ArrayList<>();
    keys.add(homeKey());
    for (Category category : Category.values()) {
      keys.add(categoryKey(category));
    }
    try {
      redis.delete(keys);
    } catch (Exception e) {
      log.warn("뉴스 캐시 비우기 실패. TTL이 지나면 새 요약이 보입니다: {}", e.toString());
    }
  }
}
