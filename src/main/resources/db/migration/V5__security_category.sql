-- 분야 개편(10/15 회의): 인터넷(INTERNET) 제외, 보안(SECURITY, 전자신문 04045) 추가 (docs/api-spec.md).
-- 인터넷 분야 기사·요약을 지운다. 보안 분야는 다음 요약 배치가 채운다. 저장한 기사는 그대로 둔다.
DELETE FROM news_articles WHERE category = 'INTERNET';
DELETE FROM news_issues WHERE category = 'INTERNET';
