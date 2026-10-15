-- 서비스 범위를 IT 6개 분야로 바꾼다 (docs/api-spec.md).
-- V1은 이미 적용된 파일이라 고치지 않고, 바뀐 내용은 이 파일로 덧붙인다.

-- 옛 분야(POLITICS, ECONOMY 등) 데이터는 새 코드가 읽지 못하므로 지운다.
-- 다음 요약 배치가 새 분야로 다시 채운다. 사용자 데이터(users, questions)는 건드리지 않는다.
DELETE FROM news_issues;
DELETE FROM news_articles;

-- 후보 기능이던 '저장한 기사'를 하지 않기로 해서 테이블을 지운다 (API도 구현하지 않음).
DROP TABLE saved_articles;
