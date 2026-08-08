package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class MissionRewardCouponMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_미션_보상_쿠폰의_FK_UNIQUE와_CHECK_제약을_강제한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        insertReferencedRows(jdbcTemplate);

        insertMissionRewardClaim(jdbcTemplate, 1L, 1L);
        assertThatThrownBy(() -> insertMissionRewardClaim(jdbcTemplate, 1L, 1L))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO coupon (
                coupon_policy_id,
                user_id,
                status,
                issued_at,
                expires_at
            ) VALUES (?, NULL, 'AVAILABLE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """,
            Long.MAX_VALUE
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertCouponIssuanceSourceIsRejected(jdbcTemplate);
        assertCouponStatusIsRejected(jdbcTemplate);
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
                        INSERT INTO coupon_policy (
                            coupon_policy_id,
                            content_id,
                            region_id,
                            name,
                            issuance_type,
                            discount_amount,
                            minimum_payment_amount,
                            valid_days,
                            issue_starts_at,
                            issue_ends_at,
                            issued_count,
                            status
                        ) VALUES (
                            1,
                            1,
                            1,
                            '미션 보상',
                            'MISSION_REWARD',
                            1,
                            1,
                            1,
                            CURRENT_TIMESTAMP(6),
                            DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY),
                            0,
                            'DRAFT'
                        )
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO mission_participation (
                            mission_participation_id,
                            mission_id,
                            user_id,
                            status,
                            joined_at,
                            completed_at
                        ) VALUES (1, 1, NULL, 'COMPLETED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO coupon (
                            coupon_id,
                            coupon_policy_id,
                            user_id,
                            status,
                            issued_at,
                            expires_at
                        ) VALUES (
                            1,
                            1,
                            NULL,
                            'AVAILABLE',
                            CURRENT_TIMESTAMP(6),
                            DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
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

    private void insertMissionRewardClaim(
        JdbcTemplate jdbcTemplate,
        Long missionParticipationId,
        Long couponPolicyId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mission_reward_claim (
                mission_participation_id,
                coupon_policy_id,
                claimed_at
            ) VALUES (?, ?, CURRENT_TIMESTAMP(6))
            """,
            missionParticipationId,
            couponPolicyId
        );
    }

    private void assertCouponIssuanceSourceIsRejected(JdbcTemplate jdbcTemplate) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO coupon_issuance (
                coupon_id,
                coupon_policy_id,
                recipient_user_id,
                issuance_identity_hash,
                issued_at
            ) VALUES (1, 1, NULL, 'missing-source', CURRENT_TIMESTAMP(6))
            """
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage())
                .contains("ck_coupon_issuance_exactly_one_source");
        });
    }

    private void assertCouponStatusIsRejected(JdbcTemplate jdbcTemplate) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO coupon_status_history (
                coupon_id,
                previous_status,
                next_status,
                reason_code,
                actor_kind,
                occurred_at
            ) VALUES (1, NULL, 'INVALID', 'TEST', 'SYSTEM', CURRENT_TIMESTAMP(6))
            """
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage())
                .contains("ck_coupon_status_history_next_status");
        });
    }
}
