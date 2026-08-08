package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class StampbookProgressMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;
    private static final String STATUS_CONSTRAINT_NAME = "ck_stampbook_progress_status";
    private static final String COMPLETED_AT_CONSTRAINT_NAME = "ck_stampbook_progress_status_completed_at";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_진행_상태와_완료_시각_CHECK_제약을_강제한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        try {
            assertProgressIsRejected(
                jdbcTemplate,
                "in_progress",
                null,
                STATUS_CONSTRAINT_NAME
            );
            assertProgressIsRejected(
                jdbcTemplate,
                "IN_PROGRESS",
                "CURRENT_TIMESTAMP(6)",
                COMPLETED_AT_CONSTRAINT_NAME
            );
            assertProgressIsRejected(
                jdbcTemplate,
                "COMPLETED",
                null,
                COMPLETED_AT_CONSTRAINT_NAME
            );
            assertProgressIsRejected(
                jdbcTemplate,
                "ENDED_INCOMPLETE",
                "CURRENT_TIMESTAMP(6)",
                COMPLETED_AT_CONSTRAINT_NAME
            );
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

    private void assertProgressIsRejected(
        JdbcTemplate jdbcTemplate,
        String status,
        String completedAt,
        String expectedConstraintName
    ) {
        String completedAtExpression = completedAt == null ? "NULL" : completedAt;

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO stampbook_progress (
                stampbook_id,
                user_id,
                status,
                completed_at
            ) VALUES (1, NULL, ?, %s)
            """.formatted(completedAtExpression),
            status
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage()).contains(expectedConstraintName);
        });
    }
}
