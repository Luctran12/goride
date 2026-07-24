# GoRide Database Release SQL Workflow

This directory stores reviewed database release SQL because this project does
not use Flyway.

Use one release folder per schema/data change:

```text
db/releases/YYYYMMDD-short-name/
  manifest.yml
  precheck.sql
  apply.sql
  verify.sql
  rollback.sql
```

## Rules

- Keep local development on Hibernate `ddl-auto=update` only for fast local
  iteration.
- Never rely on Hibernate `update` for staging or production schema changes.
- Every release folder must include a manifest plus precheck, apply, verify
  and rollback scripts.
- `apply.sql` must be transactional by default with `BEGIN;` and `COMMIT;`.
  If a PostgreSQL operation cannot run inside a transaction, set
  `transactional: false` in `manifest.yml` and document the reason.
- Any destructive operation in `apply.sql` must include
  `-- destructive-reviewed: true` near the statement after explicit review.
- PostGIS setup must be explicit when a release depends on geometry features:
  `CREATE EXTENSION IF NOT EXISTS postgis;`.
- Store generated SQL in Git before deployment. Do not edit SQL directly on a
  server.
- Run `scripts/validate-db-release.ps1` before review.
- For a new `NOT NULL` column on an existing table, include a safe default/backfill in `apply.sql` and align the entity
   `columnDefinition` so local Hibernate `ddl-auto=update` does not generate unsafe DDL.

## Recommended Flow

1. Create a release folder from `0000-template`.
2. Fill `manifest.yml`.
3. Write idempotent `precheck.sql` that confirms expected current state.
4. Write `apply.sql` with ordered DDL/DML.
5. Write `verify.sql` with queries that prove the release is correct.
6. Write `rollback.sql` for the safest reversible path.
7. Run:

```powershell
.\scripts\validate-db-release.ps1 -ReleasePath db\releases\YYYYMMDD-short-name
```

8. Review SQL and deployment order before running it against staging or
   production.
