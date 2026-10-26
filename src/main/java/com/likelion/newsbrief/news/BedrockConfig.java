package com.likelion.newsbrief.news;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * AI_MODE=bedrock일 때만 만든다 (mock에서는 AWS 설정이 없어도 앱이 뜬다). 인증은 SDK 기본 순서를 따른다: 환경 변수
 * AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY(온프렘) → EC2·EKS의 IAM 역할(AWS)
 */
@Configuration
class BedrockConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "ai.mode", havingValue = "bedrock")
  BedrockRuntimeClient bedrockRuntimeClient(@Value("${aws.region:ap-northeast-2}") String region) {
    return BedrockRuntimeClient.builder()
        .region(Region.of(region))
        // SDK 재시도까지 포함한 한 번의 호출 제한. 요약은 기사 30개를 보내 오래 걸릴 수 있다 (기사 8개에 12초).
        // 질문은 이보다 먼저 60초가 지나면 QuestionSweeper가 FAILED로 바꾼다
        .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(90)))
        .build();
  }
}
