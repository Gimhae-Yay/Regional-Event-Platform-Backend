package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

class CapacityHoldAuditEventTargetTypeMigrationTest {

    @Test
    void 감사_대상_유형_열거형의_모든_값을_DB_제약이_허용한다() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(createMigratedDataSource());

        for (AuditEventTargetType targetType : AuditEventTargetType.values()) {
            jdbcTemplate.update(
                """
                    INSERT INTO audit_event (
                        request_id, region_id, target_type, target_id, previous_state, next_state,
                        result, reason_code, actor_kind, evidence_reference, occurred_at
                    )
                    VALUES (?, NULL, ?, 1, NULL, NULL, 'SUCCESS', NULL, 'SYSTEM', ?, CURRENT_TIMESTAMP(6))
                    """,
                UUID.randomUUID().toString(),
                targetType.name(),
                "OPS-2026-0809-001"
            );
        }

        Integer capacityHoldEventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE target_type = 'CAPACITY_HOLD'",
            Integer.class
        );

        assertThat(capacityHoldEventCount).isEqualTo(1);
    }

    @Test
    void 감사_대상_유형_DB_제약은_정의되지_않은_값을_거부한다() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(createMigratedDataSource());

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO audit_event (
                    request_id, region_id, target_type, target_id, previous_state, next_state,
                    result, reason_code, actor_kind, occurred_at
                )
                VALUES (?, NULL, 'UNKNOWN', 1, NULL, NULL, 'SUCCESS', NULL, 'SYSTEM', CURRENT_TIMESTAMP(6))
                """,
            UUID.randomUUID().toString()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private DriverManagerDataSource createMigratedDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:capacity-hold-audit-target-type-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
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
