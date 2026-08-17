package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class StampbookRewardGrantMigrationMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_완료_보상_지급의_진행별_UNIQUE와_두_FK_제약을_강제한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        insertReferencedRows(jdbcTemplate);
        insertRewardGrant(jdbcTemplate, 1L, 1L);

        assertThatThrownBy(() -> insertRewardGrant(jdbcTemplate, 1L, 1L))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRewardGrant(jdbcTemplate, 2L, 1L))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRewardGrant(jdbcTemplate, 1L, 2L))
            .isInstanceOf(DataIntegrityViolationException.class);
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

    private void insertReferencedRows(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    statement.executeUpdate(
                        """
                        INSERT INTO stampbook_progress (
                            stampbook_progress_id,
                            stampbook_id,
                            user_id,
                            status,
                            completed_at
                        ) VALUES (1, 1, NULL, 'COMPLETED', CURRENT_TIMESTAMP(6))
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO coupon_policy (
                            coupon_policy_id,
                            content_id,
                            region_id,
                            name,
                            description,
                            issuance_type,
                            discount_amount,
                            minimum_payment_amount,
                            valid_days,
                            issue_starts_at,
                            issue_ends_at,
                            total_issue_limit,
                            issued_count,
                            status,
                            published_at,
                            ended_at
                        ) VALUES (
                            1,
                            1,
                            1,
                            '스탬프북 완료 보상',
                            NULL,
                            'STAMPBOOK_COMPLETION',
                            1,
                            1,
                            1,
                            CURRENT_TIMESTAMP(6),
                            DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY),
                            NULL,
                            0,
                            'DRAFT',
                            NULL,
                            NULL
                        )
                        """
                    );
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private void insertRewardGrant(
        JdbcTemplate jdbcTemplate,
        Long stampbookProgressId,
        Long couponPolicyId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO stampbook_reward_grant (
                stampbook_progress_id,
                coupon_policy_id,
                granted_at
            ) VALUES (?, ?, CURRENT_TIMESTAMP(6))
            """,
            stampbookProgressId,
            couponPolicyId
        );
    }
}
