-- 요약을 실제로 저장한 시각 (DB 시계). 요약 배치가 "마지막 요약 뒤에 들어온 기사"를 news_articles.collected_at과 같은 시계로 비교한다.
-- generated_at은 배치 시작 시각(앱 시계)이라 같은 회차에 수집한 기사보다 앞선다.
ALTER TABLE news_issues ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
