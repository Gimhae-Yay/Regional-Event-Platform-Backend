package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class StampbookMigrationMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_소문자_스탬프북_상태는_두_CHECK_제약에서_거부된다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        try {
            assertLowercaseStatusIsRejected(jdbcTemplate);

            jdbcTemplate.execute("ALTER TABLE stampbook DROP CHECK ck_stampbook_status");

            assertLowercaseStatusIsRejected(jdbcTemplate);
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

    private void assertLowercaseStatusIsRejected(JdbcTemplate jdbcTemplate) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO stampbook (
                region_id,
                reward_coupon_policy_id,
                status,
                published_at,
                ended_at
            ) VALUES (1, 1, 'draft', NULL, NULL)
            """
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
