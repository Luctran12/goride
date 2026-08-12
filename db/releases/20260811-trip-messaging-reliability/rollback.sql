BEGIN;

DROP TABLE IF EXISTS trip_message_read_states;

ALTER TABLE trip_messages
    DROP CONSTRAINT IF EXISTS uk_trip_messages_trip_sender_client;

ALTER TABLE trip_messages
    DROP COLUMN IF EXISTS client_message_id;

COMMIT;
