# GoRide Current Phase

> Last updated: 2026-08-03, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `codex/gps-fare-filtering`.
- Base develop commit: `a687c1d` (`merge: driver routing fallback`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: prevent GPS jitter, stale gaps and implausible jumps from inflating completed-trip actual distance and final fare.

---

## 2. Reviewed Work

Commit: `feat: filter gps distance for actual fare`.

Scope implemented:
- Add configurable actual-fare GPS filter properties with safe defaults.
- Ignore movement below 5 meters as jitter.
- Reject segments whose inferred speed exceeds 55 meters/second.
- Reject non-increasing timestamps and skip distance across tracking gaps longer than 30 seconds.
- Keep the last accepted anchor after jitter/speed/timestamp rejection; rebase after a long gap.
- Fall back to the trip estimated distance when no segment remains valid.
- Preserve the legacy calculation behind an explicit disable switch.
- Log aggregate rejection counts once at fare completion and warn when estimate fallback is required.
- Keep REST/WebSocket tracking contracts and database schema unchanged.

---

## 3. Runtime Configuration

- `ACTUAL_FARE_GPS_FILTER_ENABLED=true`
- `ACTUAL_FARE_GPS_MIN_MOVEMENT_METERS=5`
- `ACTUAL_FARE_GPS_MAX_SPEED_METERS_PER_SECOND=55`
- `ACTUAL_FARE_GPS_MAX_SEGMENT_GAP_SECONDS=30`

These thresholds are backend operational controls. FE continues sending trip location updates using the existing contract and displays the backend final fare without recalculating distance.

---

## 4. Validation

- Targeted fare/tracking/status/Spring context suite: pass 29 tests.
- Covers jitter, teleport spikes, long gaps, duplicate timestamps, all-rejected fallback, disabled-filter compatibility and properties validation.
- Full `./mvnw.cmd test`: 572 tests, 3 baseline failures and 18 Docker/Testcontainers initialization errors; GPS filtering tests have no failures.
- `git diff --check`: pass; Windows CRLF conversion warnings only.
- User and internal reviews completed; CodeRabbit CLI is unavailable in PATH.
- Threshold calibration with real device traces remains a staging UAT task.