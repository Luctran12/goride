package com.example.goride.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TripMessagingReleaseIntegrationTests extends PostgresRedisIntegrationTest {
    private static final Path RELEASE = Path.of(
            "db",
            "releases",
            "20260811-trip-messaging-reliability"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesVerifiesAndRollsBackTripMessagingRelease() throws IOException {
        execute("rollback.sql");
        try {
            execute("precheck.sql");
            execute("apply.sql");
            execute("verify.sql");

            assertThat(regclass("public.trip_message_read_states")).isNotNull();
            assertThat(columnExists("trip_messages", "client_message_id")).isTrue();
            assertThat(constraintExists("uk_trip_messages_trip_sender_client")).isTrue();
            assertThat(constraintExists("uk_trip_message_read_states_trip_user")).isTrue();
            assertThat(constraintExists("fk_trip_message_read_states_trip")).isTrue();
            assertThat(constraintExists("fk_trip_message_read_states_user")).isTrue();
            assertThat(constraintExists("fk_trip_message_read_states_message")).isTrue();
        } finally {
            execute("rollback.sql");
        }

        assertThat(regclass("public.trip_message_read_states")).isNull();
        assertThat(columnExists("trip_messages", "client_message_id")).isFalse();
    }

    private void execute(String file) throws IOException {
        jdbcTemplate.execute(Files.readString(RELEASE.resolve(file)));
    }

    private String regclass(String name) {
        return jdbcTemplate.queryForObject("SELECT to_regclass(?)::TEXT", String.class, name);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                table,
                column
        );
        return count != null && count == 1;
    }

    private boolean constraintExists(String constraint) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND constraint_name = ?
                """,
                Integer.class,
                constraint
        );
        return count != null && count == 1;
    }
}
