BEGIN;

CREATE TABLE IF NOT EXISTS trip_messages (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    sender_id BIGINT NOT NULL REFERENCES users(id),
    sender_role VARCHAR(20) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_trip_messages_sender_role CHECK (sender_role IN ('PASSENGER', 'DRIVER')),
    CONSTRAINT chk_trip_messages_body_not_blank CHECK (length(btrim(body)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_trip_messages_trip_sent_at
    ON trip_messages (trip_id, sent_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trip_messages_sender_sent_at
    ON trip_messages (sender_id, sent_at DESC, id DESC);

COMMIT;
