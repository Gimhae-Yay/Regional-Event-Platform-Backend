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
class MissionParticipationProgressMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;
    private static final String STATUS_CONSTRAINT_NAME = "ck_mission_participation_status";
    private static final String COMPLETED_AT_CONSTRAINT_NAME = "ck_mission_participation_status_completed_at";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_참여_상태와_완료_시각_CHECK_제약을_강제한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        try {
            assertParticipationIsRejected(
                jdbcTemplate,
                "in_progress",
                null,
                STATUS_CONSTRAINT_NAME
            );
            assertParticipationIsRejected(
                jdbcTemplate,
                "IN_PROGRESS",
                "CURRENT_TIMESTAMP(6)",
                COMPLETED_AT_CONSTRAINT_NAME
            );
            assertParticipationIsRejected(
                jdbcTemplate,
                "COMPLETED",
                null,
                COMPLETED_AT_CONSTRAINT_NAME
            );
            assertParticipationIsRejected(
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

    private void assertParticipationIsRejected(
        JdbcTemplate jdbcTemplate,
        String status,
        String completedAt,
        String expectedConstraintName
    ) {
        String completedAtExpression = completedAt == null ? "NULL" : completedAt;

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO mission_participation (
                    mission_id,
                    user_id,
                    status,
                    joined_at,
                    completed_at
                ) VALUES (1, NULL, ?, CURRENT_TIMESTAMP(6), %s)
                """.formatted(completedAtExpression),
            status
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage()).contains(expectedConstraintName);
        });
    }
}
