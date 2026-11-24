-- ============================================================
-- MianBa AI Interview Platform - Phase 1 MVP
-- Database: PostgreSQL 16+
-- Init Script - Creates all Phase 1 tables, indexes, and comments
-- ============================================================

-- ============================================================
-- Database creation (execute as superuser if needed)
-- ============================================================
-- CREATE DATABASE mianba WITH ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8';

-- ============================================================
-- Schema
-- ============================================================
CREATE SCHEMA IF NOT EXISTS interview;

-- ============================================================
-- Sequences for snowflake/assign-id compatibility
-- ============================================================
CREATE SEQUENCE IF NOT EXISTS interview.seq_user_account START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_resume START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_interview_record START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_interview_message START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_user_quota START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_payment_record START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_interview_question_score START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_question START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_tag START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_question_tag START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_user_favorite START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_daily_challenge START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS interview.seq_user_practice_record START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- ============================================================
-- 1. user_account — User accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.user_account (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    phone           VARCHAR(20)     NOT NULL,
    password        VARCHAR(256)    NOT NULL,
    nickname        VARCHAR(50),
    avatar_url      VARCHAR(512),
    fingerprint     VARCHAR(255),
    target_position VARCHAR(100),
    target_company  VARCHAR(50),
    status          SMALLINT        NOT NULL DEFAULT 1,
    last_login_time TIMESTAMP,
    ai_settings     JSONB,

    -- Common fields
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0
);

COMMENT ON TABLE  interview.user_account IS 'User accounts — phone login, profile, and career target';
COMMENT ON COLUMN interview.user_account.phone IS 'Mobile phone number, used as login identifier';
COMMENT ON COLUMN interview.user_account.password IS 'Bcrypt-hashed password';
COMMENT ON COLUMN interview.user_account.nickname IS 'Display nickname';
COMMENT ON COLUMN interview.user_account.avatar_url IS 'Avatar image URL or object storage key';
COMMENT ON COLUMN interview.user_account.target_position IS 'Target interview position, e.g. Java Backend';
COMMENT ON COLUMN interview.user_account.target_company IS 'Target company, e.g. Alibaba, Tencent';
COMMENT ON COLUMN interview.user_account.status IS 'Account status: 1=active, 0=locked, -1=banned';
COMMENT ON COLUMN interview.user_account.last_login_time IS 'Timestamp of the most recent successful login';
COMMENT ON COLUMN interview.user_account.create_time IS 'Record creation timestamp';
COMMENT ON COLUMN interview.user_account.update_time IS 'Last update timestamp';
COMMENT ON COLUMN interview.user_account.deleted IS 'Soft-delete flag: FALSE=active, TRUE=deleted';
COMMENT ON COLUMN interview.user_account.version IS 'Optimistic locking version counter';

-- ============================================================
-- 2. user_quota — User interview and resume-chat quotas
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.user_quota (
    user_id               BIGINT          PRIMARY KEY,
    interview_free_used   INT            NOT NULL DEFAULT 0,
    resume_chat_free_used INT            NOT NULL DEFAULT 0,
    interview_paid        INT            NOT NULL DEFAULT 0,
    resume_chat_paid      INT            NOT NULL DEFAULT 0,
    create_time           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN        NOT NULL DEFAULT FALSE,
    version               INT            NOT NULL DEFAULT 1
);

COMMENT ON TABLE  interview.user_quota IS 'User quota counters — tracks free and paid usage for interviews and resume chat';
COMMENT ON COLUMN interview.user_quota.user_id IS 'Owner user ID, FK to user_account';
COMMENT ON COLUMN interview.user_quota.interview_free_used IS 'Number of free interview sessions consumed';
COMMENT ON COLUMN interview.user_quota.resume_chat_free_used IS 'Number of free resume-chat sessions consumed';
COMMENT ON COLUMN interview.user_quota.interview_paid IS 'Number of paid interview sessions available';
COMMENT ON COLUMN interview.user_quota.resume_chat_paid IS 'Number of paid resume-chat sessions available';

-- ============================================================
-- 3. resume — User resumes
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.resume (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    user_id         BIGINT          NOT NULL,
    file_name       VARCHAR(255),
    file_url        VARCHAR(512),
    content_text    TEXT,
    content_json    JSONB,
    is_active       BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Common fields
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    -- Foreign key
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES interview.user_account(id)
);

COMMENT ON TABLE  interview.resume IS 'User resumes — uploaded files and parsed content';
COMMENT ON COLUMN interview.resume.user_id IS 'Owner user ID, FK to user_account';
COMMENT ON COLUMN interview.resume.file_name IS 'Original uploaded file name';
COMMENT ON COLUMN interview.resume.file_url IS 'File storage URL or object storage key';
COMMENT ON COLUMN interview.resume.content_text IS 'Full text extracted from the resume file (OCR/parsed)';
COMMENT ON COLUMN interview.resume.content_json IS 'Structured resume data in JSONB format';
COMMENT ON COLUMN interview.resume.is_active IS 'Active resume flag: only one resume per user should be active at a time';

-- Phase 1 additions: resume builder columns
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS title          VARCHAR(255);
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS template       VARCHAR(64)  NOT NULL DEFAULT 'classic';
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS language       VARCHAR(8)   NOT NULL DEFAULT 'zh';
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS theme_config   JSONB;
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS is_public      BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS share_token    VARCHAR(64);
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS share_password VARCHAR(255);
ALTER TABLE interview.resume ADD COLUMN IF NOT EXISTS view_count     INTEGER      NOT NULL DEFAULT 0;

COMMENT ON COLUMN interview.resume.title IS 'Resume display title';
COMMENT ON COLUMN interview.resume.template IS 'Template name, e.g. classic, modern';
COMMENT ON COLUMN interview.resume.language IS 'Resume language, e.g. zh, en';
COMMENT ON COLUMN interview.resume.theme_config IS 'JSON: color/font/spacing/margin config';
COMMENT ON COLUMN interview.resume.is_public IS 'Public share flag';
COMMENT ON COLUMN interview.resume.share_token IS 'Unique share token for public access';
COMMENT ON COLUMN interview.resume.share_password IS 'BCrypt-hashed share password';
COMMENT ON COLUMN interview.resume.view_count IS 'Public view counter';

-- ============================================================
-- 2b. resume_section — Resume structured sections
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.resume_section (
    id              BIGINT          PRIMARY KEY,

    resume_id       BIGINT          NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    title           VARCHAR(255),
    sort_order      INT,
    visible         BOOLEAN         NOT NULL DEFAULT TRUE,
    content         JSONB,

    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT fk_resume_section_resume FOREIGN KEY (resume_id) REFERENCES interview.resume(id)
);

CREATE INDEX IF NOT EXISTS idx_resume_section_rid ON interview.resume_section(resume_id);
COMMENT ON TABLE  interview.resume_section IS 'Resume sections — structured blocks (personalInfo, education, etc.)';
COMMENT ON COLUMN interview.resume_section.type IS 'Section type: personalInfo, summary, education, experience, project, skills, certification, language, publication, custom';
COMMENT ON COLUMN interview.resume_section.content IS 'JSON content varying by section type';

-- ============================================================
-- 3. interview_record — Interview sessions
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.interview_record (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    resume_id       BIGINT,
    user_id         BIGINT          NOT NULL,
    company         VARCHAR(50),
    position        VARCHAR(100),
    status          VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    current_stage   VARCHAR(30)     NOT NULL DEFAULT 'SELF_INTRO',
    stage_order     INTEGER         NOT NULL DEFAULT 0,
    total_questions INTEGER         NOT NULL DEFAULT 0,

    -- Scoring (nullable until evaluation completes)
    score_total     INTEGER,
    score_tech      INTEGER,
    score_expression INTEGER,
    score_project   INTEGER,
    score_logic     INTEGER,
    score_adaptability INTEGER,
    suggestion      TEXT,

    -- Timing
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    duration_seconds INTEGER,

    -- Common fields
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    -- Foreign key
    CONSTRAINT fk_interview_record_user FOREIGN KEY (user_id) REFERENCES interview.user_account(id)
);

COMMENT ON TABLE  interview.interview_record IS 'Interview sessions — each row is one complete interview attempt';
COMMENT ON COLUMN interview.interview_record.user_id IS 'Candidate user ID, FK to user_account';
COMMENT ON COLUMN interview.interview_record.company IS 'Target company for this interview session';
COMMENT ON COLUMN interview.interview_record.position IS 'Target position for this interview session';
COMMENT ON COLUMN interview.interview_record.status IS 'Session status: CREATED, IN_PROGRESS, COMPLETED, TERMINATED';
COMMENT ON COLUMN interview.interview_record.current_stage IS 'Current interview stage: SELF_INTRO, PROJECT_DEEP_DIVE, TECH_ASSESSMENT, REVERSE_QA, COMPLETED';
COMMENT ON COLUMN interview.interview_record.stage_order IS 'Order of the current stage within the interview flow (0-based)';
COMMENT ON COLUMN interview.interview_record.total_questions IS 'Total number of questions asked in this session';
COMMENT ON COLUMN interview.interview_record.score_total IS 'Overall score (0-100)';
COMMENT ON COLUMN interview.interview_record.score_tech IS 'Technical foundation score (0-100)';
COMMENT ON COLUMN interview.interview_record.score_expression IS 'Expression and communication score (0-100)';
COMMENT ON COLUMN interview.interview_record.score_project IS 'Project experience score (0-100)';
COMMENT ON COLUMN interview.interview_record.score_logic IS 'Logical thinking score (0-100)';
COMMENT ON COLUMN interview.interview_record.score_adaptability IS 'Adaptability score (0-100)';
COMMENT ON COLUMN interview.interview_record.suggestion IS 'AI-generated improvement suggestion text';
COMMENT ON COLUMN interview.interview_record.started_at IS 'When the interview session started';
COMMENT ON COLUMN interview.interview_record.completed_at IS 'When the interview session completed or was terminated';
COMMENT ON COLUMN interview.interview_record.duration_seconds IS 'Total session duration in seconds';

-- ============================================================
-- 4. interview_message — Chat messages in interviews
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.interview_message (
    -- Primary key (ASSIGN_ID / snowflake)
    id              BIGINT          PRIMARY KEY,

    -- Business fields
    interview_id    BIGINT          NOT NULL,
    role            VARCHAR(10)     NOT NULL,
    content         TEXT            NOT NULL,
    question_type   VARCHAR(30),
    sequence_num    INTEGER         NOT NULL,
    token_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Common fields (minimal — interview messages have different lifecycle needs)
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0,

    -- Foreign key
    CONSTRAINT fk_interview_message_record FOREIGN KEY (interview_id) REFERENCES interview.interview_record(id)
);

COMMENT ON TABLE  interview.interview_message IS 'Chat messages within an interview session — questions and answers';
COMMENT ON COLUMN interview.interview_message.interview_id IS 'Parent interview session ID, FK to interview_record';
COMMENT ON COLUMN interview.interview_message.role IS 'Message sender: AI or USER';
COMMENT ON COLUMN interview.interview_message.content IS 'Message content text (markdown supported for AI messages)';
COMMENT ON COLUMN interview.interview_message.question_type IS 'Question category: SELF_INTRO, TECH_PROJECT, TECH_BASIC, ALGORITHM, SYSTEM_DESIGN, BEHAVIORAL, REVERSE_QA';
COMMENT ON COLUMN interview.interview_message.sequence_num IS 'Sequential message number within the interview (0-based)';
COMMENT ON COLUMN interview.interview_message.token_count IS 'Number of tokens in this message (for AI usage tracking)';

-- ============================================================
-- 5. payment_record — WeChat Pay payment transactions
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.payment_record (
    id               BIGINT          PRIMARY KEY,
    user_id          BIGINT          NOT NULL,
    amount           DECIMAL(10,2)   NOT NULL,
    service_type     VARCHAR(20)     NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    wechat_trade_no  VARCHAR(64),
    create_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    version          INT             NOT NULL DEFAULT 1
);

COMMENT ON TABLE  interview.payment_record IS 'WeChat Pay payment records — tracks quota purchase transactions';
COMMENT ON COLUMN interview.payment_record.user_id IS 'Payer user ID, FK to user_account';
COMMENT ON COLUMN interview.payment_record.amount IS 'Payment amount in CNY';
COMMENT ON COLUMN interview.payment_record.service_type IS 'Service type: INTERVIEW or RESUME_CHAT';
COMMENT ON COLUMN interview.payment_record.status IS 'Payment status: PENDING, SUCCESS, FAILED, REFUNDED';
COMMENT ON COLUMN interview.payment_record.wechat_trade_no IS 'WeChat Pay transaction number';

-- ============================================================
-- 6. interview_question_score — Per-question scoring in interviews
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.interview_question_score (
    id                  BIGINT          PRIMARY KEY,
    interview_id        BIGINT          NOT NULL,
    message_id          BIGINT          NOT NULL,
    question_type       VARCHAR(32)     NOT NULL,
    question_description TEXT          NOT NULL,
    score               INTEGER         NOT NULL,
    feedback            TEXT,
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INTEGER         NOT NULL DEFAULT 1
);

COMMENT ON TABLE  interview.interview_question_score IS 'Per-question evaluation scores within an interview session';
COMMENT ON COLUMN interview.interview_question_score.interview_id IS 'Parent interview session ID, FK to interview_record';
COMMENT ON COLUMN interview.interview_question_score.message_id IS 'Parent message ID, FK to interview_message';
COMMENT ON COLUMN interview.interview_question_score.question_type IS 'Question category: SELF_INTRO, TECH_PROJECT, TECH_BASIC, ALGORITHM, SYSTEM_DESIGN, BEHAVIORAL, REVERSE_QA';
COMMENT ON COLUMN interview.interview_question_score.question_description IS 'The question text that was scored';
COMMENT ON COLUMN interview.interview_question_score.score IS 'Score for this question (0-100)';
COMMENT ON COLUMN interview.interview_question_score.feedback IS 'AI-generated feedback for this specific answer';

-- ============================================================
-- 7. question — Question bank
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.question (
    id              BIGINT          PRIMARY KEY,
    title           VARCHAR(200)    NOT NULL,
    content         TEXT            NOT NULL,
    answer          JSONB           NOT NULL DEFAULT '{}'::jsonb,
    type            VARCHAR(20)     NOT NULL DEFAULT 'SCENARIO',
    difficulty      VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
    easy_mistake    VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    interview_count INTEGER         NOT NULL DEFAULT 0,
    views           INTEGER         NOT NULL DEFAULT 0,
    favorites       INTEGER         NOT NULL DEFAULT 0,
    search_vector   TSVECTOR,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0
);

COMMENT ON TABLE  interview.question IS 'Question bank — covers scenarios, algorithms, system design, behavioral, and language questions';
COMMENT ON COLUMN interview.question.title IS 'Question title/summary';
COMMENT ON COLUMN interview.question.content IS 'Full question description';
COMMENT ON COLUMN interview.question.answer IS 'JSON: { summary, analysis, code }';
COMMENT ON COLUMN interview.question.type IS 'SCENARIO / ALGORITHM / SYSTEM_DESIGN / BEHAVIORAL / LANGUAGE';
COMMENT ON COLUMN interview.question.difficulty IS 'EASY / MEDIUM / HARD';
COMMENT ON COLUMN interview.question.easy_mistake IS 'NORMAL / EASY_MISTAKE / HIGH_MISTAKE';
COMMENT ON COLUMN interview.question.interview_count IS 'Number of times asked in real interviews';
COMMENT ON COLUMN interview.question.views IS 'View count';
COMMENT ON COLUMN interview.question.favorites IS 'Favorite count';
COMMENT ON COLUMN interview.question.search_vector IS 'Full-text search vector (auto-maintained by trigger)';

-- ============================================================
-- 8. tag — Predefined tags for questions
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.tag (
    id          BIGINT          PRIMARY KEY,
    name        VARCHAR(50)     NOT NULL UNIQUE,
    description VARCHAR(200),
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    version     INTEGER         NOT NULL DEFAULT 0
);

COMMENT ON TABLE  interview.tag IS 'Predefined tags for categorizing questions';
COMMENT ON COLUMN interview.tag.name IS 'Tag display name (unique)';
COMMENT ON COLUMN interview.tag.description IS 'Tag description';

-- ============================================================
-- 9. question_tag — Question-to-tag mapping (many-to-many)
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.question_tag (
    question_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (question_id, tag_id)
);

COMMENT ON TABLE  interview.question_tag IS 'Question-to-tag many-to-many mapping';

-- ============================================================
-- 10. user_favorite — User favorite/bookmark questions
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.user_favorite (
    id          BIGINT          PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    question_id BIGINT          NOT NULL,
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    version     INTEGER         NOT NULL DEFAULT 0,
    UNIQUE (user_id, question_id)
);

COMMENT ON TABLE  interview.user_favorite IS 'User favorite/bookmark questions';
COMMENT ON COLUMN interview.user_favorite.user_id IS 'Owner user ID';
COMMENT ON COLUMN interview.user_favorite.question_id IS 'Favorited question ID';

-- ============================================================
-- 11. daily_challenge — Daily challenge record per user
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.daily_challenge (
    id            BIGINT      PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    question_id   BIGINT      NOT NULL,
    challenge_date DATE       NOT NULL,
    completed     BOOLEAN     NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    version       INTEGER     NOT NULL DEFAULT 0,
    UNIQUE (user_id, challenge_date)
);

COMMENT ON TABLE  interview.daily_challenge IS 'Daily challenge record — one random question per user per day';
COMMENT ON COLUMN interview.daily_challenge.user_id IS 'Owner user ID';
COMMENT ON COLUMN interview.daily_challenge.question_id IS 'Assigned question ID';
COMMENT ON COLUMN interview.daily_challenge.challenge_date IS 'Challenge date (UTC or server local date)';
COMMENT ON COLUMN interview.daily_challenge.completed IS 'Whether user has viewed/answered the daily question';

-- ============================================================
-- 12. user_practice_record — User practice history (for recommendations)
-- ============================================================
CREATE TABLE IF NOT EXISTS interview.user_practice_record (
    id              BIGINT          PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    question_id     BIGINT          NOT NULL,
    source_type     VARCHAR(32),
    source_ref_id   BIGINT,
    practiced_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    version         INTEGER         NOT NULL DEFAULT 0
);

COMMENT ON TABLE  interview.user_practice_record IS 'User practice history — tracks which questions a user has practiced, used for recommendation deduplication';
COMMENT ON COLUMN interview.user_practice_record.user_id IS 'Owner user ID';
COMMENT ON COLUMN interview.user_practice_record.question_id IS 'Practiced question ID';
COMMENT ON COLUMN interview.user_practice_record.source_type IS 'Practice source: INTERVIEW, RECOMMENDATION, DAILY, MANUAL';
COMMENT ON COLUMN interview.user_practice_record.source_ref_id IS 'Source reference ID (interviewId, recommendationId, etc.)';
COMMENT ON COLUMN interview.user_practice_record.practiced_at IS 'When the question was practiced';

-- ============================================================
-- Unique constraints
-- ============================================================
ALTER TABLE interview.user_account
    ADD CONSTRAINT uk_user_account_phone UNIQUE (phone);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_account_fingerprint ON interview.user_account(fingerprint) WHERE fingerprint IS NOT NULL;

-- ============================================================
-- Indexes
-- ============================================================

-- user_account indexes
CREATE INDEX idx_user_account_status        ON interview.user_account(status);
CREATE INDEX idx_user_account_create_time   ON interview.user_account(create_time);

-- resume indexes
CREATE INDEX idx_resume_user_id    ON interview.resume(user_id);
CREATE INDEX idx_resume_is_active  ON interview.resume(is_active);
CREATE INDEX idx_resume_user_active ON interview.resume(user_id, is_active);

-- interview_record indexes
CREATE INDEX idx_interview_record_user_id    ON interview.interview_record(user_id);
CREATE INDEX idx_interview_record_company    ON interview.interview_record(company);
CREATE INDEX idx_interview_record_status     ON interview.interview_record(status);
CREATE INDEX idx_interview_record_user_status ON interview.interview_record(user_id, status);
CREATE INDEX idx_interview_record_create_time ON interview.interview_record(create_time);

-- interview_message indexes
CREATE INDEX idx_interview_message_interview_id  ON interview.interview_message(interview_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_interview_message_seq ON interview.interview_message(interview_id, sequence_num);
CREATE INDEX idx_interview_message_created_at    ON interview.interview_message(created_at);

-- interview_question_score indexes
CREATE INDEX idx_iqs_interview_id ON interview.interview_question_score(interview_id);
CREATE INDEX idx_iqs_question_type ON interview.interview_question_score(question_type);

-- payment_record indexes
CREATE INDEX idx_payment_record_user_id ON interview.payment_record(user_id);
CREATE INDEX idx_payment_record_status ON interview.payment_record(status);

-- question indexes
CREATE INDEX idx_question_search
    ON interview.question USING GIN(search_vector);
CREATE INDEX idx_question_heat
    ON interview.question (interview_count DESC, favorites DESC, views DESC);
CREATE INDEX idx_question_difficulty
    ON interview.question (difficulty);
CREATE INDEX idx_question_type
    ON interview.question (type);

-- tag indexes
CREATE INDEX idx_tag_name ON interview.tag(name);

-- user_favorite indexes
CREATE INDEX idx_user_favorite_user_id ON interview.user_favorite(user_id);
CREATE INDEX idx_user_favorite_question_id ON interview.user_favorite(question_id);

-- daily_challenge indexes
CREATE INDEX idx_daily_challenge_user_date
    ON interview.daily_challenge(user_id, challenge_date);

-- user_practice_record indexes
CREATE INDEX idx_user_practice_user_question
    ON interview.user_practice_record(user_id, question_id);
CREATE INDEX idx_user_practice_user_practiced
    ON interview.user_practice_record(user_id, practiced_at);

-- ============================================================
-- Trigger: auto-update update_time on row modification
-- ============================================================
CREATE OR REPLACE FUNCTION interview.set_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY[
            'user_account',
            'user_quota',
            'resume',
            'resume_section',
            'interview_record',
            'interview_message',
            'interview_question_score',
            'payment_record',
            'question',
            'tag',
            'question_tag',
            'user_favorite',
            'daily_challenge',
            'user_practice_record'
        ])
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS trg_%s_set_update_time ON interview.%I',
            tbl, tbl
        );
        EXECUTE format(
            'CREATE TRIGGER trg_%s_set_update_time
             BEFORE UPDATE ON interview.%I
             FOR EACH ROW
             EXECUTE FUNCTION interview.set_update_time()',
            tbl, tbl
        );
    END LOOP;
END;
$$;

-- ============================================================
-- Trigger: auto-update search_vector for question full-text search
-- ============================================================
CREATE OR REPLACE FUNCTION interview.set_question_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(NEW.content, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_question_search_vector ON interview.question;
CREATE TRIGGER trg_question_search_vector
    BEFORE INSERT OR UPDATE OF title, content ON interview.question
    FOR EACH ROW EXECUTE FUNCTION interview.set_question_search_vector();

-- ============================================================
-- Sequence defaults for tables using ASSIGN_ID
-- (Keys are assigned by application layer; sequences exist
--  only for reference / fallback ordering.)
-- ============================================================
-- Note: The ASSIGN_ID (snowflake) strategy generates IDs
-- at the application layer. The sequences above are provided
-- for compatibility and administrative use only.

-- ============================================================
-- Connection idle timeouts — prevent zombie connections
-- ============================================================
-- idle_session_timeout: pure idle connections auto-disconnect after 10 min
-- idle_in_transaction_session_timeout: idle-in-transaction auto-rollback after 60 sec
-- Default is 0 (disabled). Without these, connections from killed JVMs
-- linger until TCP keepalive timeout (~2 hours), eventually exhausting
-- max_connections (default 100) and causing "too many clients already".
--
-- Note: These settings apply to the entire PostgreSQL instance.
-- For existing environments, run manually:
--   ALTER SYSTEM SET idle_session_timeout = '600000';
--   ALTER SYSTEM SET idle_in_transaction_session_timeout = '60000';
--   SELECT pg_reload_conf();
ALTER SYSTEM SET idle_session_timeout = '600000';
ALTER SYSTEM SET idle_in_transaction_session_timeout = '60000';
