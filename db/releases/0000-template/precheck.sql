-- Purpose: confirm the database is in the expected state before apply.sql.
-- Replace this template with release-specific checks.

SELECT current_database() AS database_name;

SELECT EXISTS (
    SELECT 1
    FROM pg_extension
    WHERE extname = 'postgis'
) AS postgis_installed;
