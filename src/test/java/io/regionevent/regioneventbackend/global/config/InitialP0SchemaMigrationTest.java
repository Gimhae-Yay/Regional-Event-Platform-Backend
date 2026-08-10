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
    void 빈_데이터베이스에_현재_스키마를_생성한다() {
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
        List<String> appUserColumnNames = jdbcTemplate.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'APP_USER'
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
        List<String> userRoleAssignmentColumnNames = jdbcTemplate.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'USER_ROLE_ASSIGNMENT'
                """,
            String.class
        );
        List<String> userRoleAssignmentIndexNames = jdbcTemplate.queryForList(
            """
                SELECT index_name
                FROM information_schema.indexes
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'USER_ROLE_ASSIGNMENT'
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
            "16",
            "17",
            "18",
            "19",
            "20",
            "21",
            "22",
            "23",
            "24",
            "25",
            "26",
            "27",
            "28",
            "29",
            "30",
            "31",
            "32"
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
            "COUPON_POLICY",
            "PLATFORM_ADMIN_ASSIGNMENT",
            "MISSION",
            "MISSION_TARGET_CONTENT",
            "STAMPBOOK",
            "STAMPBOOK_CONTENT",
            "STAMPBOOK_PROGRESS",
            "STAMPBOOK_REWARD_GRANT",
            "MISSION_PARTICIPATION",
            "MISSION_PROGRESS",
            "STAMP_EARN",
            "MISSION_REWARD_CLAIM",
            "COUPON",
            "COUPON_ISSUANCE",
            "COUPON_STATUS_HISTORY",
            "RESERVATION_PRICE_SNAPSHOT",
            "PAYMENT",
            "PAYMENT_IDEMPOTENCY",
            "PAYMENT_VERIFICATION",
            "PAYMENT_WEBHOOK",
            "PAYMENT_DISCREPANCY",
            "PAYMENT_DISCREPANCY_ACTION",
            "REFUND",
            "REFUND_ATTEMPT",
            "COUPON_REDEMPTION"
        );
        assertThat(tableNames).doesNotContain(
            "CONTENT_REPRESENTATIVE_IMAGE",
            "CONTENT_REVISION_REPRESENTATIVE_IMAGE"
        );
        assertThat(constraintNames).contains(
            "PK_REGION",
            "CK_REGION_REGION_CODE_NORMALIZED",
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
            "CK_CONTENT_RESERVATION_PRICE",
            "CK_CONTENT_REVISION_RESERVATION_PRICE",
            "FK_COUPON_POLICY_CONTENT_REGION",
            "FK_COUPON_POLICY_REGION",
            "CK_COUPON_POLICY_STATUS_TIMESTAMPS",
            "CK_APP_USER_ACCOUNT_KIND",
            "FK_PLATFORM_ADMIN_ASSIGNMENT_USER",
            "CK_PLATFORM_ADMIN_ASSIGNMENT_INACTIVATION",
            "FK_MISSION_REGION",
            "FK_MISSION_REWARD_COUPON_POLICY",
            "CK_MISSION_CONDITION_TYPE",
            "CK_MISSION_REQUIRED_VISIT_COUNT",
            "CK_MISSION_STATUS",
            "CK_MISSION_STATUS_TIMESTAMPS",
            "FK_MISSION_TARGET_CONTENT_MISSION",
            "FK_MISSION_TARGET_CONTENT_CONTENT",
            "FK_STAMPBOOK_REGION",
            "FK_STAMPBOOK_REWARD_COUPON_POLICY",
            "CK_STAMPBOOK_STATUS_TIMESTAMPS",
            "FK_STAMPBOOK_CONTENT_STAMPBOOK",
            "FK_STAMPBOOK_CONTENT_CONTENT",
            "PK_STAMPBOOK_PROGRESS",
            "UK_STAMPBOOK_PROGRESS_STAMPBOOK_USER",
            "FK_STAMPBOOK_PROGRESS_STAMPBOOK",
            "FK_STAMPBOOK_PROGRESS_USER",
            "CK_STAMPBOOK_PROGRESS_STATUS",
            "CK_STAMPBOOK_PROGRESS_STATUS_COMPLETED_AT",
            "PK_STAMPBOOK_REWARD_GRANT",
            "UK_STAMPBOOK_REWARD_GRANT_PROGRESS",
            "FK_STAMPBOOK_REWARD_GRANT_PROGRESS",
            "FK_STAMPBOOK_REWARD_GRANT_COUPON_POLICY",
            "PK_MISSION_PARTICIPATION",
            "UK_MISSION_PARTICIPATION_MISSION_USER",
            "FK_MISSION_PARTICIPATION_MISSION",
            "FK_MISSION_PARTICIPATION_USER",
            "CK_MISSION_PARTICIPATION_STATUS",
            "CK_MISSION_PARTICIPATION_STATUS_COMPLETED_AT",
            "PK_MISSION_PROGRESS",
            "FK_MISSION_PROGRESS_PARTICIPATION",
            "FK_MISSION_PROGRESS_VISIT",
            "FK_MISSION_PROGRESS_CONTENT",
            "PK_STAMP_EARN",
            "UK_STAMP_EARN_PROGRESS_VISIT",
            "UK_STAMP_EARN_PROGRESS_CONTENT",
            "FK_STAMP_EARN_PROGRESS",
            "FK_STAMP_EARN_VISIT",
            "FK_STAMP_EARN_CONTENT",
            "PK_MISSION_REWARD_CLAIM",
            "UK_MISSION_REWARD_CLAIM_PARTICIPATION",
            "FK_MISSION_REWARD_CLAIM_PARTICIPATION",
            "FK_MISSION_REWARD_CLAIM_COUPON_POLICY",
            "PK_COUPON",
            "FK_COUPON_POLICY",
            "FK_COUPON_USER",
            "CK_COUPON_STATUS",
            "PK_COUPON_ISSUANCE",
            "UK_COUPON_ISSUANCE_COUPON",
            "UK_COUPON_ISSUANCE_IDENTITY_HASH",
            "UK_COUPON_ISSUANCE_MISSION_REWARD_CLAIM",
            "UK_COUPON_ISSUANCE_STAMPBOOK_REWARD_GRANT",
            "FK_COUPON_ISSUANCE_COUPON",
            "FK_COUPON_ISSUANCE_COUPON_POLICY",
            "FK_COUPON_ISSUANCE_RECIPIENT_USER",
            "FK_COUPON_ISSUANCE_VISIT",
            "FK_COUPON_ISSUANCE_MISSION_REWARD_CLAIM",
            "FK_COUPON_ISSUANCE_STAMPBOOK_REWARD_GRANT",
            "CK_COUPON_ISSUANCE_EXACTLY_ONE_SOURCE",
            "PK_COUPON_STATUS_HISTORY",
            "FK_COUPON_STATUS_HISTORY_COUPON",
            "CK_COUPON_STATUS_HISTORY_PREVIOUS_STATUS",
            "CK_COUPON_STATUS_HISTORY_NEXT_STATUS",
            "PK_RESERVATION_PRICE_SNAPSHOT",
            "UK_RESERVATION_PRICE_SNAPSHOT_HOLD",
            "UK_RESERVATION_PRICE_SNAPSHOT_ID_COUPON",
            "FK_RESERVATION_PRICE_SNAPSHOT_HOLD",
            "FK_RESERVATION_PRICE_SNAPSHOT_COUPON",
            "CK_RESERVATION_PRICE_SNAPSHOT_AMOUNT",
            "PK_PAYMENT",
            "UK_PAYMENT_ORDER",
            "UK_PAYMENT_PORTONE_PAYMENT",
            "UK_PAYMENT_RESERVATION",
            "UK_PAYMENT_PENDING_HOLD",
            "FK_PAYMENT_HOLD",
            "FK_PAYMENT_RESERVATION_PRICE_SNAPSHOT",
            "FK_PAYMENT_RESERVATION",
            "CK_PAYMENT_STATUS",
            "CK_PAYMENT_FINALIZED_AT",
            "PK_PAYMENT_IDEMPOTENCY",
            "UK_PAYMENT_IDEMPOTENCY_ACTOR_OPERATION_KEY",
            "UK_PAYMENT_IDEMPOTENCY_PAYMENT",
            "FK_PAYMENT_IDEMPOTENCY_PAYMENT",
            "UK_PAYMENT_IDEMPOTENCY_RESERVATION",
            "FK_PAYMENT_IDEMPOTENCY_RESERVATION",
            "CK_PAYMENT_IDEMPOTENCY_OPERATION",
            "CK_PAYMENT_IDEMPOTENCY_STATUS",
            "CK_PAYMENT_IDEMPOTENCY_RESULT",
            "PK_PAYMENT_VERIFICATION",
            "FK_PAYMENT_VERIFICATION_PAYMENT",
            "PK_PAYMENT_WEBHOOK",
            "UK_PAYMENT_WEBHOOK_PROVIDER_EVENT",
            "FK_PAYMENT_WEBHOOK_PAYMENT",
            "PK_PAYMENT_DISCREPANCY",
            "UK_PAYMENT_DISCREPANCY_PAYMENT",
            "FK_PAYMENT_DISCREPANCY_PAYMENT",
            "PK_PAYMENT_DISCREPANCY_ACTION",
            "FK_PAYMENT_DISCREPANCY_ACTION_DISCREPANCY",
            "PK_REFUND",
            "UK_REFUND_PAYMENT",
            "FK_REFUND_PAYMENT",
            "CK_REFUND_AMOUNT",
            "CK_REFUND_STATUS",
            "PK_REFUND_ATTEMPT",
            "UK_REFUND_ATTEMPT_REFUND_NO",
            "FK_REFUND_ATTEMPT_REFUND",
            "CK_REFUND_ATTEMPT_NO",
            "CK_REFUND_ATTEMPT_INITIATOR_KIND",
            "CK_REFUND_ATTEMPT_OUTCOME_KIND",
            "CK_REFUND_ATTEMPT_FAILURE_REASON_CODE",
            "CK_REFUND_ATTEMPT_OUTCOME_VALUES",
            "PK_COUPON_REDEMPTION",
            "UK_COUPON_REDEMPTION_RESERVATION",
            "UK_COUPON_REDEMPTION_SNAPSHOT",
            "UK_COUPON_REDEMPTION_CONFIRMED_COUPON",
            "FK_COUPON_REDEMPTION_COUPON",
            "FK_COUPON_REDEMPTION_SNAPSHOT_COUPON",
            "FK_COUPON_REDEMPTION_RESERVATION",
            "CK_COUPON_REDEMPTION_STATUS",
            "CK_COUPON_REDEMPTION_REVERSED_AT"
        );
        assertThat(appUserColumnNames).contains("ACCOUNT_KIND");
        assertThat(contentColumnNames).contains(
            "REPRESENTATIVE_IMAGE_OBJECT_ID",
            "REPRESENTATIVE_IMAGE_ASSIGNED_AT",
            "RESERVATION_PRICE"
        );
        assertThat(contentRevisionColumnNames).contains(
            "CANDIDATE_IMAGE_OBJECT_ID",
            "CANDIDATE_IMAGE_ASSIGNED_AT",
            "PUBLISH_AT",
            "RESERVATION_PRICE"
        );
        assertThat(contentSessionColumnNames).contains(
            "REVIEWED_AT",
            "REVIEWED_BY_USER_ID",
            "REJECT_REASON"
        );
        assertThat(capacityHoldIndexNames).contains("IDX_CAPACITY_HOLD_STATUS_EXPIRES_AT");
        assertThat(userRoleAssignmentColumnNames).contains(
            "ROLE_ASSIGNMENT_ID",
            "STATUS",
            "REVOKED_AT",
            "REVOKE_REASON_CODE",
            "ACTIVE_USER_ID"
        );
        assertThat(userRoleAssignmentIndexNames).contains(
            "UK_USER_ROLE_ASSIGNMENT_ACTIVE_USER_ROLE",
            "IDX_USER_ROLE_ASSIGNMENT_REGION_STATUS"
        );
        assertThat(imageObjectColumnNames).contains(
            "CREATED_BY_USER_ID",
            "REGION_ID",
            "UPLOAD_EXPIRES_AT",
            "LINKED_AT"
        );
    }
}
