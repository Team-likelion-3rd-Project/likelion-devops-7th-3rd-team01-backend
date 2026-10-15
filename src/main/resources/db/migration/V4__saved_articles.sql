-- 저장한 기사 (FE 마이페이지, docs/api-spec.md 5.3~5.6). V2에서 지웠던 기능을 다시 넣는다.
-- 분야 페이지의 근거 기사 하나를 저장한다. 원본 기사(news_articles)는 7일 뒤 지워지므로 내용을 복사해 둔다.

CREATE TABLE saved_articles (
    saved_article_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(255) NOT NULL,
    url              VARCHAR(512) NOT NULL,
    press            VARCHAR(100) NULL,
    published_at     DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (saved_article_id),
    UNIQUE KEY uk_saved_articles_user_url (user_id, url),
    KEY idx_saved_articles_user_created (user_id, created_at),
    CONSTRAINT fk_saved_articles_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
