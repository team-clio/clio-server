DO $$
BEGIN
    IF to_regclass('public.project_sources') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE project_sources
        DROP CONSTRAINT IF EXISTS project_sources_sync_status_check;

    ALTER TABLE project_sources
        ADD CONSTRAINT project_sources_sync_status_check
        CHECK (sync_status IN ('PENDING', 'SYNCING', 'SYNCED', 'FAILED', 'UNKNOWN', 'OUT_OF_SYNC'));
END
$$;
