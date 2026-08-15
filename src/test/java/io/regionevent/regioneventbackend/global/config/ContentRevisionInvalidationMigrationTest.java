package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ContentRevisionInvalidationMigrationTest {

    private static final Instant SUSPENDED_AT = Instant.parse("2026-08-15T09:30:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void V40_종결_콘텐츠의_활성_수정본을_한번만_무효화한다() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:content-revision-invalidation-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateToVersion(dataSource, "39");
        insertPreexistingTerminationData(jdbcTemplate);

        migrateToLatest(dataSource);

        assertRevision(
            jdbcTemplate,
            201L,
            "EDIT_INVALIDATED",
            SUSPENDED_AT,
            2L,
            "CONTENT_SUSPENDED"
        );
        assertRevision(
            jdbcTemplate,
            202L,
            "EDIT_INVALIDATED",
            ENDED_AT,
            null,
            "CONTENT_ENDED"
        );

        assertThat(migrateToLatest(dataSource)).isZero();
        assertRevision(
            jdbcTemplate,
            201L,
            "EDIT_INVALIDATED",
            SUSPENDED_AT,
            2L,
            "CONTENT_SUSPENDED"
        );
    }

    private int migrateToLatest(DriverManagerDataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
            .migrationsExecuted;
    }

    private void migrateToVersion(DriverManagerDataSource dataSource, String version) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate();
    }

    private void insertPreexistingTerminationData(JdbcTemplate jdbcTemplate) {
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        jdbcTemplate.update(
            """
                INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
                VALUES (1, 'GIMHAE', '김해시', TRUE, ?, ?)
                """,
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO app_user (
                    user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at
                )
                VALUES (1, 'operator@example.com', 'hashed-password', '운영자', '010-1234-5678', 'ACTIVE', ?, ?),
                       (2, 'admin@example.com', 'hashed-password', '지역 관리자', '010-1234-5679', 'ACTIVE', ?, ?)
                """,
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        );
        insertTerminatedContent(jdbcTemplate, 101L, "SUSPENDED", 2L, SUSPENDED_AT, "운영 중단");
        insertTerminatedContent(jdbcTemplate, 102L, "ENDED", null, ENDED_AT, null);
        insertEditRequestedRevision(jdbcTemplate, 201L, 101L, createdAt);
        insertEditRequestedRevision(jdbcTemplate, 202L, 102L, createdAt);
    }

    private void insertTerminatedContent(
        JdbcTemplate jdbcTemplate,
        Long contentId,
        String status,
        Long actorId,
        Instant terminatedAt,
        String reason
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO content (
                    content_id, region_id, operator_id, content_type, status, version_no, reservation_price,
                    title, description, location_text, operating_hours_text, contact_text, precautions,
                    age_requirement, materials, cancellation_policy_text, publish_at, deleted_at, created_at, updated_at
                )
                VALUES (?, 1, 1, 'EVENT_EXPERIENCE', ?, 0, 0, '콘텐츠', '설명', '김해', '10:00~18:00',
                        '055-1234-5678', '안전 수칙', '만 7세 이상', '편한 복장', '시작 하루 전까지 취소',
                        ?, NULL, ?, ?)
                """,
            contentId,
            status,
            Timestamp.from(terminatedAt.minusSeconds(86_400)),
            Timestamp.from(terminatedAt.minusSeconds(86_400)),
            Timestamp.from(terminatedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content_log (content_id, actor_id, status, reason, date)
                VALUES (?, ?, ?, ?, ?)
                """,
            contentId,
            actorId,
            status,
            reason,
            Timestamp.from(terminatedAt)
        );
    }

    private void insertEditRequestedRevision(
        JdbcTemplate jdbcTemplate,
        Long revisionId,
        Long contentId,
        Instant submittedAt
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO content_revision (
                    content_revision_id, content_id, revision_no, base_content_version, editor_user_id, status,
                    title, description, location_text, operating_hours_text, contact_text, precautions,
                    age_requirement, materials, cancellation_policy_text, reservation_price, publish_at,
                    submitted_at, created_at
                )
                VALUES (?, ?, 1, 0, 1, 'EDIT_REQUESTED', '수정 콘텐츠', '수정 설명', '김해', '10:00~18:00',
                        '055-1234-5678', '안전 수칙', '만 7세 이상', '편한 복장', '시작 하루 전까지 취소',
                        0, NULL, ?, ?)
                """,
            revisionId,
            contentId,
            Timestamp.from(submittedAt),
            Timestamp.from(submittedAt)
        );
    }

    private void assertRevision(
        JdbcTemplate jdbcTemplate,
        Long revisionId,
        String expectedStatus,
        Instant expectedInvalidatedAt,
        Long expectedInvalidatedByUserId,
        String expectedReason
    ) {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM content_revision WHERE content_revision_id = ?",
            String.class,
            revisionId
        )).isEqualTo(expectedStatus);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT invalidated_at FROM content_revision WHERE content_revision_id = ?",
            Timestamp.class,
            revisionId
        ).toInstant()).isEqualTo(expectedInvalidatedAt);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT invalidated_by_user_id FROM content_revision WHERE content_revision_id = ?",
            Long.class,
            revisionId
        )).isEqualTo(expectedInvalidatedByUserId);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT invalidation_reason FROM content_revision WHERE content_revision_id = ?",
            String.class,
            revisionId
        )).isEqualTo(expectedReason);
    }
}
