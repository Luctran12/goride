# GoRide Current Phase

> Last updated: 2026-07-28, Asia/Bangkok
>
> Purpose: source of truth before starting or reviewing the next backend commit.

---

## 1. Repository Status

- Current branch: `codex/word-location-mobile`.
- Base develop commit: `5b941d6` (`merge: driver location bootstrap`).
- Local-only config: `src/main/resources/application.yml` has environment-specific changes and must remain uncommitted.
- Working direction: expose the custom three-word location service through authenticated GoRide APIs for passenger and driver mobile flows.

---

## 2. Active Work Ready For Review

Planned commit: `feat: integrate three-word mobile location lookup`.

Scope implemented:
- Add a configurable HTTP adapter for the existing Python `/api/to-words` and `/api/to-coordinate` APIs.
- Expose GoRide endpoints under `/api/v1/locations` with the common response/error envelope.
- Keep `lat`/`lng` as the source of truth; translate GoRide `lng` to the Python provider's `lon` parameter.
- Normalize three-word input before provider lookup, return provider `_` compounds as spaces for mobile display, and validate coordinates at the GoRide boundary.
- Map malformed input, unknown addresses, unsupported map bounds, provider timeout, and provider failure to stable error codes.
- Add unit/provider/controller coverage and a mobile integration contract.

---

## 3. Mobile Contract

- Passenger selects a map point, then taps `Lay 3 tu`; the app calls `GET /api/v1/locations/to-words?lat={lat}&lng={lng}`.
- Driver taps `Tim bang 3 tu`, enters a dot-separated address, then the app calls `GET /api/v1/locations/to-coordinate?address={address}`.
- Driver lookup first shows a preview marker and cell bounds. It must not replace an active trip destination until the driver explicitly confirms.
- Both endpoints require a valid passenger or driver JWT.
- Booking requests continue to send their existing `lat`, `lng`, and street `address` fields. The three-word address is display/share metadata and is not booking source of truth.

---

## 4. Validation

- Targeted location/security suite: pass 32 tests.
- Full `./mvnw.cmd test`: 495 tests discovered, 485 passed, 0 failures, and 10 integration errors during Docker/Testcontainers initialization.
- Docker root cause: Testcontainers cannot access `\\.\pipe\docker_engine`; feature and non-Docker tests have no assertion failure.
- `git diff --check`: pass; Git reports Windows CRLF conversion warnings only.
- Manual review: found and fixed default exposure, mismatched provider address, inverted bounds, and out-of-cell coordinate issues; regression tests cover these cases.
- The repository contains backend only, so mobile screens are documented as an implementation-ready React Native contract rather than edited in this branch.
