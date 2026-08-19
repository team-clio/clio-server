DO $$
BEGIN
    IF to_regclass('public.project_contexts') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE project_contexts ADD COLUMN IF NOT EXISTS original_filename VARCHAR(500);
    ALTER TABLE project_contexts ADD COLUMN IF NOT EXISTS media_type VARCHAR(100);
    ALTER TABLE project_contexts ADD COLUMN IF NOT EXISTS content_hash VARCHAR(71);
    ALTER TABLE project_contexts ADD COLUMN IF NOT EXISTS sync_status VARCHAR(20);

    UPDATE project_contexts
    SET original_filename = COALESCE(original_filename, 'legacy-' || id || '.md'),
        media_type = COALESCE(media_type, 'text/markdown'),
        content_hash = COALESCE(content_hash, 'legacy:' || id),
        sync_status = COALESCE(sync_status, 'SYNCED');

    ALTER TABLE project_contexts ALTER COLUMN original_filename SET NOT NULL;
    ALTER TABLE project_contexts ALTER COLUMN media_type SET NOT NULL;
    ALTER TABLE project_contexts ALTER COLUMN content_hash SET NOT NULL;
    ALTER TABLE project_contexts ALTER COLUMN sync_status SET NOT NULL;

    CREATE UNIQUE INDEX IF NOT EXISTS uk_project_context_project_content_hash
        ON project_contexts (project_id, content_hash);
END
$$;
