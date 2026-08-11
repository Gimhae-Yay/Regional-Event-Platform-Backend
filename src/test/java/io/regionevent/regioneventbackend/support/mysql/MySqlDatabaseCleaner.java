package io.regionevent.regioneventbackend.support.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private static final int CONNECTION_OPEN_TIMEOUT_SECONDS = 1;
    private static final int DIAGNOSTIC_COLLECTION_TIMEOUT_SECONDS = 1;
    private static final int CONNECTION_ABORT_TIMEOUT_SECONDS = 1;
    private static final int STATEMENT_CLOSE_TIMEOUT_SECONDS = 1;
    private static final int CONNECTION_CLOSE_TIMEOUT_SECONDS = 1;
    private static final int MAXIMUM_DIAGNOSTIC_ROWS = 20;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final ConnectionProvider connectionProvider;

    public MySqlDatabaseCleaner(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    MySqlDatabaseCleaner(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void clean() {
        Connection connection = null;
        RuntimeException cleanupFailure = null;
        try {
            connection = openConnectionWithTimeout();
            clean(connection);
        } catch (SQLException exception) {
            cleanupFailure = new IllegalStateException("MySQL test database cleanup failed", exception);
        } catch (RuntimeException exception) {
            cleanupFailure = exception;
        } finally {
            if (connection != null) {
                closeConnection(connection, cleanupFailure);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    static MySqlDatabaseCleaner forSharedContainer() {
        return new MySqlDatabaseCleaner(SharedMySqlTestContainer::openConnection);
    }

    private Connection openConnectionWithTimeout() throws SQLException {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-connection-open");
        CompletableFuture<Connection> openedConnection = new CompletableFuture<>();
        executor.submit(() -> openConnection(openedConnection));
        try {
            return openedConnection.get(CONNECTION_OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            SQLTimeoutException timeoutException = new SQLTimeoutException(
                "MySQL cleanup connection opening exceeded the timeout",
                exception
            );
            if (!openedConnection.completeExceptionally(timeoutException)) {
                closeCompletedOpeningConnection(openedConnection);
            }
            throw timeoutException;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("MySQL cleanup connection opening was interrupted", exception);
        } catch (ExecutionException exception) {
            rethrowSqlException(exception);
            throw new IllegalStateException("unreachable");
        } finally {
            executor.shutdownNow();
        }
    }

    private void openConnection(CompletableFuture<Connection> openedConnection) {
        try {
            Connection connection = connectionProvider.open();
            if (!openedConnection.complete(connection)) {
                closeLateOpeningConnection(connection);
            }
        } catch (SQLException exception) {
            openedConnection.completeExceptionally(exception);
        } catch (RuntimeException exception) {
            openedConnection.completeExceptionally(exception);
        }
    }

    private void closeCompletedOpeningConnection(CompletableFuture<Connection> openedConnection) {
        openedConnection.thenAccept(this::closeLateOpeningConnection);
    }

    private void closeLateOpeningConnection(Connection connection) {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-late-connection-close");
        Future<?> execution = executor.submit(() -> {
            connection.close();
            return null;
        });
        boolean interrupted = Thread.interrupted();
        try {
            execution.get(CONNECTION_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            interrupted = true;
        } catch (ExecutionException | TimeoutException exception) {
            // A timed-out cleanup attempt must not block the test worker again.
        } finally {
            execution.cancel(true);
            executor.shutdownNow();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void clean(Connection connection) throws SQLException {
        int originalNetworkTimeout = configureNetworkTimeout(connection);
        boolean shouldRestoreNetworkTimeout = true;
        try {
            cleanWithTimeouts(connection);
        } catch (IllegalStateException exception) {
            shouldRestoreNetworkTimeout = !containsTruncateTimeout(exception);
            throw exception;
        } finally {
            if (shouldRestoreNetworkTimeout && !connection.isClosed()) {
                connection.setNetworkTimeout(DIRECT_EXECUTOR, originalNetworkTimeout);
            }
        }
    }

    private void cleanWithTimeouts(Connection connection) throws SQLException {
        long connectionId = findConnectionId(connection);
        boolean originalAutoCommit = connection.getAutoCommit();
        LockWaitTimeouts originalLockWaitTimeouts = configureLockWaitTimeouts(connection);
        int originalForeignKeyChecks = findForeignKeyChecks(connection);
        boolean shouldRestoreSessionSettings = true;
        try {
            if (!originalAutoCommit) {
                connection.setAutoCommit(true);
            }
            setForeignKeyChecks(connection, 0);
            for (String tableName : findApplicationTables(connection)) {
                truncate(connection, tableName, connectionId);
            }
        } catch (IllegalStateException exception) {
            shouldRestoreSessionSettings = !containsTruncateTimeout(exception);
            throw exception;
        } finally {
            if (shouldRestoreSessionSettings && !connection.isClosed()) {
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
        try {
            executeTruncate(connection, "TRUNCATE TABLE " + quote + escapedTableName + quote, connectionId);
        } catch (SQLException exception) {
            throw createTruncateFailure(tableName, connectionId, exception);
        }
    }

    private void executeTruncate(Connection connection, String sql, long connectionId) throws SQLException {
        Statement statement = createStatement(connection);
        SQLException executionFailure = null;
        try {
            executeTruncateWithTimeout(connection, statement, sql, connectionId);
        } catch (SQLException exception) {
            executionFailure = exception;
            throw exception;
        } finally {
            closeStatement(statement, executionFailure);
        }
    }

    private void closeStatement(Statement statement, SQLException executionFailure) throws SQLException {
        if (executionFailure instanceof TruncateTimeoutException timeoutException) {
            closeStatementWithTimeout(statement, timeoutException);
            return;
        }
        try {
            statement.close();
        } catch (SQLException exception) {
            if (executionFailure != null) {
                executionFailure.addSuppressed(exception);
                return;
            }
            throw exception;
        }
    }

    private void executeTruncateWithTimeout(
        Connection connection,
        Statement statement,
        String sql,
        long connectionId
    ) throws SQLException {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-truncate");
        Future<?> execution = executor.submit(() -> statement.execute(sql));
        try {
            execution.get(JDBC_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            TimeoutDiagnostics timeoutDiagnostics = collectTimeoutDiagnostics(connectionId);
            TruncateTimeoutException timeoutException = new TruncateTimeoutException(
                "TRUNCATE query exceeded JDBC wait timeout",
                exception,
                timeoutDiagnostics
            );
            preserveDiagnosticFailure(timeoutException, timeoutDiagnostics);
            abortConnection(connection, timeoutException);
            throw timeoutException;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("TRUNCATE query was interrupted", exception);
        } catch (ExecutionException exception) {
            rethrowSqlException(exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private ExecutorService newDaemonExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private void abortConnection(Connection connection, SQLException timeoutException) {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-abort");
        Future<?> execution = executor.submit(() -> {
            connection.abort(DIRECT_EXECUTOR);
            return null;
        });
        try {
            execution.get(CONNECTION_ABORT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            timeoutException.addSuppressed(new SQLException(
                "timed out while aborting the MySQL cleanup connection",
                exception
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            timeoutException.addSuppressed(new SQLException(
                "interrupted while aborting the MySQL cleanup connection",
                exception
            ));
        } catch (ExecutionException exception) {
            timeoutException.addSuppressed(new SQLException(
                "failed to abort the MySQL cleanup connection",
                exception.getCause()
            ));
        } finally {
            execution.cancel(true);
            executor.shutdownNow();
        }
    }

    private void closeConnection(Connection connection, RuntimeException cleanupFailure) {
        TruncateTimeoutException timeoutException = findTruncateTimeoutException(cleanupFailure);
        if (timeoutException != null) {
            closeConnectionWithTimeout(connection, timeoutException);
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            if (cleanupFailure != null) {
                cleanupFailure.addSuppressed(exception);
                return;
            }
            throw new IllegalStateException("MySQL test database cleanup connection close failed", exception);
        }
    }

    private void closeStatementWithTimeout(
        Statement statement,
        TruncateTimeoutException timeoutException
    ) {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-statement-close");
        Future<?> execution = executor.submit(() -> {
            statement.close();
            return null;
        });
        try {
            execution.get(STATEMENT_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            timeoutException.addSuppressed(new SQLException(
                "timed out while closing the MySQL cleanup statement",
                exception
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            timeoutException.addSuppressed(new SQLException(
                "interrupted while closing the MySQL cleanup statement",
                exception
            ));
        } catch (ExecutionException exception) {
            timeoutException.addSuppressed(new SQLException(
                "failed to close the MySQL cleanup statement",
                exception.getCause()
            ));
        } finally {
            execution.cancel(true);
            executor.shutdownNow();
        }
    }

    private void closeConnectionWithTimeout(
        Connection connection,
        TruncateTimeoutException timeoutException
    ) {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-close");
        Future<?> execution = executor.submit(() -> {
            connection.close();
            return null;
        });
        try {
            execution.get(CONNECTION_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            timeoutException.addSuppressed(new SQLException(
                "timed out while closing the MySQL cleanup connection",
                exception
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            timeoutException.addSuppressed(new SQLException(
                "interrupted while closing the MySQL cleanup connection",
                exception
            ));
        } catch (ExecutionException exception) {
            timeoutException.addSuppressed(new SQLException(
                "failed to close the MySQL cleanup connection",
                exception.getCause()
            ));
        } finally {
            execution.cancel(true);
            executor.shutdownNow();
        }
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
        TimeoutDiagnostics timeoutDiagnostics = null;
        if (isTimeout(exception)) {
            timeoutDiagnostics = findTimeoutDiagnostics(exception, connectionId);
            message += ", timeoutDiagnostics=" + timeoutDiagnostics.value();
        }
        if (timeoutDiagnostics != null && !(exception instanceof TruncateTimeoutException)) {
            preserveDiagnosticFailure(exception, timeoutDiagnostics);
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

    private boolean containsTruncateTimeout(Throwable exception) {
        return findTruncateTimeoutException(exception) != null;
    }

    private TruncateTimeoutException findTruncateTimeoutException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TruncateTimeoutException) {
                return (TruncateTimeoutException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private TimeoutDiagnostics findTimeoutDiagnostics(SQLException exception, long cleaningConnectionId) {
        if (exception instanceof TruncateTimeoutException timeoutException) {
            return timeoutException.timeoutDiagnostics();
        }
        return collectTimeoutDiagnostics(cleaningConnectionId);
    }

    private TimeoutDiagnostics collectTimeoutDiagnostics(long cleaningConnectionId) {
        ExecutorService executor = newDaemonExecutor("mysql-cleaner-diagnostics");
        Future<TimeoutDiagnostics> execution = executor.submit(
            () -> collectTimeoutDiagnosticsWithConnection(cleaningConnectionId)
        );
        try {
            return execution.get(DIAGNOSTIC_COLLECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            execution.cancel(true);
            return unavailableDiagnostics(new SQLException(
                "diagnostic connection exceeded the collection timeout",
                exception
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailableDiagnostics(new SQLException(
                "diagnostic collection was interrupted",
                exception
            ));
        } catch (ExecutionException exception) {
            return unavailableDiagnostics(new SQLException(
                "diagnostic collection failed",
                exception.getCause()
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    private TimeoutDiagnostics collectTimeoutDiagnosticsWithConnection(long cleaningConnectionId) {
        try (Connection diagnosticConnection = connectionProvider.open()) {
            long diagnosticConnectionId = findConnectionId(diagnosticConnection);
            return new TimeoutDiagnostics("{cleaningConnectionId=" + cleaningConnectionId
                + ", diagnosticConnectionId=" + diagnosticConnectionId
                + ", dataLockWaits=" + findDataLockWaits(diagnosticConnection, cleaningConnectionId)
                + ", metadataLocks=" + findPendingMetadataLocks(diagnosticConnection)
                + ", activeTransactions=" + findActiveTransactions(diagnosticConnection)
                + "}", null);
        } catch (SQLException exception) {
            return unavailableDiagnostics(exception);
        }
    }

    private TimeoutDiagnostics unavailableDiagnostics(SQLException exception) {
        return new TimeoutDiagnostics("{unavailable=" + toDiagnosticFailure(exception) + "}", exception);
    }

    private void preserveDiagnosticFailure(
        Exception failure,
        TimeoutDiagnostics timeoutDiagnostics
    ) {
        if (timeoutDiagnostics.failure() != null) {
            failure.addSuppressed(timeoutDiagnostics.failure());
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

    private record TimeoutDiagnostics(
        String value,
        SQLException failure
    ) {
    }

    private static final class TruncateTimeoutException extends SQLTimeoutException {

        private static final long serialVersionUID = 1L;

        private final TimeoutDiagnostics timeoutDiagnostics;

        private TruncateTimeoutException(
            String message,
            Throwable cause,
            TimeoutDiagnostics timeoutDiagnostics
        ) {
            super(message, cause);
            this.timeoutDiagnostics = timeoutDiagnostics;
        }

        private TimeoutDiagnostics timeoutDiagnostics() {
            return timeoutDiagnostics;
        }
    }

    @FunctionalInterface
    interface ConnectionProvider {

        Connection open() throws SQLException;
    }
}
