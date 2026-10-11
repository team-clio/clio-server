DO $$
BEGIN
    IF to_regclass('public.bugs') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE bugs
        DROP CONSTRAINT IF EXISTS bugs_status_check;

    ALTER TABLE bugs
        ADD CONSTRAINT bugs_status_check
        CHECK (status IN ('NEW', 'ANALYZING', 'FAILED', 'NEEDS_REVIEW', 'TRIAGED', 'RESOLVED', 'IGNORED'));
END
$$;
