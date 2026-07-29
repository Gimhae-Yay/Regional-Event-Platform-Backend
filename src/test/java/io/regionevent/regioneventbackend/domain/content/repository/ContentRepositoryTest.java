package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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

    @Autowired
    ContentRepositoryTest(
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
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
