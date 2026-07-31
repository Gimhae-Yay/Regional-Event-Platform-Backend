package io.regionevent.regioneventbackend.domain.idempotency.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@Service
public class IdempotencyService {

    private static final String CLAIM_SQL = """
        INSERT INTO idempotency_record (
            actor_user_id,
            operation,
            idempotency_key_hash,
            request_hash,
            status,
            created_at,
            expires_at
        ) VALUES (?, ?, ?, ?, 'PROCESSING', ?, ?)
        ON DUPLICATE KEY UPDATE
            idempotency_record_id = LAST_INSERT_ID(0) + idempotency_record_id
        """;
    private static final List<IdempotencyRecordStatus> TERMINAL_STATUSES = List.of(
        IdempotencyRecordStatus.SUCCEEDED,
        IdempotencyRecordStatus.FAILED
    );

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final IdempotencyProperties properties;

    public IdempotencyService(
        IdempotencyRecordRepository idempotencyRecordRepository,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        IdempotencyProperties properties
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyAcquireResult acquire(IdempotencyCommand command) {
        Integer previousLockWaitTimeout = configureMySqlLockWaitTimeout();
        try {
            Instant now = clock.instant();
            jdbcTemplate.update(
                CLAIM_SQL,
                command.actor().getUserId(),
                command.operation().name(),
                command.idempotencyKeyHash(),
                command.requestHash(),
                now,
                now.plus(properties.retention())
            );
            Long insertedRecordId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            IdempotencyRecord record = idempotencyRecordRepository
                .findByActorUserIdAndOperationAndIdempotencyKeyHash(
                    command.actor().getUserId(),
                    command.operation(),
                    command.idempotencyKeyHash()
                )
                .orElseThrow(() -> new IllegalStateException("claimed idempotency record does not exist"));

            if (insertedRecordId != null && insertedRecordId.equals(record.getIdempotencyRecordId())) {
                return new IdempotencyAcquireResult.Acquired(record);
            }
            return toExistingResult(record, command.requestHash());
        } catch (CannotAcquireLockException | QueryTimeoutException exception) {
            return new IdempotencyAcquireResult.InProgress();
        } finally {
            restoreMySqlLockWaitTimeout(previousLockWaitTimeout);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeWithReservation(
        IdempotencyRecord record,
        String resultCode,
        Reservation reservation
    ) {
        Instant completedAt = clock.instant();
        record.completeWithReservation(
            resultCode,
            reservation,
            completedAt,
            completedAt.plus(properties.retention())
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeWithVisit(IdempotencyRecord record, String resultCode, Visit visit) {
        Instant completedAt = clock.instant();
        record.completeWithVisit(
            resultCode,
            visit,
            completedAt,
            completedAt.plus(properties.retention())
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeWithFailure(IdempotencyRecord record, String resultCode) {
        Instant completedAt = clock.instant();
        record.completeWithFailure(
            resultCode,
            completedAt,
            completedAt.plus(properties.retention())
        );
    }

    @Transactional
    public int deleteExpiredTerminalRecords() {
        return idempotencyRecordRepository.deleteExpiredTerminalRecords(
            TERMINAL_STATUSES
        );
    }

    private IdempotencyAcquireResult toExistingResult(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            return new IdempotencyAcquireResult.KeyConflict();
        }

        return switch (record.getStatus()) {
            case PROCESSING -> new IdempotencyAcquireResult.InProgress();
            case SUCCEEDED -> new IdempotencyAcquireResult.Succeeded(record);
            case FAILED -> new IdempotencyAcquireResult.Failed(record);
        };
    }

    private Integer configureMySqlLockWaitTimeout() {
        return jdbcTemplate.execute((Connection connection) -> {
            if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return null;
            }
            try (Statement statement = connection.createStatement()) {
                int previousLockWaitTimeout = findMySqlLockWaitTimeout(statement);
                statement.execute("SET SESSION innodb_lock_wait_timeout = " + properties.lockWaitTimeoutSeconds());
                return previousLockWaitTimeout;
            } catch (SQLException exception) {
                throw new IllegalStateException("failed to configure MySQL lock wait timeout", exception);
            }
        });
    }

    private int findMySqlLockWaitTimeout(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("MySQL lock wait timeout does not exist");
            }
            return resultSet.getInt(1);
        }
    }

    private void restoreMySqlLockWaitTimeout(Integer previousLockWaitTimeout) {
        if (previousLockWaitTimeout == null) {
            return;
        }
        jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = " + previousLockWaitTimeout);
    }
}
