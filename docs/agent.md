# Agent Rules

## Before Coding

- Read `docs/TDD.md` to understand the original system design and API contracts.
- Read `docs/backend-implementation.md` to understand the current front-end implementation plan.
- Read `docs/current-phase.md` to understand the active feature, phase, branch, and next expected work.
- Read `docs/implementation-log.md` to understand what has already been implemented and reviewed.
- Read `docs/changes-in-implementation.md` if it exists to understand approved deviations from the TDD.
- If `docs/current-phase.md` is missing or empty, create or fill it before coding with the active feature, current phase, branch, planned commit scope, and next checkpoint.
- Check `git status --short` before making changes and do not overwrite user changes.


## Branching Strategy

- Always start each feature from `main`.
- Create one branch per feature using a clear feature name, for example `codex/map-location-picker`.
- Do not mix unrelated features in one branch.
- Keep each branch focused enough that it can be reviewed independently.

## Commit Strategy

- Split each feature into multiple small commits.
- Each commit should contain one reviewable unit of work, such as shared types, API client, one screen refactor, or one bug fix.
- Do not put too much code in a single commit.
- Run the relevant validation before committing when feasible.
- Do not continue to the next commit until the current commit has been reviewed and documented.

## After Each Commit

- Run the review skill or available review workflow for the committed changes.
- If no review skill is available, perform a manual code-review pass focused on bugs, regressions, missing tests, API mismatches, and edge cases.
- Write a detailed implementation entry in `docs/implementation-log.md`.
- The implementation log entry must include branch name, commit hash, files changed, behavior implemented, validation run, review findings, and known risks.
- After writing `docs/implementation-log.md`, update `docs/current-phase.md` with the completed commit, current phase status, next phase or next commit scope, and whether user review is required.
- Wait for user review before starting the next commit.

## Phase Tracking

- Use `docs/current-phase.md` as the source of truth for which feature is active and which implementation phase is in progress.
- Keep `docs/current-phase.md` aligned with `docs/backend-implementation.md`; if the phase changes, update it before starting new code.
- Do not start a new feature branch or commit scope until `docs/current-phase.md` clearly states the next intended work.
- When user feedback changes the plan, update `docs/current-phase.md` and record any TDD deviation in `docs/changes-in-implementation.md` when applicable.

## Handling Design Deviations

- If implementation differs from `docs/TDD.md`, document the difference in `docs/changes-in-implementation.md`.
- Each deviation entry must include the date, branch, affected feature, TDD expectation, implemented behavior, reason for the change, and impact.
- Do not silently change API contracts, route behavior, state names, or UX flow without recording the deviation.
- If the deviation has product or backend implications, pause and ask the user before committing it.

## Review Gate

- A feature is not considered complete until all commits have review notes in `docs/implementation-log.md`.
- Any requested user changes after review must be implemented on the same feature branch in a new small commit.
- After updating requested changes, run another review pass and append the result to `docs/implementation-log.md`.
- Only move to the next feature branch after the user approves the current feature.
