package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

class PlatformAdminAuditEventMigrationTest {

    @Test
    void P1_감사_대상_유형과_증빙_참조를_저장한다() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(createMigratedDataSource());

        List<AuditEventTargetType> targetTypes = List.of(
            AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT,
            AuditEventTargetType.USER_ROLE_ASSIGNMENT,
            AuditEventTargetType.STAMPBOOK,
            AuditEventTargetType.MISSION,
            AuditEventTargetType.COUPON_POLICY,
            AuditEventTargetType.COUPON,
            AuditEventTargetType.RESERVATION_PRICE_SNAPSHOT,
            AuditEventTargetType.PAYMENT,
            AuditEventTargetType.REFUND,
            AuditEventTargetType.PAYMENT_DISCREPANCY
        );

        for (AuditEventTargetType targetType : targetTypes) {
            jdbcTemplate.update(
                """
                    INSERT INTO audit_event (
                        request_id, target_type, target_id, result, actor_kind, evidence_reference, occurred_at
                    )
                    VALUES (?, ?, 1, 'SUCCESS', 'SYSTEM', ?, CURRENT_TIMESTAMP(6))
                    """,
                UUID.randomUUID().toString(),
                targetType.name(),
                "OPS-2026-0809-001"
            );
        }

        String evidenceReference = jdbcTemplate.queryForObject(
            """
                SELECT evidence_reference
                FROM audit_event
                WHERE target_type = 'USER_ROLE_ASSIGNMENT'
                """,
            String.class
        );

        assertThat(evidenceReference).isEqualTo("OPS-2026-0809-001");
    }

    @Test
    void P1_감사_대상_유형_제약은_정의되지_않은_값을_거부한다() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(createMigratedDataSource());

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO audit_event (
                    request_id, target_type, target_id, result, actor_kind, occurred_at
                )
                VALUES (?, 'UNKNOWN', 1, 'SUCCESS', 'SYSTEM', CURRENT_TIMESTAMP(6))
                """,
            UUID.randomUUID().toString()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private DriverManagerDataSource createMigratedDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:platform-admin-audit-event-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        return dataSource;
    }
}
