package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import org.mockito.InOrder;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class ReservationIdempotencyLockWaitTimeoutConfigurerTest {

    @Test
    void configureForCurrentTransaction_MySql에서는_원래_세션_대기_시간을_복구한다() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(jdbcTemplate.execute(
            org.mockito.ArgumentMatchers.<ConnectionCallback<ReservationIdempotencyLockWaitTimeoutConfigurer.LockWaitTimeoutScope>>any()
        ))
            .thenAnswer(invocation -> {
                ConnectionCallback<ReservationIdempotencyLockWaitTimeoutConfigurer.LockWaitTimeoutScope> callback =
                    invocation.getArgument(0);
                return callback.doInConnection(connection);
            });
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(50);

        ReservationIdempotencyLockWaitTimeoutConfigurer configurer =
            new ReservationIdempotencyLockWaitTimeoutConfigurer(jdbcTemplate, 3);

        try (var ignored = configurer.configureForCurrentTransaction()) {
        }

        InOrder inOrder = inOrder(statement);
        inOrder.verify(statement).execute("SET SESSION innodb_lock_wait_timeout = 3");
        inOrder.verify(statement).execute("SET SESSION innodb_lock_wait_timeout = 50");
    }
}
