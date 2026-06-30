# Database Release Process

GoRide does not use Flyway. Local development can still use Hibernate
`ddl-auto=update`, but staging and production schema changes must be reviewed
and versioned as SQL release folders under `db/releases`.

## Release Folder Contract

Each schema/data release uses this layout:

```text
db/releases/YYYYMMDD-short-name/
  manifest.yml
  precheck.sql
  apply.sql
  verify.sql
  rollback.sql
```

Use `db/releases/0000-template` as the starting point.

## File Responsibilities

| File | Purpose |
| --- | --- |
| `manifest.yml` | Human-readable metadata: owner, risk, transaction mode, dependencies, rollout and rollback notes. |
| `precheck.sql` | Read-only checks that confirm the target database is ready for the release. |
| `apply.sql` | The actual reviewed DDL/DML. It should be transactional by default. |
| `verify.sql` | Read-only checks that prove the release succeeded. |
| `rollback.sql` | The safest available reverse operation or a documented no-op when rollback is impossible. |

## Validation

Before review, run:

```powershell
.\scripts\validate-db-release.ps1 -ReleasePath db\releases\YYYYMMDD-short-name
```

To validate every release folder:

```powershell
.\scripts\validate-db-release.ps1 -All
```

The validator checks required files, manifest fields, transactional markers and
unreviewed destructive SQL keywords. It does not connect to a database and does
not execute SQL.

## Deployment Checklist

1. Confirm the app commit and SQL release folder are reviewed together.
2. Back up the target database or confirm a recent restorable backup exists.
3. Run `precheck.sql` against the target environment.
4. Run `apply.sql`.
5. Run `verify.sql` and store the output with deployment notes.
6. Smoke test affected backend APIs.
7. Keep `rollback.sql` ready during the deployment window.

## GoRide-Specific Notes

- PostgreSQL/PostGIS is required for geometry columns such as trip pickup,
  dropoff and location history.
- Redis state is not part of SQL release folders.
- Avoid production `ddl-auto=update`; prefer `validate` or an environment
  setting that does not mutate schema automatically.
- For destructive changes, add `-- destructive-reviewed: true` near the SQL
  statement after explicit review.
- When rollback cannot fully restore data, make `rollback.sql` explicit about
  the limitation and use compensating SQL.
