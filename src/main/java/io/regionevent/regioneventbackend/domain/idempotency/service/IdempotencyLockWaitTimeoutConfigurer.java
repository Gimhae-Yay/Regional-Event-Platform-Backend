package io.regionevent.regioneventbackend.domain.idempotency.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyLockWaitTimeoutConfigurer {

    private final JdbcTemplate jdbcTemplate;
    private final int lockWaitTimeoutSeconds;

    public IdempotencyLockWaitTimeoutConfigurer(
        JdbcTemplate jdbcTemplate,
        @Value("${reservation.idempotency.lock-wait-timeout-seconds}") int lockWaitTimeoutSeconds
    ) {
        if (lockWaitTimeoutSeconds < 1) {
            throw new IllegalArgumentException("lockWaitTimeoutSeconds must be positive");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.lockWaitTimeoutSeconds = lockWaitTimeoutSeconds;
    }

    public LockWaitTimeoutScope configureForCurrentTransaction() {
        return jdbcTemplate.execute((ConnectionCallback<LockWaitTimeoutScope>) connection -> {
            if (!isMySql(connection)) {
                return LockWaitTimeoutScope.noop();
            }
            int originalLockWaitTimeoutSeconds = findSessionLockWaitTimeoutSeconds(connection);
            setSessionLockWaitTimeoutSeconds(connection, lockWaitTimeoutSeconds);
            return new LockWaitTimeoutScope(connection, originalLockWaitTimeoutSeconds);
        });
    }

    private static boolean isMySql(Connection connection) throws SQLException {
        String databaseProductName = connection.getMetaData().getDatabaseProductName();
        return databaseProductName.toLowerCase().contains("mysql");
    }

    private static int findSessionLockWaitTimeoutSeconds(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("MySQL session lock wait timeout must be available");
            }
            return resultSet.getInt(1);
        }
    }

    private static void setSessionLockWaitTimeoutSeconds(
        Connection connection,
        int lockWaitTimeoutSeconds
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION innodb_lock_wait_timeout = " + lockWaitTimeoutSeconds);
        }
    }

    public static final class LockWaitTimeoutScope implements AutoCloseable {

        private final Connection connection;
        private final Integer originalLockWaitTimeoutSeconds;

        private LockWaitTimeoutScope(Connection connection, int originalLockWaitTimeoutSeconds) {
            this.connection = connection;
            this.originalLockWaitTimeoutSeconds = originalLockWaitTimeoutSeconds;
        }

        private LockWaitTimeoutScope() {
            this.connection = null;
            this.originalLockWaitTimeoutSeconds = null;
        }

        private static LockWaitTimeoutScope noop() {
            return new LockWaitTimeoutScope();
        }

        @Override
        public void close() {
            if (connection == null) {
                return;
            }
            try {
                setSessionLockWaitTimeoutSeconds(connection, originalLockWaitTimeoutSeconds);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to restore MySQL session lock wait timeout", exception);
            }
        }
    }
}
