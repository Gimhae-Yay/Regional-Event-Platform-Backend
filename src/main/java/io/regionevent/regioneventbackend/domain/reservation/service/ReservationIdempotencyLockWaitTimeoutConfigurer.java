package io.regionevent.regioneventbackend.domain.reservation.service;

import java.sql.Statement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReservationIdempotencyLockWaitTimeoutConfigurer {

    private final JdbcTemplate jdbcTemplate;
    private final int lockWaitTimeoutSeconds;

    public ReservationIdempotencyLockWaitTimeoutConfigurer(
        JdbcTemplate jdbcTemplate,
        @Value("${reservation.idempotency.lock-wait-timeout-seconds}") int lockWaitTimeoutSeconds
    ) {
        if (lockWaitTimeoutSeconds < 1) {
            throw new IllegalArgumentException("lockWaitTimeoutSeconds must be positive");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.lockWaitTimeoutSeconds = lockWaitTimeoutSeconds;
    }

    public void configureForCurrentTransaction() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            if (!databaseProductName.toLowerCase().contains("mysql")) {
                return null;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout = " + lockWaitTimeoutSeconds);
            }
            return null;
        });
    }
}
