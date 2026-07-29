package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class InitialP0SchemaMigrationTest {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InitialP0SchemaMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 빈_데이터베이스에_단일_V1으로_P0_전체_스키마를_생성한다() {
        List<String> appliedVersions = jdbcTemplate.queryForList(
            "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL AND \"success\" = TRUE",
            String.class
        );
        List<String> tableNames = jdbcTemplate.queryForList(
            """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'PUBLIC'
                  AND table_type = 'BASE TABLE'
                """,
            String.class
        );
        List<String> constraintNames = jdbcTemplate.queryForList(
            """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'PUBLIC'
                """,
            String.class
        );

        assertThat(appliedVersions).containsExactly("1");
        assertThat(tableNames).contains(
            "REGION",
            "APP_USER",
            "USER_ROLE_ASSIGNMENT",
            "OPERATOR_APPLICATION",
            "IMAGE_OBJECT",
            "CONTENT",
            "CONTENT_SESSION",
            "CONTENT_LOG",
            "CONTENT_REVISION",
            "CONTENT_REPRESENTATIVE_IMAGE",
            "CONTENT_REVISION_REPRESENTATIVE_IMAGE",
            "CAPACITY_HOLD",
            "RESERVATION",
            "IDEMPOTENCY_RECORD",
            "VISIT",
            "REVIEW",
            "AUDIT_EVENT",
            "AUDIT_EVENT_ACTOR_LINK"
        );
        assertThat(constraintNames).contains(
            "PK_REGION",
            "UK_CONTENT_REVISION_CONTENT_REVISION_NO",
            "FK_CONTENT_SESSION_CONTENT_REGION",
            "FK_RESERVATION_HOLD_SESSION_REGION",
            "FK_VISIT_RESERVATION_SESSION_REGION",
            "FK_REVIEW_VISIT_CONTENT_REGION",
            "CK_IDEMPOTENCY_RECORD_RESULT"
        );
    }
}
