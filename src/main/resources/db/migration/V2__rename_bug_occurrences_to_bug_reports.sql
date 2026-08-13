DO $$
BEGIN
    IF to_regclass('public.bug_occurrences') IS NOT NULL
       AND to_regclass('public.bug_reports') IS NOT NULL THEN
        RAISE EXCEPTION 'Both bug_occurrences and bug_reports exist; manual reconciliation is required';
    ELSIF to_regclass('public.bug_occurrences') IS NOT NULL THEN
        ALTER TABLE bug_occurrences RENAME TO bug_reports;
    END IF;

    IF to_regclass('public.bug_reports') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_bug_reports_project_occurred
            ON bug_reports (project_id, occurred_at);
        CREATE INDEX IF NOT EXISTS idx_bug_reports_bug_occurred
            ON bug_reports (bug_id, occurred_at);
    END IF;
END
$$;
