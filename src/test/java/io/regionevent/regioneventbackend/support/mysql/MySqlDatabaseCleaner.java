package io.regionevent.regioneventbackend.support.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

public final class MySqlDatabaseCleaner {

    private static final String FLYWAY_SCHEMA_HISTORY = "flyway_schema_history";
    private static final int LOCK_WAIT_TIMEOUT_SECONDS = 2;
    private static final int JDBC_QUERY_TIMEOUT_SECONDS = 3;
    private static final int JDBC_NETWORK_TIMEOUT_MILLIS = 5_000;
    private static final int MAXIMUM_DIAGNOSTIC_ROWS = 20;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final ConnectionProvider connectionProvider;

    public MySqlDatabaseCleaner(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    private MySqlDatabaseCleaner(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void clean() {
        try (Connection connection = connectionProvider.open()) {
            clean(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("MySQL test database cleanup failed", exception);
        }
    }

    static MySqlDatabaseCleaner forSharedContainer() {
        return new MySqlDatabaseCleaner(SharedMySqlTestContainer::openConnection);
    }

    private void clean(Connection connection) throws SQLException {
        int originalNetworkTimeout = configureNetworkTimeout(connection);
        try {
            cleanWithTimeouts(connection);
        } finally {
            if (!connection.isClosed()) {
                connection.setNetworkTimeout(DIRECT_EXECUTOR, originalNetworkTimeout);
            }
        }
    }

    private void cleanWithTimeouts(Connection connection) throws SQLException {
        long connectionId = findConnectionId(connection);
        boolean originalAutoCommit = connection.getAutoCommit();
        LockWaitTimeouts originalLockWaitTimeouts = configureLockWaitTimeouts(connection);
        int originalForeignKeyChecks = findForeignKeyChecks(connection);
        try {
            if (!originalAutoCommit) {
                connection.setAutoCommit(true);
            }
            setForeignKeyChecks(connection, 0);
            for (String tableName : findApplicationTables(connection)) {
                truncate(connection, tableName, connectionId);
            }
        } finally {
            if (!connection.isClosed()) {
                setForeignKeyChecks(connection, originalForeignKeyChecks);
                restoreLockWaitTimeouts(connection, originalLockWaitTimeouts);
                if (!originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
            }
        }
    }

    private int findForeignKeyChecks(Connection connection) throws SQLException {
        try (
            Statement statement = createStatement(connection);
            ResultSet resultSet = statement.executeQuery("SELECT @@SESSION.FOREIGN_KEY_CHECKS")
        ) {
            if (!resultSet.next()) {
                throw new SQLException("FOREIGN_KEY_CHECKS query returned no result");
            }
            return resultSet.getInt(1);
        }
    }

    private void setForeignKeyChecks(Connection connection, int enabled) throws SQLException {
        try (Statement statement = createStatement(connection)) {
            statement.execute("SET SESSION FOREIGN_KEY_CHECKS = " + enabled);
        }
    }

    private List<String> findApplicationTables(Connection connection) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (
            Statement statement = createStatement(connection);
            ResultSet tables = statement.executeQuery("""
                SELECT TABLE_NAME
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """)
        ) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (!FLYWAY_SCHEMA_HISTORY.equalsIgnoreCase(tableName)) {
                    tableNames.add(tableName);
                }
            }
        }
        tableNames.sort(Comparator.naturalOrder());
        return tableNames;
    }

    private void truncate(Connection connection, String tableName, long connectionId) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString().trim();
        String escapedTableName = tableName.replace(quote, quote + quote);
        try (Statement statement = createStatement(connection)) {
            executeTruncateWithTimeout(
                connection,
                statement,
                "TRUNCATE TABLE " + quote + escapedTableName + quote
            );
        } catch (SQLException exception) {
            throw createTruncateFailure(tableName, connectionId, exception);
        }
    }

    private void executeTruncateWithTimeout(
        Connection connection,
        Statement statement,
        String sql
    ) throws SQLException {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mysql-cleaner-truncate");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> execution = executor.submit(() -> statement.execute(sql));
        try {
            execution.get(JDBC_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            abortConnection(connection);
            throw new SQLTimeoutException("TRUNCATE query exceeded JDBC wait timeout", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("TRUNCATE query was interrupted", exception);
        } catch (ExecutionException exception) {
            rethrowSqlException(exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private void abortConnection(Connection connection) {
        Thread abortThread = new Thread(() -> {
            try {
                connection.abort(DIRECT_EXECUTOR);
            } catch (SQLException exception) {
                throw new IllegalStateException("failed to abort timed out MySQL cleanup connection", exception);
            }
        }, "mysql-cleaner-abort");
        abortThread.setDaemon(true);
        abortThread.start();
    }

    private void rethrowSqlException(ExecutionException exception) throws SQLException {
        Throwable cause = exception.getCause();
        if (cause instanceof SQLException sqlException) {
            throw sqlException;
        }
        throw new SQLException("TRUNCATE query execution failed", cause);
    }

    private Statement createStatement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private int configureNetworkTimeout(Connection connection) throws SQLException {
        int originalNetworkTimeout = connection.getNetworkTimeout();
        connection.setNetworkTimeout(DIRECT_EXECUTOR, JDBC_NETWORK_TIMEOUT_MILLIS);
        return originalNetworkTimeout;
    }

    private long findConnectionId(Connection connection) throws SQLException {
        try (
            Statement statement = createStatement(connection);
            ResultSet resultSet = statement.executeQuery("SELECT CONNECTION_ID()")
        ) {
            if (!resultSet.next()) {
                throw new SQLException("CONNECTION_ID query returned no result");
            }
            return resultSet.getLong(1);
        }
    }

    private LockWaitTimeouts configureLockWaitTimeouts(Connection connection) throws SQLException {
        int originalInnoDbLockWaitTimeout = findSessionTimeout(connection, "innodb_lock_wait_timeout");
        int originalMetadataLockWaitTimeout = findSessionTimeout(connection, "lock_wait_timeout");
        try {
            setSessionTimeout(connection, "innodb_lock_wait_timeout", LOCK_WAIT_TIMEOUT_SECONDS);
            setSessionTimeout(connection, "lock_wait_timeout", LOCK_WAIT_TIMEOUT_SECONDS);
            return new LockWaitTimeouts(originalInnoDbLockWaitTimeout, originalMetadataLockWaitTimeout);
        } catch (SQLException exception) {
            setSessionTimeout(connection, "innodb_lock_wait_timeout", originalInnoDbLockWaitTimeout);
            throw exception;
        }
    }

    private int findSessionTimeout(Connection connection, String timeoutName) throws SQLException {
        try (
            Statement statement = createStatement(connection);
            ResultSet resultSet = statement.executeQuery("SELECT @@SESSION." + timeoutName)
        ) {
            if (!resultSet.next()) {
                throw new SQLException(timeoutName + " query returned no result");
            }
            return resultSet.getInt(1);
        }
    }

    private void setSessionTimeout(Connection connection, String timeoutName, int timeoutSeconds) throws SQLException {
        try (Statement statement = createStatement(connection)) {
            statement.execute("SET SESSION " + timeoutName + " = " + timeoutSeconds);
        }
    }

    private void restoreLockWaitTimeouts(Connection connection, LockWaitTimeouts originalTimeouts) throws SQLException {
        setSessionTimeout(connection, "innodb_lock_wait_timeout", originalTimeouts.innoDbLockWaitTimeout());
        setSessionTimeout(connection, "lock_wait_timeout", originalTimeouts.metadataLockWaitTimeout());
    }

    private IllegalStateException createTruncateFailure(String tableName, long connectionId, SQLException exception) {
        String message = "MySQL test database cleanup TRUNCATE failed: table=" + tableName
            + ", connectionId=" + connectionId;
        if (isTimeout(exception)) {
            message += ", timeoutDiagnostics=" + collectTimeoutDiagnostics(connectionId);
        }
        return new IllegalStateException(message, exception);
    }

    private boolean isTimeout(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (current instanceof SQLTimeoutException
                || current.getErrorCode() == 1205
                || "HYT00".equals(current.getSQLState())
                || "S1T00".equals(current.getSQLState())
                || "08S01".equals(current.getSQLState())) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private String collectTimeoutDiagnostics(long cleaningConnectionId) {
        try (Connection diagnosticConnection = connectionProvider.open()) {
            long diagnosticConnectionId = findConnectionId(diagnosticConnection);
            return "{cleaningConnectionId=" + cleaningConnectionId
                + ", diagnosticConnectionId=" + diagnosticConnectionId
                + ", dataLockWaits=" + findDataLockWaits(diagnosticConnection, cleaningConnectionId)
                + ", metadataLocks=" + findPendingMetadataLocks(diagnosticConnection)
                + ", activeTransactions=" + findActiveTransactions(diagnosticConnection)
                + "}";
        } catch (SQLException exception) {
            return "{unavailable=" + toDiagnosticFailure(exception) + "}";
        }
    }

    private String findDataLockWaits(Connection connection, long cleaningConnectionId) {
        try (
            var statement = connection.prepareStatement("""
                SELECT waiting_thread.processlist_id,
                       blocking_thread.processlist_id,
                       requested_lock.object_schema,
                       requested_lock.object_name,
                       requested_lock.lock_type,
                       requested_lock.lock_mode,
                       blocking_lock.lock_mode
                FROM performance_schema.data_lock_waits AS lock_wait
                JOIN performance_schema.threads AS waiting_thread
                    ON waiting_thread.thread_id = lock_wait.requesting_thread_id
                JOIN performance_schema.threads AS blocking_thread
                    ON blocking_thread.thread_id = lock_wait.blocking_thread_id
                JOIN performance_schema.data_locks AS requested_lock
                    ON requested_lock.engine_lock_id = lock_wait.requesting_engine_lock_id
                JOIN performance_schema.data_locks AS blocking_lock
                    ON blocking_lock.engine_lock_id = lock_wait.blocking_engine_lock_id
                WHERE waiting_thread.processlist_id = ?
                LIMIT ?
                """)
        ) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            statement.setLong(1, cleaningConnectionId);
            statement.setInt(2, MAXIMUM_DIAGNOSTIC_ROWS);
            try (ResultSet resultSet = statement.executeQuery()) {
                return toRows(resultSet, 7);
            }
        } catch (SQLException exception) {
            return "unavailable(" + toDiagnosticFailure(exception) + ")";
        }
    }

    private String findPendingMetadataLocks(Connection connection) {
        try (
            Statement statement = createStatement(connection);
            ResultSet resultSet = statement.executeQuery("""
                SELECT object_type,
                       object_schema,
                       object_name,
                       lock_type,
                       lock_duration,
                       lock_status,
                       owner_thread_id
                FROM performance_schema.metadata_locks
                WHERE object_schema = DATABASE()
                  AND lock_status = 'PENDING'
                LIMIT 20
                """)
        ) {
            return toRows(resultSet, 7);
        } catch (SQLException exception) {
            return "unavailable(" + toDiagnosticFailure(exception) + ")";
        }
    }

    private String findActiveTransactions(Connection connection) {
        try (
            Statement statement = createStatement(connection);
            ResultSet resultSet = statement.executeQuery("""
                SELECT trx_mysql_thread_id,
                       trx_state,
                       TIMESTAMPDIFF(SECOND, trx_started, NOW())
                FROM information_schema.innodb_trx
                LIMIT 20
                """)
        ) {
            return toRows(resultSet, 3);
        } catch (SQLException exception) {
            return "unavailable(" + toDiagnosticFailure(exception) + ")";
        }
    }

    private String toRows(ResultSet resultSet, int columnCount) throws SQLException {
        List<String> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<String> columns = new ArrayList<>();
            for (int column = 1; column <= columnCount; column++) {
                columns.add(String.valueOf(resultSet.getObject(column)));
            }
            rows.add("[" + String.join(",", columns) + "]");
        }
        return rows.isEmpty() ? "none" : String.join(";", rows);
    }

    private String toDiagnosticFailure(SQLException exception) {
        return "sqlState=" + exception.getSQLState() + ", errorCode=" + exception.getErrorCode();
    }

    private record LockWaitTimeouts(
        int innoDbLockWaitTimeout,
        int metadataLockWaitTimeout
    ) {
    }

    @FunctionalInterface
    private interface ConnectionProvider {

        Connection open() throws SQLException;
    }
}
