BEGIN;

ALTER TABLE trip_messages
    ADD COLUMN IF NOT EXISTS client_message_id UUID;

UPDATE trip_messages
SET client_message_id = md5('goride-trip-message:' || id::text)::uuid
WHERE client_message_id IS NULL;

ALTER TABLE trip_messages
    ALTER COLUMN client_message_id SET NOT NULL;

ALTER TABLE trip_messages
    DROP CONSTRAINT IF EXISTS uk_trip_messages_trip_sender_client;

ALTER TABLE trip_messages
    ADD CONSTRAINT uk_trip_messages_trip_sender_client
        UNIQUE (trip_id, sender_id, client_message_id);

CREATE TABLE IF NOT EXISTS trip_message_read_states (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL,
    read_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_trip_message_read_states_trip_user UNIQUE (trip_id, user_id),
    CONSTRAINT fk_trip_message_read_states_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_trip_message_read_states_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_trip_message_read_states_message FOREIGN KEY (last_read_message_id) REFERENCES trip_messages(id)
);

CREATE INDEX IF NOT EXISTS idx_trip_message_read_states_user_trip
    ON trip_message_read_states (user_id, trip_id);

COMMIT;
