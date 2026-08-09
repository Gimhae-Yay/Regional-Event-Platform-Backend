package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class MissionDefinitionMigrationTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void 미션_정의_마이그레이션은_조건_상태_FK와_복합_식별_제약을_적용한다() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(createMigratedDataSource());
        insertRegion(jdbcTemplate);
        insertContent(jdbcTemplate);
        insertMissionRewardCouponPolicy(jdbcTemplate);
        insertMission(jdbcTemplate, 1L, "CONTENT_SET", null, "DRAFT", null, null);
        insertMissionTargetContent(jdbcTemplate, 1L, 1L);
        insertMission(jdbcTemplate, 7L, "VISIT_COUNT", 1, "PENDING_REVIEW", null, null);
        insertMission(jdbcTemplate, 8L, "VISIT_COUNT", 1, "PUBLISHED", PUBLISHED_AT, null);
        insertMission(jdbcTemplate, 9L, "VISIT_COUNT", 1, "ENDED", PUBLISHED_AT, ENDED_AT);

        Integer targetContentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mission_target_content WHERE mission_id = 1",
            Integer.class
        );

        assertThat(targetContentCount).isEqualTo(1);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            2L,
            "VISIT_COUNT",
            null,
            "DRAFT",
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            3L,
            "CONTENT_SET",
            1,
            "DRAFT",
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            4L,
            "VISIT_COUNT",
            1,
            "PUBLISHED",
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            5L,
            "UNKNOWN",
            1,
            "DRAFT",
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            6L,
            "VISIT_COUNT",
            1,
            "DRAFT",
            null,
            null,
            999L
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            10L,
            "VISIT_COUNT",
            1,
            "DRAFT",
            PUBLISHED_AT,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMission(
            jdbcTemplate,
            11L,
            "VISIT_COUNT",
            1,
            "PENDING_REVIEW",
            null,
            ENDED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMissionTargetContent(jdbcTemplate, 1L, 1L))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMissionTargetContent(jdbcTemplate, 999L, 1L))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMissionTargetContent(jdbcTemplate, 1L, 999L))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SingleConnectionDataSource createMigratedDataSource() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:mission-definition-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            true
        );

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        return dataSource;
    }

    private void insertRegion(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
                INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
                VALUES (1, 'GIMHAE', '김해시', TRUE, ?, ?)
                """,
            Timestamp.from(ISSUE_STARTS_AT),
            Timestamp.from(ISSUE_STARTS_AT)
        );
    }

    private void insertContent(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
                INSERT INTO app_user (
                    user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at
                )
                VALUES (1, 'operator@example.com', 'hashed-password', '콘텐츠 운영자', '010-1234-5678', 'ACTIVE', ?, ?)
                """,
            Timestamp.from(ISSUE_STARTS_AT),
            Timestamp.from(ISSUE_STARTS_AT)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content (
                    content_id, region_id, operator_id, content_type, status, version_no, title, description,
                    location_text, operating_hours_text, contact_text, precautions, age_requirement, materials,
                    cancellation_policy_text, publish_at, created_at, updated_at
                )
                VALUES (
                    1, 1, 1, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, '김해 가야 문화 체험', '김해 가야 문화를 체험합니다.',
                    '김해문화의전당', '10:00~18:00', '055-1234-5678', '안전요원의 안내를 따릅니다.', '만 7세 이상', '편한 복장',
                    '시작 하루 전까지 취소할 수 있습니다.', ?, ?, ?
                )
                """,
            Timestamp.from(ISSUE_STARTS_AT),
            Timestamp.from(ISSUE_STARTS_AT),
            Timestamp.from(ISSUE_STARTS_AT)
        );
    }

    private void insertMissionRewardCouponPolicy(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
                INSERT INTO coupon_policy (
                    coupon_policy_id, content_id, region_id, name, issuance_type, discount_amount,
                    minimum_payment_amount, valid_days, issue_starts_at, issue_ends_at, issued_count, status
                )
                VALUES (1, 1, 1, '미션 완료 보상', 'MISSION_REWARD', 3_000, 10_000, 30, ?, ?, 0, 'DRAFT')
                """,
            Timestamp.from(ISSUE_STARTS_AT),
            Timestamp.from(ISSUE_ENDS_AT)
        );
    }

    private void insertMission(
        JdbcTemplate jdbcTemplate,
        Long missionId,
        String conditionType,
        Integer requiredVisitCount,
        String status,
        Instant publishedAt,
        Instant endedAt
    ) {
        insertMission(
            jdbcTemplate,
            missionId,
            conditionType,
            requiredVisitCount,
            status,
            publishedAt,
            endedAt,
            1L
        );
    }

    private void insertMission(
        JdbcTemplate jdbcTemplate,
        Long missionId,
        String conditionType,
        Integer requiredVisitCount,
        String status,
        Instant publishedAt,
        Instant endedAt,
        Long rewardCouponPolicyId
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO mission (
                    mission_id, region_id, condition_type, required_visit_count, reward_coupon_policy_id,
                    status, ends_at, published_at, ended_at
                )
                VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?)
                """,
            missionId,
            conditionType,
            requiredVisitCount,
            rewardCouponPolicyId,
            status,
            Timestamp.from(ENDS_AT),
            publishedAt == null ? null : Timestamp.from(publishedAt),
            endedAt == null ? null : Timestamp.from(endedAt)
        );
    }

    private void insertMissionTargetContent(
        JdbcTemplate jdbcTemplate,
        Long missionId,
        Long contentId
    ) {
        jdbcTemplate.update(
            "INSERT INTO mission_target_content (mission_id, content_id) VALUES (?, ?)",
            missionId,
            contentId
        );
    }
}
