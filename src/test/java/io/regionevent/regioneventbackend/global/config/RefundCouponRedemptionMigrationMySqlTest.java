package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class RefundCouponRedemptionMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_환불_시도와_쿠폰_사용의_유일_FK_CHECK_제약을_강제한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        insertReferencedRows(jdbcTemplate);

        insertRefund(jdbcTemplate);
        assertThatThrownBy(() -> insertRefund(jdbcTemplate))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertRefundAttemptValuesAreRejected(jdbcTemplate);
        insertCouponRedemption(jdbcTemplate);
        assertThatThrownBy(() -> insertCouponRedemption(jdbcTemplate))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertCouponRedemptionReversalConstraints(jdbcTemplate);
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
                            1, 1, 1, '방문 보상', 'VISIT', 1, 1, 1,
                            CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY), 0, 'DRAFT'
                        )
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO coupon (
                            coupon_id, coupon_policy_id, user_id, status, issued_at, expires_at
                        ) VALUES (
                            1, 1, NULL, 'USED', CURRENT_TIMESTAMP(6),
                            DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
                        )
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO capacity_hold (
                            hold_id, region_id, session_id, user_id, quantity, status, expires_at,
                            terminal_at, invalidation_reason, capacity_released_at, created_at
                        ) VALUES (
                            1, 1, 1, NULL, 1, 'CONSUMED', CURRENT_TIMESTAMP(6),
                            CURRENT_TIMESTAMP(6), NULL, NULL, CURRENT_TIMESTAMP(6)
                        )
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO reservation_price_snapshot (
                            reservation_price_snapshot_id, hold_id, coupon_id, base_amount,
                            discount_amount, final_amount, currency, created_at
                        ) VALUES (1, 1, 1, 10000, 3000, 7000, 'KRW', CURRENT_TIMESTAMP(6))
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO reservation (
                            reservation_id, reservation_no, qr_reference, region_id, hold_id, session_id,
                            user_id, status, confirmed_at, cancelled_at, cancellation_reason, expired_at,
                            capacity_released_at, updated_at
                        ) VALUES (
                            1, 'R-1', 'qr-1', 1, 1, 1, NULL, 'CONFIRMED', CURRENT_TIMESTAMP(6),
                            NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP(6)
                        )
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO payment (
                            payment_id, hold_id, reservation_price_snapshot_id, reservation_id, order_id,
                            portone_payment_id, status, finalized_at
                        ) VALUES (1, 1, 1, NULL, 'order-1', NULL, 'PENDING', NULL)
                        """
                    );
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private void insertRefund(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
            INSERT INTO refund (payment_id, amount, status, requested_at, completed_at)
            VALUES (1, 7000, 'REQUESTED', CURRENT_TIMESTAMP(6), NULL)
            """
        );
    }

    private void assertRefundAttemptValuesAreRejected(JdbcTemplate jdbcTemplate) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO refund_attempt (
                refund_id, attempt_no, initiator_kind, portone_cancellation_id, outcome_kind,
                failure_reason_code, external_status, result_hash, attempted_at
            ) VALUES (1, 1, 'SYSTEM', NULL, 'PENDING', NULL, 'CANCELLED', NULL, CURRENT_TIMESTAMP(6))
            """
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage())
                .contains("ck_refund_attempt_outcome_values");
        });
    }

    private void insertCouponRedemption(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
            INSERT INTO coupon_redemption (
                coupon_id, reservation_price_snapshot_id, reservation_id, status, redeemed_at, reversed_at
            ) VALUES (1, 1, 1, 'CONFIRMED', CURRENT_TIMESTAMP(6), NULL)
            """
        );
    }

    private void assertCouponRedemptionReversalConstraints(JdbcTemplate jdbcTemplate) {
        Integer uniqueRefundIndexCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
                AND table_name = 'coupon_redemption'
                AND index_name = 'uk_coupon_redemption_refund'
                AND non_unique = 0
            """,
            Integer.class
        );
        String deleteRule = jdbcTemplate.queryForObject(
            """
            SELECT delete_rule
            FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE()
                AND table_name = 'coupon_redemption'
                AND constraint_name = 'fk_coupon_redemption_refund'
            """,
            String.class
        );
        assertThat(uniqueRefundIndexCount).isEqualTo(1);
        assertThat(deleteRule).isEqualTo("RESTRICT");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE coupon_redemption SET refund_id = 1 WHERE reservation_id = 1"
        )).isInstanceOfAny(DataIntegrityViolationException.class, UncategorizedSQLException.class);

        jdbcTemplate.update(
            """
            UPDATE coupon_redemption
            SET status = 'REVERSED',
                refund_id = 1,
                reversal_reason_code = 'REFUND_SUCCEEDED',
                reversed_at = CURRENT_TIMESTAMP(6)
            WHERE reservation_id = 1
            """
        );
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM refund WHERE refund_id = 1"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            UPDATE coupon_redemption
            SET refund_id = NULL,
                reversal_reason_code = 'REFUND_SUCCEEDED'
            WHERE reservation_id = 1
            """
        )).isInstanceOfAny(DataIntegrityViolationException.class, UncategorizedSQLException.class);

        jdbcTemplate.update(
            """
            UPDATE coupon_redemption
            SET status = 'CONFIRMED',
                refund_id = NULL,
                reversal_reason_code = NULL,
                reversed_at = NULL
            WHERE reservation_id = 1
            """
        );
        jdbcTemplate.update(
            """
            UPDATE coupon_redemption
            SET status = 'REVERSED',
                reversal_reason_code = 'RESERVATION_CANCELLED',
                reversed_at = CURRENT_TIMESTAMP(6)
            WHERE reservation_id = 1
            """
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT refund_id FROM coupon_redemption WHERE reservation_id = 1",
            Long.class
        )).isNull();
    }
}
