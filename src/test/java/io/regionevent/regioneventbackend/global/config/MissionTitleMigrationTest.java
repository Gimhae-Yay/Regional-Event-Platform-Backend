package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class MissionTitleMigrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void V45는_빈_미션_테이블에_NOT_NULL_VARCHAR_255_제목을_추가하고_중복을_허용한다() throws SQLException {
        SingleConnectionDataSource dataSource = createDataSource();
        migrateTo(dataSource, "44");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertRequiredRows(jdbcTemplate);

        migrateToLatest(dataSource);

        Map<String, Object> titleColumn = jdbcTemplate.queryForMap(
            """
                SELECT is_nullable, character_maximum_length, column_default
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = 'MISSION'
                  AND column_name = 'TITLE'
                """
        );

        assertThat(titleColumn.get("IS_NULLABLE")).isEqualTo("NO");
        assertThat(((Number) titleColumn.get("CHARACTER_MAXIMUM_LENGTH")).longValue()).isEqualTo(255L);
        assertThat(titleColumn.get("COLUMN_DEFAULT")).isNull();
        assertThat(countUniqueTitleIndexes(dataSource)).isZero();

        assertThatThrownBy(() -> insertMissionWithoutTitle(jdbcTemplate, 1L))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        insertMission(jdbcTemplate, 1L, "중복 미션");
        insertMission(jdbcTemplate, 2L, "중복 미션");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mission WHERE title = '중복 미션'",
            Integer.class
        )).isEqualTo(2);
    }

    private SingleConnectionDataSource createDataSource() {
        return new SingleConnectionDataSource(
            "jdbc:h2:mem:mission-title-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            true
        );
    }

    private void migrateTo(
        SingleConnectionDataSource dataSource,
        String version
    ) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate();
    }

    private void migrateToLatest(SingleConnectionDataSource dataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private void insertRequiredRows(JdbcTemplate jdbcTemplate) {
        Timestamp createdAt = Timestamp.from(CREATED_AT);
        jdbcTemplate.update(
            """
                INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
                VALUES (1, 'GIMHAE', '김해시', TRUE, ?, ?)
                """,
            createdAt,
            createdAt
        );
        jdbcTemplate.update(
            """
                INSERT INTO app_user (
                    user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at
                )
                VALUES (1, 'operator@example.com', 'hashed-password', '운영자', '010-1234-5678', 'ACTIVE', ?, ?)
                """,
            createdAt,
            createdAt
        );
        jdbcTemplate.update(
            """
                INSERT INTO content (
                    content_id, region_id, operator_id, content_type, status, version_no, title, description,
                    location_text, operating_hours_text, contact_text, precautions, age_requirement, materials,
                    cancellation_policy_text, publish_at, created_at, updated_at
                )
                VALUES (
                    1, 1, 1, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, '보상 콘텐츠', '설명',
                    '김해', '10:00~18:00', '055-1234-5678', '주의', '전체', '없음', '취소 가능', ?, ?, ?
                )
                """,
            createdAt,
            createdAt,
            createdAt
        );
        jdbcTemplate.update(
            """
                INSERT INTO coupon_policy (
                    coupon_policy_id, content_id, region_id, name, issuance_type, discount_amount,
                    minimum_payment_amount, valid_days, issue_starts_at, issue_ends_at, issued_count, status
                )
                VALUES (1, 1, 1, '미션 보상', 'MISSION_REWARD', 1000, 5000, 30, ?, ?, 0, 'DRAFT')
                """,
            createdAt,
            Timestamp.from(ISSUE_ENDS_AT)
        );
    }

    private void insertMission(
        JdbcTemplate jdbcTemplate,
        Long missionId,
        String title
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO mission (
                    mission_id, title, region_id, condition_type, required_visit_count,
                    reward_coupon_policy_id, status, ends_at
                )
                VALUES (?, ?, 1, 'VISIT_COUNT', 1, 1, 'DRAFT', ?)
                """,
            missionId,
            title,
            Timestamp.from(MISSION_ENDS_AT)
        );
    }

    private void insertMissionWithoutTitle(
        JdbcTemplate jdbcTemplate,
        Long missionId
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO mission (
                    mission_id, region_id, condition_type, required_visit_count,
                    reward_coupon_policy_id, status, ends_at
                )
                VALUES (?, 1, 'VISIT_COUNT', 1, 1, 'DRAFT', ?)
                """,
            missionId,
            Timestamp.from(MISSION_ENDS_AT)
        );
    }

    private int countUniqueTitleIndexes(SingleConnectionDataSource dataSource) throws SQLException {
        int count = 0;
        try (ResultSet indexes = dataSource.getConnection()
            .getMetaData()
            .getIndexInfo(null, null, "MISSION", true, false)) {
            while (indexes.next()) {
                if ("TITLE".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    count++;
                }
            }
        }
        return count;
    }
}
