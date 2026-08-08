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
    void 빈_데이터베이스에_V1부터_V16까지_현재_스키마를_생성한다() {
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
        List<String> contentColumnNames = jdbcTemplate.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'CONTENT'
                """,
            String.class
        );
        List<String> contentRevisionColumnNames = jdbcTemplate.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'CONTENT_REVISION'
            """,
            String.class
        );
        List<String> imageObjectColumnNames = jdbcTemplate.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'IMAGE_OBJECT'
                """,
            String.class
        );
        List<String> contentSessionColumnNames = jdbcTemplate.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'CONTENT_SESSION'
                """,
            String.class
        );
        List<String> capacityHoldIndexNames = jdbcTemplate.queryForList(
            """
                SELECT index_name
                FROM information_schema.indexes
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'CAPACITY_HOLD'
                """,
            String.class
        );

        assertThat(appliedVersions).containsExactly(
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "10",
            "11",
            "12",
            "13",
            "14",
            "15",
            "16"
        );
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
            "SESSION_REVISION",
            "CAPACITY_HOLD",
            "RESERVATION",
            "IDEMPOTENCY_RECORD",
            "VISIT",
            "REVIEW",
            "AUDIT_EVENT",
            "AUDIT_EVENT_ACTOR_LINK",
            "COUPON_POLICY"
        );
        assertThat(tableNames).doesNotContain(
            "CONTENT_REPRESENTATIVE_IMAGE",
            "CONTENT_REVISION_REPRESENTATIVE_IMAGE"
        );
        assertThat(constraintNames).contains(
            "PK_REGION",
            "UK_CONTENT_REVISION_CONTENT_REVISION_NO",
            "FK_CONTENT_REPRESENTATIVE_IMAGE_OBJECT",
            "FK_CONTENT_REVISION_CANDIDATE_IMAGE_OBJECT",
            "FK_CONTENT_SESSION_CONTENT_REGION",
            "FK_CONTENT_SESSION_REVIEWED_BY_USER",
            "CK_CONTENT_SESSION_STATUS_V2",
            "CK_CONTENT_SESSION_REVIEW_STATE",
            "FK_SESSION_REVISION_TARGET_SESSION_CONTENT_REGION",
            "FK_IMAGE_OBJECT_CREATED_BY_USER",
            "FK_IMAGE_OBJECT_REGION",
            "CK_SESSION_REVISION_REVIEW_STATE",
            "FK_RESERVATION_HOLD_SESSION_REGION",
            "UK_RESERVATION_RESERVATION_NO",
            "FK_VISIT_RESERVATION_SESSION_REGION",
            "FK_REVIEW_VISIT_CONTENT_REGION",
            "CK_CAPACITY_HOLD_TERMINAL",
            "CK_IDEMPOTENCY_RECORD_PROCESSING_RESULT",
            "CK_IDEMPOTENCY_RECORD_FAILED_RESULT",
            "CK_IDEMPOTENCY_RECORD_RESERVATION_RESULT",
            "CK_IDEMPOTENCY_RECORD_VISIT_RESULT",
            "CK_REVIEW_STATE",
            "CK_CONTENT_REVISION_REVIEWED",
            "FK_COUPON_POLICY_CONTENT_REGION",
            "FK_COUPON_POLICY_REGION",
            "CK_COUPON_POLICY_STATUS_TIMESTAMPS"
        );
        assertThat(contentColumnNames).contains(
            "REPRESENTATIVE_IMAGE_OBJECT_ID",
            "REPRESENTATIVE_IMAGE_ASSIGNED_AT"
        );
        assertThat(contentRevisionColumnNames).contains(
            "CANDIDATE_IMAGE_OBJECT_ID",
            "CANDIDATE_IMAGE_ASSIGNED_AT",
            "PUBLISH_AT"
        );
        assertThat(contentSessionColumnNames).contains(
            "REVIEWED_AT",
            "REVIEWED_BY_USER_ID",
            "REJECT_REASON"
        );
        assertThat(capacityHoldIndexNames).contains("IDX_CAPACITY_HOLD_STATUS_EXPIRES_AT");
        assertThat(imageObjectColumnNames).contains(
            "CREATED_BY_USER_ID",
            "REGION_ID",
            "UPLOAD_EXPIRES_AT",
            "LINKED_AT"
        );
    }
}
