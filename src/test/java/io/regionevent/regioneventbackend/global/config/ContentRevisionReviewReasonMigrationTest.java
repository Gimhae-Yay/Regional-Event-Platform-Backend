package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ContentRevisionReviewReasonMigrationTest {

    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void V12_승인_사유를_정규화한_뒤_V13_검토_제약을_적용한다() {
        DriverManagerDataSource dataSource = createDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        migrateToV12(dataSource);
        insertV12ReviewData(jdbcTemplate);

        migrateToLatest(dataSource);

        assertThat(findReviewReason(jdbcTemplate, 1L)).isNull();
        assertThat(findReviewReason(jdbcTemplate, 2L)).isEqualTo("기존 반려 사유");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE content_revision SET review_reason = '승인 사유' WHERE content_revision_id = 1"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private DriverManagerDataSource createDataSource() {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:content-revision-review-reason-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
    }

    private void migrateToV12(DriverManagerDataSource dataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("12"))
            .load()
            .migrate();
    }

    private void migrateToLatest(DriverManagerDataSource dataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private String findReviewReason(JdbcTemplate jdbcTemplate, Long revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT review_reason FROM content_revision WHERE content_revision_id = ?",
            String.class,
            revisionId
        );
    }

    private void insertV12ReviewData(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
            """
                INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
                VALUES (1, 'GIMHAE', '김해시', TRUE, ?, ?)
                """,
            Timestamp.from(REVIEWED_AT),
            Timestamp.from(REVIEWED_AT)
        );
        jdbcTemplate.update(
            """
                INSERT INTO app_user (
                    user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at
                )
                VALUES (1, 'reviewer@example.com', 'hashed-password', '검토자', '010-1234-5678', 'ACTIVE', ?, ?)
                """,
            Timestamp.from(REVIEWED_AT),
            Timestamp.from(REVIEWED_AT)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content (
                    content_id, region_id, operator_id, content_type, status, version_no, title, description,
                    location_text, operating_hours_text, contact_text, precautions, age_requirement, materials,
                    cancellation_policy_text, publish_at, deleted_at, created_at, updated_at
                )
                VALUES (
                    1, 1, 1, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, '원본 제목', '원본 설명',
                    '원본 장소', '10:00~18:00', '055-1234-5678', '원본 주의사항', '만 7세 이상', '편한 복장',
                    '원본 취소 정책', ?, NULL, ?, ?
                )
                """,
            Timestamp.from(REVIEWED_AT),
            Timestamp.from(REVIEWED_AT),
            Timestamp.from(REVIEWED_AT)
        );
        insertRevision(jdbcTemplate, 1L, 1, "EDIT_APPROVED", "기존 승인 사유");
        insertRevision(jdbcTemplate, 2L, 2, "EDIT_REJECTED", "기존 반려 사유");
    }

    private void insertRevision(
        JdbcTemplate jdbcTemplate,
        Long revisionId,
        int revisionNo,
        String status,
        String reviewReason
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO content_revision (
                    content_revision_id, content_id, revision_no, base_content_version, editor_user_id, status,
                    title, description, location_text, operating_hours_text, contact_text, precautions,
                    age_requirement, materials, cancellation_policy_text, submitted_at, reviewed_at,
                    reviewed_by_user_id, review_reason, withdrawn_at, withdrawn_by_user_id, withdrawal_reason,
                    created_at
                )
                VALUES (
                    ?, 1, ?, 1, 1, ?, '후보 제목', '후보 설명', '후보 장소', '10:00~18:00',
                    '055-9876-5432', '후보 주의사항', '만 8세 이상', '운동화', '후보 취소 정책', ?, ?, 1, ?,
                    NULL, NULL, NULL, ?
                )
                """,
            revisionId,
            revisionNo,
            status,
            Timestamp.from(REVIEWED_AT),
            Timestamp.from(REVIEWED_AT),
            reviewReason,
            Timestamp.from(REVIEWED_AT)
        );
    }
}
