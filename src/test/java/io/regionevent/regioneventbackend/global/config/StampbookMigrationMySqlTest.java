package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class StampbookMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;
    private static final String STATUS_CONSTRAINT_NAME = "ck_stampbook_status";
    private static final String STATUS_TIMESTAMPS_CONSTRAINT_NAME = "ck_stampbook_status_timestamps";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_스탬프북제목은길이100의_NOT_NULL_열로생성된다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);

        Map<String, Object> titleColumn = jdbcTemplate.queryForMap(
            """
            SELECT IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
                AND table_name = 'stampbook'
                AND column_name = 'title'
            """
        );

        assertThat(titleColumn.get("IS_NULLABLE")).isEqualTo("NO");
        assertThat(((Number) titleColumn.get("CHARACTER_MAXIMUM_LENGTH")).intValue())
            .isEqualTo(100);
    }

    @Test
    void MySQL에서_소문자_스탬프북_상태는_두_CHECK_제약에서_거부된다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        try {
            assertLowercaseStatusIsRejected(jdbcTemplate, STATUS_CONSTRAINT_NAME);

            jdbcTemplate.execute("ALTER TABLE stampbook DROP CHECK " + STATUS_CONSTRAINT_NAME);

            assertLowercaseStatusIsRejected(jdbcTemplate, STATUS_TIMESTAMPS_CONSTRAINT_NAME);
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private JdbcTemplate createJdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        ));
    }

    private void migrate(JdbcTemplate jdbcTemplate) {
        Flyway.configure()
            .dataSource(jdbcTemplate.getDataSource())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private void assertLowercaseStatusIsRejected(
        JdbcTemplate jdbcTemplate,
        String expectedConstraintName
    ) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO stampbook (
                title,
                region_id,
                reward_coupon_policy_id,
                status,
                published_at,
                ended_at
            ) VALUES ('스탬프북 제목', 1, 1, 'draft', NULL, NULL)
            """
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage()).contains(expectedConstraintName);
        });
    }
}
