SELECT
    to_regclass('public.trip_messages') AS trip_messages_table,
    to_regclass('public.trip_message_read_states') AS trip_message_read_states_table;

SELECT count(*) AS existing_message_count
FROM trip_messages;
