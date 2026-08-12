# GoRide Current Phase

> Last updated: 2026-08-11, Asia/Bangkok
>
> Purpose: source of truth before reviewing the current backend feature.

---

## 1. Repository Status

- Current branch: `codex/trip-messaging-production`.
- Base: local `develop` after `merge: route eta matching ranking`.
- The environment-specific `application.yml` override and `docs/images/` are restored outside staging, with the stash retained as backup. The staged YAML uses environment-variable defaults and contains no Supabase credential.
- Working direction: complete reliable passenger-driver messaging before FE integration testing.

---

## 2. Current Scope

Planned commit: `feat: complete reliable trip messaging`.

Implemented scope:
- Require FE-generated UUID `clientMessageId` and enforce unique `(trip_id, sender_id, client_message_id)` delivery.
- Return the original persisted message on retries without rebroadcasting or sending duplicate push notifications.
- Add cursor sync for initial history, older pagination and reconnect catch-up.
- Add per-participant read cursor and unread count excluding the user's own messages.
- Broadcast read state and persisted messages only after database commit.
- Return STOMP ACKs on `/user/queue/trip-message-acks` and business errors on `/user/queue/trip-message-errors`.
- Send FCM message notifications through the existing Firebase channel when enabled.
- Apply a per-user chat rate limit shared through the configured rate-limit store.
- Reuse the configured CORS allowlist for SockJS/native WebSocket origins instead of wildcard origins.
- Add manual SQL release `20260811-trip-messaging-reliability` with FK/unique/index verification and rollback.

---

## 3. Validation

- Targeted chat/security suite: pass 26 tests.
- Docker-backed REST + PostgreSQL + Redis flow: pass.
- SQL release apply/verify/rollback against PostGIS: pass.
- SQL release static validator: pass.
- Full Maven suite: pass 616 tests with PostgreSQL/PostGIS and Redis Testcontainers.
- `git diff --check`: pass; internal diff review complete.
- CodeRabbit CLI is unavailable in the local PATH; user review remains pending.

---

## 4. FE Contract

- Canonical send path: REST `POST /api/v1/trips/{tripId}/messages` with stable `clientMessageId`.
- Realtime message topic: `/topic/trip/{tripId}/messages`.
- Cursor sync: `GET /api/v1/trips/{tripId}/messages/sync` with `beforeId` or `afterId`.
- Read state: `PUT /api/v1/trips/{tripId}/messages/read-state`.
- Unread count: `GET /api/v1/trips/{tripId}/messages/unread-count`.
- Read topic: `/topic/trip/{tripId}/message-read`.
- STOMP send remains available at `/app/trip.message` with user ACK/error queues.

---

## 5. Deployment Boundary

- Current Spring Simple Broker is appropriate for local/staging and one WebSocket backend replica.
- Before horizontally scaling WebSocket replicas, add a managed STOMP broker relay or equivalent shared fan-out.
- REST cursor sync remains the recovery path when realtime delivery is interrupted.
