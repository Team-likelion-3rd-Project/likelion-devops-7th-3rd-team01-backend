-- 분야 개편(GAME 제외, AI 추가)과 이슈 상세 필드 (docs/api-spec.md).

-- 게임 분야를 뺀다. 남은 분야 기사는 그대로 쓴다
DELETE FROM news_articles WHERE category = 'GAME';

-- 옛 요약에는 상세 필드가 없어 지운다. 다음 요약 배치(30분 이내)가 새 형식으로 다시 채운다
DELETE FROM news_issues;

ALTER TABLE news_issues
    ADD COLUMN key_points JSON NOT NULL AFTER summary,
    ADD COLUMN timeline   JSON NOT NULL AFTER key_points,
    ADD COLUMN glossary   JSON NOT NULL AFTER timeline;
