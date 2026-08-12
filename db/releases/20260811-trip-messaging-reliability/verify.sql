SELECT
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'trip_messages'
  AND column_name = 'client_message_id';

SELECT
    constraint_name,
    constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'public'
  AND table_name IN ('trip_messages', 'trip_message_read_states')
  AND constraint_type IN ('UNIQUE', 'FOREIGN KEY')
ORDER BY table_name, constraint_type, constraint_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'trip_message_read_states'
ORDER BY indexname;

SELECT count(*) AS messages_without_client_id
FROM trip_messages
WHERE client_message_id IS NULL;
