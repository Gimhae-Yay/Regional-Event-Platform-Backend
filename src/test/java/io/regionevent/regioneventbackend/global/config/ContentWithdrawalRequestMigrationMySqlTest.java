package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class ContentWithdrawalRequestMigrationMySqlTest {

    private static final String FIRST_KEY_HASH = "a".repeat(64);
    private static final String SECOND_KEY_HASH = "b".repeat(64);
    private static final String THIRD_KEY_HASH = "c".repeat(64);
    private static final String FOURTH_KEY_HASH = "d".repeat(64);
    private static final String FIFTH_KEY_HASH = "e".repeat(64);
    private static final String SIXTH_KEY_HASH = "f".repeat(64);
    private static final String SEVENTH_KEY_HASH = "0".repeat(64);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void MySQL에서_철회_요청_제약과_감사_대상_확장을_적용한다() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        try {
            insertPending(jdbcTemplate, FIRST_KEY_HASH, "첫 요청");

            assertThatThrownBy(() -> insertPending(jdbcTemplate, SECOND_KEY_HASH, "중복 대기 요청"))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, exception ->
                    assertThat(exception.getMessage())
                        .contains("uk_content_withdrawal_request_active_content")
                );

            jdbcTemplate.update(
                """
                UPDATE content_withdrawal_request
                SET status = 'INVALIDATED',
                    invalidated_at = CURRENT_TIMESTAMP(6),
                    invalidation_reason = 'CONTENT_ENDED'
                WHERE idempotency_key_hash = ?
                """,
                FIRST_KEY_HASH
            );
            insertPending(jdbcTemplate, THIRD_KEY_HASH, "새 대기 요청");

            assertThatThrownBy(() -> insertInvalidated(jdbcTemplate, FIRST_KEY_HASH, "키 재사용"))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, exception ->
                    assertThat(exception.getMessage())
                        .contains("uk_content_withdrawal_request_content_key")
                );

            assertStatusFieldChecks(jdbcTemplate);

            int auditCount = jdbcTemplate.update(
                """
                INSERT INTO audit_event (
                    request_id,
                    target_type,
                    target_id,
                    next_state,
                    result,
                    reason_code,
                    actor_kind,
                    actor_role,
                    occurred_at
                ) VALUES (?, 'CONTENT_WITHDRAWAL_REQUEST', 1, 'PENDING', 'SUCCESS',
                    'CONTENT_WITHDRAWAL_REQUESTED', 'USER', 'OPERATOR', CURRENT_TIMESTAMP(6))
                """,
                "550e8400-e29b-41d4-a716-446655440000"
            );
            assertThat(auditCount).isOne();
            assertForeignKeys(jdbcTemplate);
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private JdbcTemplate createJdbcTemplate() {
        return new JdbcTemplate(new SingleConnectionDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword(),
            true
        ));
    }

    private void migrate(JdbcTemplate jdbcTemplate) {
        Flyway.configure()
            .dataSource(jdbcTemplate.getDataSource())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private void insertPending(JdbcTemplate jdbcTemplate, String keyHash, String reason) {
        jdbcTemplate.update(
            """
            INSERT INTO content_withdrawal_request (
                content_id,
                idempotency_key_hash,
                status,
                request_reason,
                requested_at
            ) VALUES (1, ?, 'PENDING', ?, CURRENT_TIMESTAMP(6))
            """,
            keyHash,
            reason
        );
    }

    private void insertInvalidated(JdbcTemplate jdbcTemplate, String keyHash, String reason) {
        jdbcTemplate.update(
            """
            INSERT INTO content_withdrawal_request (
                content_id,
                idempotency_key_hash,
                status,
                request_reason,
                requested_at,
                invalidated_at,
                invalidation_reason
            ) VALUES (
                1,
                ?,
                'INVALIDATED',
                ?,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6),
                'CONTENT_ENDED'
            )
            """,
            keyHash,
            reason
        );
    }

    private void assertStatusFieldChecks(JdbcTemplate jdbcTemplate) {
        assertCheckViolation(
            () -> insertWithReviewFields(
                jdbcTemplate,
                FOURTH_KEY_HASH,
                "PENDING",
                "CURRENT_TIMESTAMP(6)",
                "NULL",
                "NULL",
                "NULL"
            ),
            "ck_content_withdrawal_request_pending_fields"
        );
        assertCheckViolation(
            () -> insertWithReviewFields(
                jdbcTemplate,
                FIFTH_KEY_HASH,
                "APPROVED",
                "NULL",
                "NULL",
                "NULL",
                "NULL"
            ),
            "ck_content_withdrawal_request_approved_fields"
        );
        assertCheckViolation(
            () -> insertWithReviewFields(
                jdbcTemplate,
                SIXTH_KEY_HASH,
                "REJECTED",
                "CURRENT_TIMESTAMP(6)",
                "'   '",
                "NULL",
                "NULL"
            ),
            "ck_content_withdrawal_request_rejected_fields"
        );
        assertCheckViolation(
            () -> insertWithReviewFields(
                jdbcTemplate,
                SEVENTH_KEY_HASH,
                "INVALIDATED",
                "NULL",
                "NULL",
                "CURRENT_TIMESTAMP(6)",
                "NULL"
            ),
            "ck_content_withdrawal_request_invalidated_fields"
        );
    }

    private void insertWithReviewFields(
        JdbcTemplate jdbcTemplate,
        String keyHash,
        String status,
        String reviewedAt,
        String rejectionReason,
        String invalidatedAt,
        String invalidationReason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO content_withdrawal_request (
                content_id,
                idempotency_key_hash,
                status,
                request_reason,
                requested_at,
                reviewed_at,
                rejection_reason,
                invalidated_at,
                invalidation_reason
            ) VALUES (1, ?, ?, '상태 제약 검증', CURRENT_TIMESTAMP(6), %s, %s, %s, %s)
            """.formatted(reviewedAt, rejectionReason, invalidatedAt, invalidationReason),
            keyHash,
            status
        );
    }

    private void assertCheckViolation(Runnable insertion, String constraintName) {
        assertThatThrownBy(insertion::run)
            .isInstanceOfSatisfying(DataAccessException.class, exception ->
                assertThat(exception.getMessage()).contains(constraintName)
            );
    }

    private void assertForeignKeys(JdbcTemplate jdbcTemplate) {
        List<String> foreignKeys = jdbcTemplate.queryForList(
            """
            SELECT CONSTRAINT_NAME
            FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
                AND TABLE_NAME = 'content_withdrawal_request'
            """,
            String.class
        );
        assertThat(foreignKeys).containsExactlyInAnyOrder(
            "fk_content_withdrawal_request_content",
            "fk_content_withdrawal_request_requester",
            "fk_content_withdrawal_request_reviewer",
            "fk_content_withdrawal_request_invalidator"
        );
    }
}
