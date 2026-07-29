package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RepresentativeImageReferenceMigrationTest {

    @Test
    void V1_대표_이미지_연결_데이터를_콘텐츠_루트_FK로_이관한다() {
        DriverManagerDataSource dataSource = createDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Instant assignedAt = Instant.parse("2026-08-01T00:00:00Z");

        migrateToV1(dataSource);

        insertV1RepresentativeImageData(jdbcTemplate, assignedAt, 2L);

        migrateToLatest(dataSource);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT representative_image_object_id FROM content WHERE content_id = 1",
            Long.class
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT representative_image_assigned_at FROM content WHERE content_id = 1",
            Timestamp.class
        ).toInstant()).isEqualTo(assignedAt);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT candidate_image_object_id FROM content_revision WHERE content_revision_id = 1",
            Long.class
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT candidate_image_assigned_at FROM content_revision WHERE content_revision_id = 1",
            Timestamp.class
        ).toInstant()).isEqualTo(assignedAt);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'PUBLIC' AND table_name = 'CONTENT_REPRESENTATIVE_IMAGE'",
            Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'PUBLIC' AND table_name = 'CONTENT_REVISION_REPRESENTATIVE_IMAGE'",
            Integer.class
        )).isZero();
    }

    @Test
    void V1_두_연결_테이블의_동일_이미지_객체는_이관을_중단한다() {
        DriverManagerDataSource dataSource = createDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Instant assignedAt = Instant.parse("2026-08-01T00:00:00Z");

        migrateToV1(dataSource);
        insertV1RepresentativeImageData(jdbcTemplate, assignedAt, 1L);

        assertThatThrownBy(() -> migrateToLatest(dataSource))
            .isInstanceOf(FlywayException.class);

        assertThat(tableExists(jdbcTemplate, "CONTENT_REPRESENTATIVE_IMAGE")).isTrue();
        assertThat(tableExists(jdbcTemplate, "CONTENT_REVISION_REPRESENTATIVE_IMAGE")).isTrue();
        assertThat(columnExists(jdbcTemplate, "CONTENT", "REPRESENTATIVE_IMAGE_OBJECT_ID")).isFalse();
        assertThat(columnExists(jdbcTemplate, "CONTENT_REVISION", "CANDIDATE_IMAGE_OBJECT_ID")).isFalse();
    }

    private DriverManagerDataSource createDataSource() {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:representative-image-migration-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
    }

    private void migrateToV1(DriverManagerDataSource dataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("1"))
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

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND table_name = ?",
            Integer.class,
            tableName
        );

        return tableCount != null && tableCount > 0;
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer columnCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name = ?
                  AND column_name = ?
                """,
            Integer.class,
            tableName,
            columnName
        );

        return columnCount != null && columnCount > 0;
    }

    private void insertV1RepresentativeImageData(
        JdbcTemplate jdbcTemplate,
        Instant assignedAt,
        Long candidateImageObjectId
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
                VALUES (1, 'GIMHAE', '김해시', TRUE, ?, ?)
                """,
            Timestamp.from(assignedAt),
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO app_user (user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at)
                VALUES (1, 'operator@example.com', 'hashed-password', '운영자', '010-1234-5678', 'ACTIVE', ?, ?)
                """,
            Timestamp.from(assignedAt),
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO image_object (
                    image_object_id, object_key, media_type, byte_size, checksum, lifecycle_status,
                    delete_attempt_count, last_delete_attempted_at, created_at
                )
                VALUES (1, 'content/current.webp', 'image/webp', 1, 'sha256:current', 'ACTIVE', 0, NULL, ?)
                """,
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO image_object (
                    image_object_id, object_key, media_type, byte_size, checksum, lifecycle_status,
                    delete_attempt_count, last_delete_attempted_at, created_at
                )
                VALUES (2, 'content/candidate.webp', 'image/webp', 1, 'sha256:candidate', 'ACTIVE', 0, NULL, ?)
                """,
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content (
                    content_id, region_id, operator_id, content_type, status, version_no, title, description,
                    location_text, operating_hours_text, contact_text, precautions, age_requirement, materials,
                    cancellation_policy_text, publish_at, deleted_at, created_at, updated_at
                )
                VALUES (
                    1, 1, 1, 'EVENT_EXPERIENCE', 'APPROVED', 0, '김해 가야 문화 체험', '설명',
                    '김해문화의전당', '10:00~18:00', '055-123-4567', '안전 수칙', '만 7세 이상', '편한 복장',
                    '시작 하루 전까지 취소', ?, NULL, ?, ?
                )
                """,
            Timestamp.from(assignedAt),
            Timestamp.from(assignedAt),
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content_revision (
                    content_revision_id, content_id, revision_no, base_content_version, editor_user_id, status,
                    title, description, location_text, operating_hours_text, contact_text, precautions,
                    age_requirement, materials, cancellation_policy_text, submitted_at, reviewed_at,
                    reviewed_by_user_id, review_reason, withdrawn_at, withdrawn_by_user_id, withdrawal_reason, created_at
                )
                VALUES (
                    1, 1, 1, 0, 1, 'EDIT_REQUESTED', '김해 가야 문화 체험 수정', '수정 설명',
                    '김해문화의전당', '10:00~18:00', '055-123-4567', '안전 수칙', '만 7세 이상', '편한 복장',
                    '시작 하루 전까지 취소', ?, NULL, NULL, NULL, NULL, NULL, NULL, ?
                )
                """,
            Timestamp.from(assignedAt),
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content_representative_image (content_id, image_object_id, assigned_at)
                VALUES (1, 1, ?)
                """,
            Timestamp.from(assignedAt)
        );
        jdbcTemplate.update(
            """
                INSERT INTO content_revision_representative_image (content_revision_id, image_object_id, assigned_at)
                VALUES (1, ?, ?)
                """,
            candidateImageObjectId,
            Timestamp.from(assignedAt)
        );
    }
}
