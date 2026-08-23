package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class PortOnePaymentIdRepairMigrationMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void V46은_검증이력의결제식별자로_승인및불일치결제를보정한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrateToV45(jdbcTemplate);
        insertPaymentFixtures(jdbcTemplate);

        migrateAll(jdbcTemplate);

        assertThat(jdbcTemplate.queryForList(
            "SELECT payment_id, portone_payment_id FROM payment ORDER BY payment_id"
        )).containsExactly(
            java.util.Map.of("payment_id", 1L, "portone_payment_id", "payment-approved"),
            java.util.Map.of("payment_id", 2L, "portone_payment_id", "payment-discrepant"),
            java.util.Map.of("payment_id", 3L, "portone_payment_id", "payment-late-approval"),
            java.util.Map.of("payment_id", 4L, "portone_payment_id", "transaction-unverified")
        );
    }

    private JdbcTemplate createJdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        ));
    }

    private void migrateToV45(JdbcTemplate jdbcTemplate) {
        Flyway.configure()
            .dataSource(jdbcTemplate.getDataSource())
            .locations("classpath:db/migration")
            .target("45")
            .load()
            .migrate();
    }

    private void migrateAll(JdbcTemplate jdbcTemplate) {
        Flyway.configure()
            .dataSource(jdbcTemplate.getDataSource())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private void insertPaymentFixtures(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    insertPayment(statement, 1, "order-approved", "transaction-approved", "APPROVED");
                    insertPayment(statement, 2, "order-discrepant", "transaction-discrepant", "DISCREPANT");
                    insertPayment(statement, 3, "order-late-approval", "transaction-late-approval", "DISCREPANT");
                    insertPayment(statement, 4, "order-unverified", "transaction-unverified", "APPROVED");
                    insertVerification(statement, 1, "payment-approved", "APPROVE");
                    insertVerification(statement, 2, "payment-discrepant", "DISCREPANT");
                    insertVerification(statement, 3, "payment-late-approval", "APPROVE");
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private void insertPayment(
        Statement statement,
        long paymentId,
        String orderId,
        String portonePaymentId,
        String status
    ) throws java.sql.SQLException {
        statement.executeUpdate(("""
            INSERT INTO payment (
                payment_id,
                hold_id,
                reservation_price_snapshot_id,
                reservation_id,
                order_id,
                portone_payment_id,
                status,
                finalized_at
            ) VALUES (%d, %d, %d, NULL, '%s', '%s', '%s', CURRENT_TIMESTAMP(6))
            """).formatted(paymentId, paymentId, paymentId, orderId, portonePaymentId, status));
    }

    private void insertVerification(
        Statement statement,
        long paymentId,
        String observedPaymentId,
        String decision
    ) throws java.sql.SQLException {
        statement.executeUpdate(("""
            INSERT INTO payment_verification (
                payment_id,
                verification_reason,
                observed_amount,
                observed_currency,
                observed_order_id,
                external_status,
                internal_decision,
                response_hash,
                verified_at
            ) VALUES (%d, 'Transaction.Paid', 10000, 'KRW', '%s', 'PAID', '%s', 'hash-%d', CURRENT_TIMESTAMP(6))
            """).formatted(paymentId, observedPaymentId, decision, paymentId));
    }
}
