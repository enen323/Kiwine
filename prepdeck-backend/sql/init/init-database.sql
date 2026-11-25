-- ============================================================
-- MianBa AI Interview Platform - Phase 1 MVP
-- Database: MySQL 8.0+
-- Init Script - Creates all Phase 1 tables, indexes, and comments
-- ============================================================

-- ============================================================
-- PG -> MySQL conversion notes (design: 2026-11-24-prepdeck-monolith-mysql, §7)
--   - interview schema dropped: all tables live directly in the prepdeck database
--   - snowflake sequences dropped: MyBatis-Plus ASSIGN_ID assigns IDs in the app layer
--   - JSONB -> JSON, TIMESTAMP -> DATETIME
--   - update_time is app-maintained (PG's 14 auto-update triggers are gone,
--     no ON UPDATE CURRENT_TIMESTAMP by design — known difference #8)
--   - question.search_vector + GIN index + trigger dropped (full-text search
--     moved to LIKE in the mapper — design §7b)
--   - the two PG partial unique indexes are NOT built (design §7c, see notes below)
--   - job-service tables absorbed from V1__create_job_tables.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS prepdeck CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE prepdeck;

-- ============================================================
-- 1. user_account — User accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS user_account (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    phone           VARCHAR(20)     NOT NULL COMMENT 'Mobile phone number, used as login identifier',
    password        VARCHAR(256)    NOT NULL COMMENT 'Bcrypt-hashed password',
    nickname        VARCHAR(50)     COMMENT 'Display nickname',
    avatar_url      VARCHAR(512)    COMMENT 'Avatar image URL or object storage key',
    fingerprint     VARCHAR(255)    COMMENT 'Device fingerprint (no DB-level unique index — see "Partial unique indexes" below)',
    target_position VARCHAR(100)    COMMENT 'Target interview position, e.g. Java Backend',
    target_company  VARCHAR(50)     COMMENT 'Target company, e.g. Alibaba, Tencent',
    status          SMALLINT        NOT NULL DEFAULT 1 COMMENT 'Account status: 1=active, 0=locked, -1=banned',
    last_login_time DATETIME        COMMENT 'Timestamp of the most recent successful login',
    ai_settings     JSON            COMMENT 'User AI settings (model, temperature, prompt) as JSON',

    -- Common fields
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last update timestamp (app-maintained, no ON UPDATE)',
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Soft-delete flag: FALSE=active, TRUE=deleted',
    version         INTEGER         NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version counter'
) COMMENT = 'User accounts — phone login, profile, and career target';

-- ============================================================
-- 2. user_quota — User interview and resume-chat quotas
-- ============================================================
CREATE TABLE IF NOT EXISTS user_quota (
    user_id               BIGINT          PRIMARY KEY COMMENT 'Owner user ID, FK to user_account',
    interview_free_used   INT             NOT NULL DEFAULT 0 COMMENT 'Number of free interview sessions consumed',
    resume_chat_free_used INT             NOT NULL DEFAULT 0 COMMENT 'Number of free resume-chat sessions consumed',
    interview_paid        INT             NOT NULL DEFAULT 0 COMMENT 'Number of paid interview sessions available',
    resume_chat_paid      INT             NOT NULL DEFAULT 0 COMMENT 'Number of paid resume-chat sessions available',
    create_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN         NOT NULL DEFAULT FALSE,
    version               INT             NOT NULL DEFAULT 1
) COMMENT = 'User quota counters — tracks free and paid usage for interviews and resume chat';

-- ============================================================
-- 3. resume — User resumes
-- ============================================================
CREATE TABLE IF NOT EXISTS resume (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    user_id         BIGINT          NOT NULL COMMENT 'Owner user ID, FK to user_account',
    file_name       VARCHAR(255)    COMMENT 'Original uploaded file name',
    file_url        VARCHAR(512)    COMMENT 'File storage URL or object storage key',
    content_text    TEXT            COMMENT 'Full text extracted from the resume file (OCR/parsed)',
    content_json    JSON            COMMENT 'Structured resume data in JSON format',
    is_active       BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Active resume flag: only one resume per user should be active at a time',

    -- Phase 1 additions: resume builder columns
    -- (originally added via ALTER TABLE ADD COLUMN IF NOT EXISTS,
    --  which MySQL does not support — folded into the table definition)
    title           VARCHAR(255)    COMMENT 'Resume display title',
    template        VARCHAR(64)     NOT NULL DEFAULT 'classic' COMMENT 'Template name, e.g. classic, modern',
    language        VARCHAR(8)      NOT NULL DEFAULT 'zh' COMMENT 'Resume language, e.g. zh, en',
    theme_config    JSON            COMMENT 'JSON: color/font/spacing/margin config',
    is_public       BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Public share flag',
    share_token     VARCHAR(64)     COMMENT 'Unique share token for public access',
    share_password  VARCHAR(255)    COMMENT 'BCrypt-hashed share password',
    view_count      INTEGER         NOT NULL DEFAULT 0 COMMENT 'Public view counter',

    -- Common fields
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    -- Foreign key
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) COMMENT = 'User resumes — uploaded files and parsed content';

-- ============================================================
-- 2b. resume_section — Resume structured sections
-- ============================================================
CREATE TABLE IF NOT EXISTS resume_section (
    id              BIGINT          PRIMARY KEY,

    resume_id       BIGINT          NOT NULL,
    type            VARCHAR(32)     NOT NULL COMMENT 'Section type: personalInfo, summary, education, experience, project, skills, certification, language, publication, custom',
    title           VARCHAR(255),
    sort_order      INT,
    visible         BOOLEAN         NOT NULL DEFAULT TRUE,
    content         JSON            COMMENT 'JSON content varying by section type',

    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT fk_resume_section_resume FOREIGN KEY (resume_id) REFERENCES resume(id)
) COMMENT = 'Resume sections — structured blocks (personalInfo, education, etc.)';

-- ============================================================
-- 3. interview_record — Interview sessions
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_record (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    resume_id       BIGINT,
    user_id         BIGINT          NOT NULL COMMENT 'Candidate user ID, FK to user_account',
    company         VARCHAR(50)     COMMENT 'Target company for this interview session',
    position        VARCHAR(100)    COMMENT 'Target position for this interview session',
    status          VARCHAR(20)     NOT NULL DEFAULT 'CREATED' COMMENT 'Session status: CREATED, IN_PROGRESS, COMPLETED, TERMINATED',
    current_stage   VARCHAR(30)     NOT NULL DEFAULT 'SELF_INTRO' COMMENT 'Current interview stage: SELF_INTRO, PROJECT_DEEP_DIVE, TECH_ASSESSMENT, REVERSE_QA, COMPLETED',
    stage_order     INTEGER         NOT NULL DEFAULT 0 COMMENT 'Order of the current stage within the interview flow (0-based)',
    total_questions INTEGER         NOT NULL DEFAULT 0 COMMENT 'Total number of questions asked in this session',

    -- Scoring (nullable until evaluation completes)
    score_total     INTEGER         COMMENT 'Overall score (0-100)',
    score_tech      INTEGER         COMMENT 'Technical foundation score (0-100)',
    score_expression INTEGER        COMMENT 'Expression and communication score (0-100)',
    score_project   INTEGER         COMMENT 'Project experience score (0-100)',
    score_logic     INTEGER         COMMENT 'Logical thinking score (0-100)',
    score_adaptability INTEGER      COMMENT 'Adaptability score (0-100)',
    suggestion      TEXT            COMMENT 'AI-generated improvement suggestion text',

    -- Timing
    started_at      DATETIME        COMMENT 'When the interview session started',
    completed_at    DATETIME        COMMENT 'When the interview session completed or was terminated',
    duration_seconds INTEGER        COMMENT 'Total session duration in seconds',

    -- Common fields
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    -- Foreign key
    CONSTRAINT fk_interview_record_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) COMMENT = 'Interview sessions — each row is one complete interview attempt';

-- ============================================================
-- 4. interview_message — Chat messages in interviews
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_message (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    interview_id    BIGINT          NOT NULL COMMENT 'Parent interview session ID, FK to interview_record',
    role            VARCHAR(10)     NOT NULL COMMENT 'Message sender: AI or USER',
    content         TEXT            NOT NULL COMMENT 'Message content text (markdown supported for AI messages)',
    question_type   VARCHAR(30)     COMMENT 'Question category: SELF_INTRO, TECH_PROJECT, TECH_BASIC, ALGORITHM, SYSTEM_DESIGN, BEHAVIORAL, REVERSE_QA',
    sequence_num    INTEGER         NOT NULL COMMENT 'Sequential message number within the interview (0-based)',
    token_count     INTEGER         NOT NULL DEFAULT 0 COMMENT 'Number of tokens in this message (for AI usage tracking)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Common fields (minimal — interview messages have different lifecycle needs)
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    -- Foreign key
    CONSTRAINT fk_interview_message_record FOREIGN KEY (interview_id) REFERENCES interview_record(id)
) COMMENT = 'Chat messages within an interview session — questions and answers';

-- ============================================================
-- 5. payment_record — WeChat Pay payment transactions
-- ============================================================
CREATE TABLE IF NOT EXISTS payment_record (
    id               BIGINT          PRIMARY KEY,
    user_id          BIGINT          NOT NULL COMMENT 'Payer user ID, FK to user_account',
    amount           DECIMAL(10,2)   NOT NULL COMMENT 'Payment amount in CNY',
    service_type     VARCHAR(20)     NOT NULL COMMENT 'Service type: INTERVIEW or RESUME_CHAT',
    status           VARCHAR(20)     NOT NULL COMMENT 'Payment status: PENDING, SUCCESS, FAILED, REFUNDED',
    wechat_trade_no  VARCHAR(64)     COMMENT 'WeChat Pay transaction number',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    version          INT             NOT NULL DEFAULT 1
) COMMENT = 'WeChat Pay payment records — tracks quota purchase transactions';

-- ============================================================
-- 6. interview_question_score — Per-question scoring in interviews
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_question_score (
    id                   BIGINT          PRIMARY KEY,
    interview_id         BIGINT          NOT NULL COMMENT 'Parent interview session ID, FK to interview_record',
    message_id           BIGINT          NOT NULL COMMENT 'Parent message ID, FK to interview_message',
    question_type        VARCHAR(32)     NOT NULL COMMENT 'Question category: SELF_INTRO, TECH_PROJECT, TECH_BASIC, ALGORITHM, SYSTEM_DESIGN, BEHAVIORAL, REVERSE_QA',
    question_description TEXT            NOT NULL COMMENT 'The question text that was scored',
    score                INTEGER         NOT NULL COMMENT 'Score for this question (0-100)',
    feedback             TEXT            COMMENT 'AI-generated feedback for this specific answer',
    create_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              BOOLEAN         NOT NULL DEFAULT FALSE,
    version              INTEGER         NOT NULL DEFAULT 1
) COMMENT = 'Per-question evaluation scores within an interview session';

-- ============================================================
-- 7. question — Question bank
-- ============================================================
-- NOTE: the PG search_vector column, its GIN index and its
-- setweight/to_tsvector trigger are intentionally absent (design §7b).
-- Full-text search is done with LIKE in QuestionMapper.xml.
CREATE TABLE IF NOT EXISTS question (
    id              BIGINT          PRIMARY KEY,
    title           VARCHAR(200)    NOT NULL COMMENT 'Question title/summary',
    content         TEXT            NOT NULL COMMENT 'Full question description',
    answer          JSON            NOT NULL DEFAULT ('{}') COMMENT 'JSON: { summary, analysis, code }',
    type            VARCHAR(20)     NOT NULL DEFAULT 'SCENARIO' COMMENT 'SCENARIO / ALGORITHM / SYSTEM_DESIGN / BEHAVIORAL / LANGUAGE',
    difficulty      VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM' COMMENT 'EASY / MEDIUM / HARD',
    easy_mistake    VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / EASY_MISTAKE / HIGH_MISTAKE',
    interview_count INTEGER         NOT NULL DEFAULT 0 COMMENT 'Number of times asked in real interviews',
    views           INTEGER         NOT NULL DEFAULT 0 COMMENT 'View count',
    favorites       INTEGER         NOT NULL DEFAULT 0 COMMENT 'Favorite count',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0
) COMMENT = 'Question bank — covers scenarios, algorithms, system design, behavioral, and language questions';

-- ============================================================
-- 8. tag — Predefined tags for questions
-- ============================================================
CREATE TABLE IF NOT EXISTS tag (
    id          BIGINT          PRIMARY KEY,
    name        VARCHAR(50)     NOT NULL UNIQUE COMMENT 'Tag display name (unique)',
    description VARCHAR(200)    COMMENT 'Tag description',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    version     INTEGER         NOT NULL DEFAULT 0
) COMMENT = 'Predefined tags for categorizing questions';

-- ============================================================
-- 9. question_tag — Question-to-tag mapping (many-to-many)
-- ============================================================
CREATE TABLE IF NOT EXISTS question_tag (
    question_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (question_id, tag_id)
) COMMENT = 'Question-to-tag many-to-many mapping';

-- ============================================================
-- 10. user_favorite — User favorite/bookmark questions
-- ============================================================
CREATE TABLE IF NOT EXISTS user_favorite (
    id          BIGINT          PRIMARY KEY,
    user_id     BIGINT          NOT NULL COMMENT 'Owner user ID',
    question_id BIGINT          NOT NULL COMMENT 'Favorited question ID',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    version     INTEGER         NOT NULL DEFAULT 0,
    UNIQUE (user_id, question_id)
) COMMENT = 'User favorite/bookmark questions';

-- ============================================================
-- 11. daily_challenge — Daily challenge record per user
-- ============================================================
CREATE TABLE IF NOT EXISTS daily_challenge (
    id             BIGINT      PRIMARY KEY,
    user_id        BIGINT      NOT NULL COMMENT 'Owner user ID',
    question_id    BIGINT      NOT NULL COMMENT 'Assigned question ID',
    challenge_date DATE        NOT NULL COMMENT 'Challenge date (UTC or server local date)',
    completed      BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'Whether user has viewed/answered the daily question',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    version        INTEGER     NOT NULL DEFAULT 0,
    UNIQUE (user_id, challenge_date)
) COMMENT = 'Daily challenge record — one random question per user per day';

-- ============================================================
-- 12. user_practice_record — User practice history (for recommendations)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_practice_record (
    id              BIGINT          PRIMARY KEY,
    user_id         BIGINT          NOT NULL COMMENT 'Owner user ID',
    question_id     BIGINT          NOT NULL COMMENT 'Practiced question ID',
    source_type     VARCHAR(32)     COMMENT 'Practice source: INTERVIEW, RECOMMENDATION, DAILY, MANUAL',
    source_ref_id   BIGINT          COMMENT 'Source reference ID (interviewId, recommendationId, etc.)',
    practiced_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When the question was practiced',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0
) COMMENT = 'User practice history — tracks which questions a user has practiced, used for recommendation deduplication';

-- ============================================================
-- Partial unique indexes — INTENTIONALLY NOT BUILT (design §7c, decision A)
--
-- MySQL cannot express PG partial unique indexes (WHERE ...), so both
-- are dropped here and the guarantee moved to the application layer.
--
-- 1) uk_user_account_fingerprint ON user_account (fingerprint)
--    WHERE fingerprint IS NOT NULL
--    PG purpose: fingerprint dedup. Decorative — no DB-level guarantee.
--
-- 2) uk_user_active_interview ON interview_record (user_id)
--    WHERE deleted = false AND status IN ('CREATED', 'IN_PROGRESS')
--    PG purpose: "one active interview per user" hard business rule.
--    Guarded in the app layer by
--    InterviewServiceImpl.checkNoActiveInterview (interview/service/impl/InterviewServiceImpl.java).
--
-- shortcut: 并发双开（同时创建两个进行中面试）的竞态窗口存在（应用层检查非原子），
--           生产化需改为生成列 + 唯一索引。
-- ============================================================

-- ============================================================
-- Unique constraints
-- ============================================================
ALTER TABLE user_account
    ADD CONSTRAINT uk_user_account_phone UNIQUE (phone);

-- ============================================================
-- Indexes
-- ============================================================

-- user_account indexes
CREATE INDEX idx_user_account_status        ON user_account(status);
CREATE INDEX idx_user_account_create_time   ON user_account(create_time);

-- resume indexes
CREATE INDEX idx_resume_user_id    ON resume(user_id);
CREATE INDEX idx_resume_is_active  ON resume(is_active);
CREATE INDEX idx_resume_user_active ON resume(user_id, is_active);

-- resume_section indexes
CREATE INDEX idx_resume_section_rid ON resume_section(resume_id);

-- interview_record indexes
CREATE INDEX idx_interview_record_user_id    ON interview_record(user_id);
CREATE INDEX idx_interview_record_company    ON interview_record(company);
CREATE INDEX idx_interview_record_status     ON interview_record(status);
CREATE INDEX idx_interview_record_user_status ON interview_record(user_id, status);
CREATE INDEX idx_interview_record_create_time ON interview_record(create_time);

-- interview_message indexes
CREATE INDEX idx_interview_message_interview_id  ON interview_message(interview_id);
CREATE UNIQUE INDEX uk_interview_message_seq ON interview_message(interview_id, sequence_num);
CREATE INDEX idx_interview_message_created_at    ON interview_message(created_at);

-- interview_question_score indexes
CREATE INDEX idx_iqs_interview_id ON interview_question_score(interview_id);
CREATE INDEX idx_iqs_question_type ON interview_question_score(question_type);

-- payment_record indexes
CREATE INDEX idx_payment_record_user_id ON payment_record(user_id);
CREATE INDEX idx_payment_record_status ON payment_record(status);

-- question indexes
-- (idx_question_search GIN(search_vector) dropped with the column — design §7b)
CREATE INDEX idx_question_heat
    ON question (interview_count DESC, favorites DESC, views DESC);
CREATE INDEX idx_question_difficulty
    ON question (difficulty);
CREATE INDEX idx_question_type
    ON question (type);

-- tag indexes
CREATE INDEX idx_tag_name ON tag(name);

-- user_favorite indexes
CREATE INDEX idx_user_favorite_user_id ON user_favorite(user_id);
CREATE INDEX idx_user_favorite_question_id ON user_favorite(question_id);

-- daily_challenge indexes
CREATE INDEX idx_daily_challenge_user_date
    ON daily_challenge(user_id, challenge_date);

-- user_practice_record indexes
CREATE INDEX idx_user_practice_user_question
    ON user_practice_record(user_id, question_id);
CREATE INDEX idx_user_practice_user_practiced
    ON user_practice_record(user_id, practiced_at);

-- ============================================================
-- job-service tables — ABSORBED from V1__create_job_tables.sql
-- (the flyway migration file was deleted; its content lives here now)
-- ============================================================

-- 投递记录主表
CREATE TABLE IF NOT EXISTS job_applications (
    id          VARCHAR(64)  PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    resume_id   VARCHAR(64)  NULL,
    company     VARCHAR(255) NOT NULL,
    position    VARCHAR(255) NOT NULL,
    channel     VARCHAR(64)  NULL,
    status      VARCHAR(32)  NOT NULL,
    applied_at  DATETIME     NOT NULL,
    notes       TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_applications_user_applied
    ON job_applications (user_id, applied_at DESC);

CREATE INDEX idx_job_applications_resume
    ON job_applications (resume_id);

-- AI 求职信表
CREATE TABLE IF NOT EXISTS ai_cover_letters (
    id              VARCHAR(64)  PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    resume_id       VARCHAR(64)  NOT NULL,
    application_id  VARCHAR(64)  NULL,
    company         VARCHAR(255) NOT NULL,
    position        VARCHAR(255) NOT NULL,
    job_description TEXT         NULL,
    extra_notes     TEXT         NULL,
    output_language VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    body            TEXT         NOT NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL
);

CREATE INDEX idx_ai_cover_letters_user_resume_created
    ON ai_cover_letters (user_id, resume_id, created_at DESC);

CREATE INDEX idx_ai_cover_letters_application
    ON ai_cover_letters (application_id);

-- ============================================================
-- Notes for the next reader
-- ============================================================
-- * Sequences (PG CREATE SEQUENCE) are gone: MyBatis-Plus ASSIGN_ID
--   (snowflake) generates IDs in the application layer.
-- * update_time has no ON UPDATE CURRENT_TIMESTAMP and no PG-style
--   trigger: every write path that must refresh it sets update_time = NOW()
--   explicitly in the mapper (known difference #8).
-- * PG's ALTER SYSTEM idle-timeout settings have no MySQL equivalent and
--   were not replaced (MySQL manages this via wait_timeout on the server).
-- ============================================================
