-- ============================================================
-- job-service 数据表 DDL（schema: interview）
-- ============================================================

-- 投递记录主表
create table if not exists job_applications (
    id          varchar(64)  primary key,
    user_id     bigint       not null,
    resume_id   varchar(64)  null,
    company     varchar(255) not null,
    position    varchar(255) not null,
    channel     varchar(64)  null,
    status      varchar(32)  not null,
    applied_at  timestamp    not null,
    notes       text         null,
    created_at  timestamp    not null default CURRENT_TIMESTAMP,
    updated_at  timestamp    not null default CURRENT_TIMESTAMP
);

create index if not exists idx_job_applications_user_applied
    on job_applications (user_id, applied_at desc);

create index if not exists idx_job_applications_resume
    on job_applications (resume_id);

-- AI 求职信表
create table if not exists ai_cover_letters (
    id              varchar(64)  primary key,
    user_id         bigint       not null,
    resume_id       varchar(64)  not null,
    application_id  varchar(64)  null,
    company         varchar(255) not null,
    position        varchar(255) not null,
    job_description text         null,
    extra_notes     text         null,
    output_language varchar(64)  not null,
    title           varchar(255) not null,
    body            text         not null,
    created_at      timestamp    not null,
    updated_at      timestamp    not null
);

create index if not exists idx_ai_cover_letters_user_resume_created
    on ai_cover_letters (user_id, resume_id, created_at desc);

create index if not exists idx_ai_cover_letters_application
    on ai_cover_letters (application_id);
