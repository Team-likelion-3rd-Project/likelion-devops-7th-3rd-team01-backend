-- V6이 기존 요약의 created_at을 마이그레이션 시각으로 채워, 그 전에 수집했지만 요약하지 못한 기사가 "요약보다 오래된 기사"로 보였다.
-- 기존 요약은 생성 시각(generated_at, UTC 저장)으로 되돌린다. DB 시간대가 UTC가 아니면 한 번 더 요약할 뿐 빠뜨리지는 않는다.
UPDATE news_issues SET created_at = generated_at WHERE created_at > generated_at;
