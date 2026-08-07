package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentRepositoryTest {

    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ContentRepositoryTest(
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 콘텐츠의_필수_속성과_승인_상태를_저장한다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Instant publishAt = Instant.parse("2026-08-01T00:00:00Z");

        Content content = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.APPROVED, publishAt)
        );

        Content foundContent = contentRepository.findById(content.getContentId()).orElseThrow();

        assertThat(foundContent.getContentType()).isEqualTo(ContentType.EVENT_EXPERIENCE);
        assertThat(foundContent.getStatus()).isEqualTo(ContentStatus.APPROVED);
        assertThat(foundContent.getVersionNo()).isZero();
        assertThat(foundContent.getTitle()).isEqualTo("김해 가야 문화 체험");
        assertThat(foundContent.getDescription()).isEqualTo("김해 가야 문화를 체험하는 행사입니다.");
        assertThat(foundContent.getLocationText()).isEqualTo("김해문화의전당");
        assertThat(foundContent.getOperatingHoursText()).isEqualTo("매일 10:00~18:00");
        assertThat(foundContent.getContactText()).isEqualTo("055-123-4567");
        assertThat(foundContent.getPrecautions()).isEqualTo("안전요원의 안내를 따라주세요.");
        assertThat(foundContent.getAgeRequirement()).isEqualTo("만 7세 이상");
        assertThat(foundContent.getMaterials()).isEqualTo("편한 복장");
        assertThat(foundContent.getCancellationPolicyText()).isEqualTo("시작 하루 전까지 취소할 수 있습니다.");
        assertThat(foundContent.getPublishAt()).isEqualTo(publishAt);
        assertThat(foundContent.getDeletedAt()).isNull();
        assertThat(foundContent.getCreatedAt()).isNotNull();
        assertThat(foundContent.getUpdatedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(ContentStatus.class)
    void 콘텐츠_상태_카탈로그를_문자열로_매핑한다(ContentStatus status) {
        Region region = saveRegion();
        AppUser operator = saveOperator();

        Content content = contentRepository.saveAndFlush(
            newContent(region, operator, status, Instant.parse("2026-08-01T00:00:00Z"))
        );

        assertThat(contentRepository.findById(content.getContentId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(
        value = ContentStatus.class,
        names = {"PENDING", "APPROVED"}
    )
    void 공개_전_상태의_콘텐츠만_소프트_삭제한다(ContentStatus status) {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Content content = contentRepository.saveAndFlush(
            newContent(region, operator, status, Instant.parse("2026-08-01T00:00:00Z"))
        );

        content.softDelete();
        contentRepository.flush();

        assertThat(content.getDeletedAt()).isNotNull();
        assertThat(contentRepository.findById(content.getContentId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void 공개된_콘텐츠는_소프트_삭제할_수_없다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Content content = newContent(
            region,
            operator,
            ContentStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z")
        );

        assertThatThrownBy(content::softDelete)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 공개되고_삭제되지_않은_콘텐츠만_존재로_판정한다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Content publishedContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.PUBLISHED, Instant.parse("2026-08-01T00:00:00Z"))
        );
        Content pendingContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.PENDING, Instant.parse("2026-08-01T00:00:00Z"))
        );

        assertThat(contentRepository.existsByContentIdAndStatusAndDeletedAtIsNull(
            publishedContent.getContentId(),
            ContentStatus.PUBLISHED
        )).isTrue();
        assertThat(contentRepository.existsByContentIdAndStatusAndDeletedAtIsNull(
            pendingContent.getContentId(),
            ContentStatus.PUBLISHED
        )).isFalse();
    }

    @Test
    void 심사_대기_콘텐츠만_반려_상태로_조건부_전이한다() {
        Content content = contentRepository.saveAndFlush(
            newContent(saveRegion(), saveOperator(), ContentStatus.PENDING, Instant.parse("2026-08-01T00:00:00Z"))
        );
        Instant rejectedAt = Instant.parse("2026-08-02T00:00:00Z");

        int updatedCount = contentRepository.rejectPendingByContentId(
            content.getContentId(),
            rejectedAt
        );

        entityManager.clear();
        Content rejectedContent = contentRepository.findById(content.getContentId()).orElseThrow();
        assertThat(updatedCount).isEqualTo(1);
        assertThat(rejectedContent.getStatus()).isEqualTo(ContentStatus.REJECTED);
        assertThat(rejectedContent.getVersionNo()).isEqualTo(1);
        assertThat(rejectedContent.getUpdatedAt()).isEqualTo(rejectedAt);
        assertThat(contentRepository.rejectPendingByContentId(content.getContentId(), rejectedAt)).isZero();
    }

    @Test
    void JDBC로_행사_체험_외_콘텐츠_유형을_저장할_수_없다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();

        Instant now = Instant.parse("2026-08-01T00:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO content (
                    region_id,
                    operator_id,
                    content_type,
                    status,
                    version_no,
                    title,
                    description,
                    location_text,
                    operating_hours_text,
                    contact_text,
                    precautions,
                    age_requirement,
                    materials,
                    cancellation_policy_text,
                    publish_at,
                    deleted_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            region.getRegionId(),
            operator.getUserId(),
            "OTHER",
            "PENDING",
            0,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Timestamp.from(now),
            null,
            Timestamp.from(now),
            Timestamp.from(now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void JDBC로_상태_카탈로그_외_상태로_변경할_수_없다() {
        Content content = contentRepository.saveAndFlush(
            newContent(saveRegion(), saveOperator(), ContentStatus.PENDING, Instant.parse("2026-08-01T00:00:00Z"))
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE content SET status = ? WHERE content_id = ?",
            "DRAFT",
            content.getContentId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void JDBC로_공개된_콘텐츠에_소프트_삭제_시각을_설정할_수_없다() {
        Content content = contentRepository.saveAndFlush(
            newContent(saveRegion(), saveOperator(), ContentStatus.PUBLISHED, Instant.parse("2026-08-01T00:00:00Z"))
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE content SET deleted_at = ? WHERE content_id = ?",
            Timestamp.from(Instant.parse("2026-08-02T00:00:00Z")),
            content.getContentId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 지역과_운영자를_지연_로딩으로_매핑한다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Content content = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.PENDING, Instant.parse("2026-08-01T00:00:00Z"))
        );
        entityManager.clear();

        Content foundContent = contentRepository.findById(content.getContentId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundContent, "region")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundContent, "operator")).isFalse();
        assertThat(foundContent.getRegion().getRegionId()).isEqualTo(region.getRegionId());
        assertThat(foundContent.getOperator().getUserId()).isEqualTo(operator.getUserId());
    }

    @Test
    void 소프트_삭제된_콘텐츠도_지역과_함께_조회한다() {
        Region region = saveRegion();
        Content content = contentRepository.saveAndFlush(
            newContent(
                region,
                saveOperator(),
                ContentStatus.PENDING,
                Instant.parse("2026-08-01T00:00:00Z")
            )
        );
        content.softDelete();
        contentRepository.flush();
        Long contentId = content.getContentId();
        entityManager.clear();

        Content foundContent = contentRepository.findByContentId(contentId).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundContent.getDeletedAt()).isNotNull();
        assertThat(persistenceUnitUtil.isLoaded(foundContent, "region")).isTrue();
        assertThat(foundContent.getRegion().getRegionId()).isEqualTo(region.getRegionId());
    }

    @Test
    void 자동_공개_후보는_승인되고_삭제되지_않았으며_공개_예정_시각이_지난_콘텐츠만_조회한다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Instant now = Instant.now();
        Content pastApprovedContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.APPROVED, now.minusSeconds(60))
        );
        contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.APPROVED, now.plusSeconds(3_600))
        );
        contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.PENDING, now.minusSeconds(60))
        );
        Content deletedApprovedContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.APPROVED, now.minusSeconds(60))
        );
        deletedApprovedContent.softDelete();
        contentRepository.flush();

        assertThat(contentRepository.findApprovedPublicationCandidateIds())
            .containsExactly(pastApprovedContent.getContentId());
    }

    @Test
    void 자동_공개_대상은_잠금_시점에도_상태와_삭제와_공개_예정_시각을_다시_확인한다() {
        Region region = saveRegion();
        AppUser operator = saveOperator();
        Content approvedContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.APPROVED, Instant.now().minusSeconds(60))
        );
        Content futureContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.APPROVED, Instant.now().plusSeconds(3_600))
        );
        Content pendingContent = contentRepository.saveAndFlush(
            newContent(region, operator, ContentStatus.PENDING, Instant.now().minusSeconds(60))
        );
        entityManager.clear();

        assertThat(contentRepository.findApprovedPublicationTargetForUpdate(approvedContent.getContentId()))
            .isPresent();
        assertThat(contentRepository.findApprovedPublicationTargetForUpdate(futureContent.getContentId()))
            .isEmpty();
        assertThat(contentRepository.findApprovedPublicationTargetForUpdate(pendingContent.getContentId()))
            .isEmpty();
    }

    private Region saveRegion() {
        return regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
    }

    private AppUser saveOperator() {
        return appUserRepository.saveAndFlush(
            new AppUser(
                "operator@example.com",
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            )
        );
    }

    private Content newContent(
        Region region,
        AppUser operator,
        ContentStatus status,
        Instant publishAt
    ) {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            publishAt
        );
    }

}
