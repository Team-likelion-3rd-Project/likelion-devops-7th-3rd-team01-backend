-- 초기 스키마 (docs/erd.md). 시간은 모두 UTC로 저장한다.

CREATE TABLE users (
    user_id    BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    nickname   VARCHAR(20)  NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE questions (
    question_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    query_text   VARCHAR(500) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    answer       TEXT         NULL,
    sources      JSON         NULL,
    saved_at     DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at   DATETIME     NULL,
    completed_at DATETIME     NULL,
    PRIMARY KEY (question_id),
    KEY idx_questions_user_created (user_id, created_at),
    KEY idx_questions_user_saved (user_id, saved_at),
    KEY idx_questions_status_created (status, created_at),
    CONSTRAINT fk_questions_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

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
    CONSTRAINT fk_saved_articles_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE news_articles (
    article_id   BIGINT       NOT NULL AUTO_INCREMENT,
    category     VARCHAR(20)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT         NULL,
    press        VARCHAR(100) NULL,
    url          VARCHAR(512) NOT NULL,
    published_at DATETIME     NULL,
    collected_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (article_id),
    UNIQUE KEY uk_news_articles_url (url),
    KEY idx_news_articles_category_collected (category, collected_at),
    FULLTEXT KEY ft_news_articles_title_desc (title, description) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE news_issues (
    issue_id     BIGINT       NOT NULL AUTO_INCREMENT,
    category     VARCHAR(20)  NOT NULL,
    generated_at DATETIME     NOT NULL,
    rank_no      INT          NOT NULL,
    title        VARCHAR(255) NOT NULL,
    summary      TEXT         NOT NULL,
    sources      JSON         NOT NULL,
    mock         BOOLEAN      NOT NULL,
    PRIMARY KEY (issue_id),
    KEY idx_news_issues_category_generated (category, generated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
