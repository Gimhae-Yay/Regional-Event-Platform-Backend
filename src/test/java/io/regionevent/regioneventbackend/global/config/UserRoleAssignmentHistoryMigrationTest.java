package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class UserRoleAssignmentHistoryMigrationTest {

    @Test
    void 기존_역할_배정을_활성_이력으로_이관한다() {
        DriverManagerDataSource dataSource = createDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateTo(dataSource, "16");
        insertP0RoleAssignments(jdbcTemplate);

        migrateTo(dataSource, "17");

        Map<String, Object> visitorAssignment = jdbcTemplate.queryForMap(
            """
                SELECT role_assignment_id, status, revoked_at, revoke_reason_code
                FROM user_role_assignment
                WHERE user_id = 1 AND role = 'VISITOR'
                """
        );
        Map<String, Object> regionAdminAssignment = jdbcTemplate.queryForMap(
            """
                SELECT role_assignment_id, status, region_id, revoked_at, revoke_reason_code
                FROM user_role_assignment
                WHERE user_id = 2 AND role = 'REGION_ADMIN'
                """
        );

        assertThat(visitorAssignment)
            .containsEntry("STATUS", "ACTIVE")
            .containsEntry("REVOKED_AT", null)
            .containsEntry("REVOKE_REASON_CODE", null);
        assertThat(visitorAssignment.get("ROLE_ASSIGNMENT_ID")).isInstanceOf(Number.class);
        assertThat(regionAdminAssignment)
            .containsEntry("STATUS", "ACTIVE")
            .containsEntry("REGION_ID", 1L)
            .containsEntry("REVOKED_AT", null)
            .containsEntry("REVOKE_REASON_CODE", null);
        assertThat(regionAdminAssignment.get("ROLE_ASSIGNMENT_ID")).isInstanceOf(Number.class);
    }

    private DriverManagerDataSource createDataSource() {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:user-role-assignment-history-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
    }

    private void migrateTo(
        DriverManagerDataSource dataSource,
        String targetVersion
    ) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(targetVersion)
            .load()
            .migrate();
    }

    private void insertP0RoleAssignments(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
                INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
                VALUES (1, 'GIMHAE', '김해시', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO app_user (
                    user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at
                )
                VALUES
                    (1, 'visitor@example.com', 'hash', '방문자', '010-1111-1111', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
                    (2, 'admin@example.com', 'hash', '관리자', '010-2222-2222', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO user_role_assignment (user_id, role, region_id, granted_at)
                VALUES
                    (1, 'VISITOR', NULL, CURRENT_TIMESTAMP(6)),
                    (2, 'REGION_ADMIN', 1, CURRENT_TIMESTAMP(6))
                """
        );
    }
}
