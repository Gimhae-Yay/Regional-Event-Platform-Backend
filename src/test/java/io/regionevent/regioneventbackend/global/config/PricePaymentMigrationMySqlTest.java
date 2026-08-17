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
class PricePaymentMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_가격_결제_멱등성과_웹훅_제약을_강제한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        insertReferencedRows(jdbcTemplate);

        assertSnapshotAmountIsRejected(jdbcTemplate);
        insertPayment(jdbcTemplate, "order-1");
        assertThatThrownBy(() -> insertPayment(jdbcTemplate, "order-2"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertPaymentIdempotencyOperationIsRejected(jdbcTemplate);
        insertWebhook(jdbcTemplate, "event-1");
        assertThatThrownBy(() -> insertWebhook(jdbcTemplate, "event-1"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
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
                        INSERT INTO capacity_hold (
                            hold_id,
                            region_id,
                            session_id,
                            user_id,
                            quantity,
                            status,
                            expires_at,
                            terminal_at,
                            invalidation_reason,
                            capacity_released_at,
                            created_at
                        ) VALUES (
                            1,
                            1,
                            1,
                            NULL,
                            1,
                            'ACTIVE',
                            DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR),
                            NULL,
                            NULL,
                            NULL,
                            CURRENT_TIMESTAMP(6)
                        )
                        """
                    );
                    statement.executeUpdate(
                        """
                        INSERT INTO reservation_price_snapshot (
                            reservation_price_snapshot_id,
                            hold_id,
                            coupon_id,
                            base_amount,
                            discount_amount,
                            final_amount,
                            currency,
                            created_at
                        ) VALUES (1, 1, NULL, 10000, 0, 10000, 'KRW', CURRENT_TIMESTAMP(6))
                        """
                    );
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private void assertSnapshotAmountIsRejected(JdbcTemplate jdbcTemplate) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO reservation_price_snapshot (
                hold_id,
                coupon_id,
                base_amount,
                discount_amount,
                final_amount,
                currency,
                created_at
            ) VALUES (2, NULL, 10000, 3000, 8000, 'KRW', CURRENT_TIMESTAMP(6))
            """
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage())
                .contains("ck_reservation_price_snapshot_amount");
        });
    }

    private void insertPayment(
        JdbcTemplate jdbcTemplate,
        String orderId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO payment (
                hold_id,
                reservation_price_snapshot_id,
                reservation_id,
                order_id,
                portone_payment_id,
                status,
                finalized_at
            ) VALUES (1, 1, NULL, ?, NULL, 'PENDING', NULL)
            """,
            orderId
        );
    }

    private void assertPaymentIdempotencyOperationIsRejected(JdbcTemplate jdbcTemplate) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO payment_idempotency (
                actor_user_id,
                operation,
                idempotency_key_hash,
                request_hash,
                status,
                payment_id,
                completed_at,
                expires_at
            ) VALUES (1, 'OTHER', 'key', 'request', 'PROCESSING', NULL, NULL, NULL)
            """
        )).isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
            assertThat(exception.getSQLException().getErrorCode())
                .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
            assertThat(exception.getSQLException().getMessage())
                .contains("ck_payment_idempotency_operation");
        });
    }

    private void insertWebhook(
        JdbcTemplate jdbcTemplate,
        String providerEventId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO payment_webhook (
                provider_event_id,
                payment_id,
                authentication_result,
                processing_result,
                payload_hash,
                received_at
            ) VALUES (?, NULL, 'AUTHENTICATED', 'PROCESSED', 'hash', CURRENT_TIMESTAMP(6))
            """,
            providerEventId
        );
    }
}
