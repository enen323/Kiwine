DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'interview_record'
          AND indexname = 'uk_user_active_interview'
    ) THEN
        CREATE UNIQUE INDEX uk_user_active_interview
            ON interview_record (user_id)
            WHERE deleted = false
              AND status IN ('CREATED', 'IN_PROGRESS');
    END IF;
END $$;
