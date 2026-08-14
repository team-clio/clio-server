DO $$
BEGIN
    IF to_regclass('public.project_sources') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE project_sources ADD COLUMN IF NOT EXISTS provider VARCHAR(30);
    ALTER TABLE project_sources ADD COLUMN IF NOT EXISTS owner VARCHAR(255);
    ALTER TABLE project_sources ADD COLUMN IF NOT EXISTS name VARCHAR(255);
    ALTER TABLE project_sources ADD COLUMN IF NOT EXISTS include_paths TEXT;
    ALTER TABLE project_sources ADD COLUMN IF NOT EXISTS exclude_paths TEXT;
    ALTER TABLE project_sources ADD COLUMN IF NOT EXISTS enabled BOOLEAN;

    UPDATE project_sources
    SET provider = CASE
            WHEN repo_url ILIKE '%gitlab%' THEN 'GITLAB'
            WHEN repo_url ILIKE '%bitbucket%' THEN 'BITBUCKET'
            ELSE 'GITHUB'
        END,
        owner = COALESCE(NULLIF(split_part(regexp_replace(repo_url, '(.+://|git@)[^/:]+[:/]|\.git$', '', 'g'), '/', 1), ''), 'unknown'),
        name = COALESCE(NULLIF(split_part(regexp_replace(repo_url, '(.+://|git@)[^/:]+[:/]|\.git$', '', 'g'), '/', 2), ''), 'repository'),
        enabled = TRUE
    WHERE provider IS NULL OR owner IS NULL OR name IS NULL OR enabled IS NULL;

    UPDATE project_sources SET sync_status = 'PENDING' WHERE sync_status IN ('UNKNOWN', 'OUT_OF_SYNC');

    ALTER TABLE project_sources ALTER COLUMN provider SET NOT NULL;
    ALTER TABLE project_sources ALTER COLUMN owner SET NOT NULL;
    ALTER TABLE project_sources ALTER COLUMN name SET NOT NULL;
    ALTER TABLE project_sources ALTER COLUMN enabled SET NOT NULL;

    CREATE UNIQUE INDEX IF NOT EXISTS uk_project_sources_project_repo_url
        ON project_sources (project_id, repo_url);
END
$$;
