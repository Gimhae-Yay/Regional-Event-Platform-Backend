package io.regionevent.regioneventbackend.support.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MySqlDatabaseCleanerTest {

    private static final long CLEANING_CONNECTION_ID = 101L;
    private static final long BLOCKING_CONNECTION_ID = 202L;

    @Test
    @Timeout(8)
    void clean_Jdbc대기시간초과면_연결중단전잠금진단을수집한다() throws Exception {
        CleaningConnection cleaningConnection = createCleaningConnection();
        Connection diagnosticConnection = createDiagnosticConnection();
        CountDownLatch truncateExecution = new CountDownLatch(1);
        AtomicInteger executionOrder = new AtomicInteger();
        AtomicInteger diagnosticOrder = new AtomicInteger();
        AtomicInteger abortOrder = new AtomicInteger();
        AtomicInteger openedConnectionCount = new AtomicInteger();

        doAnswer(invocation -> {
            try {
                truncateExecution.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return false;
        }).when(cleaningConnection.truncateStatement()).execute(any(String.class));
        doAnswer(invocation -> {
            abortOrder.set(executionOrder.incrementAndGet());
            return null;
        }).when(cleaningConnection.connection()).abort(any(Executor.class));

        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            if (openedConnectionCount.getAndIncrement() == 0) {
                return cleaningConnection.connection();
            }
            diagnosticOrder.set(executionOrder.incrementAndGet());
            return diagnosticConnection;
        });

        IllegalStateException exception = catchThrowableOfType(
            databaseCleaner::clean,
            IllegalStateException.class
        );

        assertThat(exception)
            .hasMessageContaining("connectionId=" + CLEANING_CONNECTION_ID)
            .hasMessageContaining(
                "dataLockWaits=[" + CLEANING_CONNECTION_ID
                    + "," + BLOCKING_CONNECTION_ID
                    + ",test_schema,test_cleanup_parent,RECORD,X,IX]"
            )
            .hasMessageContaining("metadataLocks=none")
            .hasMessageContaining(
                "activeTransactions=[" + BLOCKING_CONNECTION_ID + ",RUNNING,1]"
            );
        assertThat(diagnosticOrder.get()).isPositive();
        assertThat(abortOrder.get()).isGreaterThan(diagnosticOrder.get());
    }

    @Test
    @Timeout(8)
    void clean_진단연결획득이제한시간을넘기면_unavailable진단을남긴다() throws Exception {
        CleaningConnection cleaningConnection = createCleaningConnection();
        CountDownLatch truncateExecution = new CountDownLatch(1);
        AtomicInteger openedConnectionCount = new AtomicInteger();

        doAnswer(invocation -> {
            try {
                truncateExecution.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return false;
        }).when(cleaningConnection.truncateStatement()).execute(any(String.class));

        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            if (openedConnectionCount.getAndIncrement() == 0) {
                return cleaningConnection.connection();
            }
            try {
                TimeUnit.MINUTES.sleep(1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException("diagnostic connection interrupted", exception);
            }
            throw new SQLException("diagnostic connection unexpectedly completed");
        });

        IllegalStateException exception = catchThrowableOfType(
            databaseCleaner::clean,
            IllegalStateException.class
        );

        assertThat(exception)
            .hasMessageContaining("timeoutDiagnostics={unavailable=sqlState=null, errorCode=0}");
        assertThat(exception.getCause().getSuppressed())
            .singleElement()
            .isInstanceOf(SQLException.class);
    }

    @Test
    @Timeout(3)
    void clean_최초연결획득이제한시간을넘기면_단계를식별할수있는예외를던진다() {
        CountDownLatch connectionOpening = new CountDownLatch(1);
        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            awaitWithoutInterrupt(connectionOpening);
            throw new SQLException("connection opening unexpectedly completed");
        });

        long startedAt = System.nanoTime();
        try {
            IllegalStateException exception = catchThrowableOfType(
                databaseCleaner::clean,
                IllegalStateException.class
            );

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(2));
            assertThat(exception.getCause())
                .isInstanceOf(SQLTimeoutException.class)
                .hasMessageContaining("cleanup connection opening exceeded the timeout");
        } finally {
            connectionOpening.countDown();
        }
    }

    @Test
    @Timeout(4)
    void clean_최초연결획득시간초과뒤늦게반환된연결을종료한다() throws Exception {
        Connection lateConnection = mock(Connection.class);
        CountDownLatch connectionOpening = new CountDownLatch(1);
        CountDownLatch lateConnectionClosed = new CountDownLatch(1);
        doAnswer(invocation -> {
            lateConnectionClosed.countDown();
            return null;
        }).when(lateConnection).close();
        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            awaitWithoutInterrupt(connectionOpening);
            return lateConnection;
        });

        long startedAt = System.nanoTime();
        try {
            IllegalStateException exception = catchThrowableOfType(
                databaseCleaner::clean,
                IllegalStateException.class
            );

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(2));
            assertThat(exception.getCause())
                .isInstanceOf(SQLTimeoutException.class)
                .hasMessageContaining("cleanup connection opening exceeded the timeout");

            connectionOpening.countDown();

            assertThat(lateConnectionClosed.await(2, TimeUnit.SECONDS)).isTrue();
            verify(lateConnection).close();
        } finally {
            connectionOpening.countDown();
        }
    }

    @Test
    void clean_최초연결획득이RuntimeException으로실패하면_원인을보존한다() {
        RuntimeException connectionOpeningFailure = new IllegalStateException("connection opening failed");
        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            throw connectionOpeningFailure;
        });

        IllegalStateException exception = catchThrowableOfType(
            databaseCleaner::clean,
            IllegalStateException.class
        );

        assertThat(exception.getCause())
            .isInstanceOf(SQLException.class)
            .hasCause(connectionOpeningFailure);
    }

    @Test
    @Timeout(15)
    void clean_연결중단시간초과면_세션복구없이원래진단을유지한다() throws Exception {
        CleaningConnection cleaningConnection = createCleaningConnection();
        Connection diagnosticConnection = createDiagnosticConnection();
        CountDownLatch truncateExecution = new CountDownLatch(1);
        CountDownLatch abortExecution = new CountDownLatch(1);
        AtomicInteger openedConnectionCount = new AtomicInteger();

        doAnswer(invocation -> {
            try {
                truncateExecution.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return false;
        }).when(cleaningConnection.truncateStatement()).execute(any(String.class));
        doAnswer(invocation -> {
            try {
                abortExecution.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(cleaningConnection.connection()).abort(any(Executor.class));

        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            if (openedConnectionCount.getAndIncrement() == 0) {
                return cleaningConnection.connection();
            }
            return diagnosticConnection;
        });

        IllegalStateException exception = catchThrowableOfType(
            databaseCleaner::clean,
            IllegalStateException.class
        );

        assertThat(exception)
            .hasMessageContaining("table=test_cleanup_parent")
            .hasMessageContaining("connectionId=" + CLEANING_CONNECTION_ID)
            .hasMessageContaining("timeoutDiagnostics={");
        assertThat(exception.getCause().getSuppressed())
            .singleElement()
            .satisfies(suppressed -> assertThat(suppressed.getMessage())
                .contains("timed out while aborting the MySQL cleanup connection"));
        verify(cleaningConnection.connection(), times(9)).createStatement();
        verify(cleaningConnection.connection(), times(1))
            .setNetworkTimeout(any(Executor.class), eq(5_000));
    }

    @Test
    @Timeout(10)
    void clean_연결중단과종료가제한시간을넘기면_원래진단을유지한다() throws Exception {
        CleaningConnection cleaningConnection = createCleaningConnection();
        Connection diagnosticConnection = createDiagnosticConnection();
        CountDownLatch truncateExecution = new CountDownLatch(1);
        CountDownLatch abortExecution = new CountDownLatch(1);
        CountDownLatch closeExecution = new CountDownLatch(1);
        AtomicInteger openedConnectionCount = new AtomicInteger();

        doAnswer(invocation -> {
            awaitWithoutInterrupt(truncateExecution);
            return false;
        }).when(cleaningConnection.truncateStatement()).execute(any(String.class));
        doAnswer(invocation -> {
            awaitWithoutInterrupt(abortExecution);
            return null;
        }).when(cleaningConnection.connection()).abort(any(Executor.class));
        doAnswer(invocation -> {
            awaitWithoutInterrupt(closeExecution);
            return null;
        }).when(cleaningConnection.connection()).close();

        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            if (openedConnectionCount.getAndIncrement() == 0) {
                return cleaningConnection.connection();
            }
            return diagnosticConnection;
        });

        try {
            IllegalStateException exception = catchThrowableOfType(
                databaseCleaner::clean,
                IllegalStateException.class
            );

            assertThat(exception)
                .hasMessageContaining("table=test_cleanup_parent")
                .hasMessageContaining("connectionId=" + CLEANING_CONNECTION_ID)
                .hasMessageContaining("timeoutDiagnostics={");
            assertThat(exception.getCause().getSuppressed())
                .hasSize(2)
                .anySatisfy(suppressed -> assertThat(suppressed.getMessage())
                    .contains("timed out while aborting the MySQL cleanup connection"))
                .anySatisfy(suppressed -> assertThat(suppressed.getMessage())
                    .contains("timed out while closing the MySQL cleanup connection"));
            verify(cleaningConnection.connection(), times(9)).createStatement();
            verify(cleaningConnection.connection(), times(1))
                .setNetworkTimeout(any(Executor.class), eq(5_000));
        } finally {
            abortExecution.countDown();
            closeExecution.countDown();
        }
    }

    @Test
    @Timeout(8)
    void clean_Statement종료가제한시간을넘기면_원래진단을유지한다() throws Exception {
        CleaningConnection cleaningConnection = createCleaningConnection();
        Connection diagnosticConnection = createDiagnosticConnection();
        CountDownLatch truncateExecution = new CountDownLatch(1);
        CountDownLatch statementCloseExecution = new CountDownLatch(1);
        AtomicInteger openedConnectionCount = new AtomicInteger();

        doAnswer(invocation -> {
            awaitWithoutInterrupt(truncateExecution);
            return false;
        }).when(cleaningConnection.truncateStatement()).execute(any(String.class));
        doAnswer(invocation -> {
            awaitWithoutInterrupt(statementCloseExecution);
            return null;
        }).when(cleaningConnection.truncateStatement()).close();

        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(() -> {
            if (openedConnectionCount.getAndIncrement() == 0) {
                return cleaningConnection.connection();
            }
            return diagnosticConnection;
        });

        long startedAt = System.nanoTime();
        try {
            IllegalStateException exception = catchThrowableOfType(
                databaseCleaner::clean,
                IllegalStateException.class
            );

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(6));
            assertThat(exception)
                .hasMessageContaining("table=test_cleanup_parent")
                .hasMessageContaining("connectionId=" + CLEANING_CONNECTION_ID)
                .hasMessageContaining("timeoutDiagnostics={");
            assertThat(exception.getCause().getSuppressed())
                .singleElement()
                .satisfies(suppressed -> assertThat(suppressed.getMessage())
                    .contains("timed out while closing the MySQL cleanup statement"));
            verify(cleaningConnection.truncateStatement()).close();
        } finally {
            statementCloseExecution.countDown();
        }
    }

    @Test
    void clean_Truncate실패뒤Statement종료가실패하면_원래예외에suppressed로보존한다() throws Exception {
        CleaningConnection cleaningConnection = createCleaningConnection();
        SQLException executionFailure = new SQLException("truncate execution failed");
        SQLException statementCloseFailure = new SQLException("statement close failed");
        doThrow(executionFailure).when(cleaningConnection.truncateStatement()).execute(any(String.class));
        doThrow(statementCloseFailure).when(cleaningConnection.truncateStatement()).close();

        MySqlDatabaseCleaner databaseCleaner = new MySqlDatabaseCleaner(
            cleaningConnection::connection
        );

        IllegalStateException exception = catchThrowableOfType(
            databaseCleaner::clean,
            IllegalStateException.class
        );

        assertThat(exception.getCause())
            .isSameAs(executionFailure)
            .hasSuppressedException(statementCloseFailure);
    }

    private void awaitWithoutInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private CleaningConnection createCleaningConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = createCleaningStatement();
        Statement truncateStatement = mock(Statement.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        AtomicInteger statementCount = new AtomicInteger();

        when(connection.getNetworkTimeout()).thenReturn(0);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.isClosed()).thenReturn(false);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getIdentifierQuoteString()).thenReturn("`");
        when(connection.createStatement()).thenAnswer(invocation -> {
            if (statementCount.incrementAndGet() == 9) {
                return truncateStatement;
            }
            return statement;
        });

        return new CleaningConnection(connection, truncateStatement);
    }

    private Statement createCleaningStatement() throws SQLException {
        Statement statement = mock(Statement.class);

        when(statement.execute(any(String.class))).thenReturn(false);
        when(statement.executeQuery(any(String.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("CONNECTION_ID")) {
                return singleLongResult(CLEANING_CONNECTION_ID);
            }
            if (sql.contains("FOREIGN_KEY_CHECKS")
                || sql.contains("innodb_lock_wait_timeout")
                || sql.contains("lock_wait_timeout")) {
                return singleIntegerResult(10);
            }
            if (sql.contains("information_schema.tables")) {
                return tableResult();
            }
            throw new AssertionError("unexpected cleaning query: " + sql);
        });

        return statement;
    }

    private Connection createDiagnosticConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement dataLockWaitStatement = mock(PreparedStatement.class);
        ResultSet metadataLocks = rowsResult();
        ResultSet activeTransactions = rowsResult(
            new Object[]{BLOCKING_CONNECTION_ID, "RUNNING", 1}
        );
        ResultSet dataLockWaits = rowsResult(
            new Object[]{
                CLEANING_CONNECTION_ID,
                BLOCKING_CONNECTION_ID,
                "test_schema",
                "test_cleanup_parent",
                "RECORD",
                "X",
                "IX"
            }
        );

        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(any(String.class))).thenReturn(dataLockWaitStatement);
        when(statement.executeQuery(any(String.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("CONNECTION_ID")) {
                return singleLongResult(303L);
            }
            if (sql.contains("metadata_locks")) {
                return metadataLocks;
            }
            if (sql.contains("innodb_trx")) {
                return activeTransactions;
            }
            throw new AssertionError("unexpected diagnostic query: " + sql);
        });
        when(dataLockWaitStatement.executeQuery()).thenReturn(dataLockWaits);

        return connection;
    }

    private ResultSet singleLongResult(long value) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong(1)).thenReturn(value);
        return resultSet;
    }

    private ResultSet singleIntegerResult(int value) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getInt(1)).thenReturn(value);
        return resultSet;
    }

    private ResultSet tableResult() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("TABLE_NAME")).thenReturn("test_cleanup_parent");
        return resultSet;
    }

    private ResultSet rowsResult(Object[]... rows) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        AtomicInteger rowIndex = new AtomicInteger(-1);

        when(resultSet.next()).thenAnswer(invocation -> rowIndex.incrementAndGet() < rows.length);
        when(resultSet.getObject(anyInt())).thenAnswer(invocation -> {
            int columnIndex = invocation.getArgument(0, Integer.class) - 1;
            return rows[rowIndex.get()][columnIndex];
        });

        return resultSet;
    }

    private record CleaningConnection(
        Connection connection,
        Statement truncateStatement
    ) {
    }
}
