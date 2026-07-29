package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentRepresentativeImageMappingTest {

    private final ContentRepository contentRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    ContentRepresentativeImageMappingTest(
        ContentRepository contentRepository,
        ImageObjectRepository imageObjectRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.contentRepository = contentRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 콘텐츠가_대표_이미지_객체를_직접_참조한다() {
        Content content = saveContent("김해 가야 문화 체험");
        ImageObject imageObject = saveImageObject("content/representative.webp");
        Instant assignedAt = Instant.parse("2026-08-01T00:00:00Z");

        content.assignRepresentativeImage(imageObject, assignedAt);
        contentRepository.flush();
        entityManager.clear();

        Content foundContent = contentRepository.findById(content.getContentId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundContent.getRepresentativeImageAssignedAt()).isEqualTo(assignedAt);
        assertThat(persistenceUnitUtil.isLoaded(foundContent, "representativeImageObject")).isFalse();
        assertThat(foundContent.getRepresentativeImageObject().getImageObjectId()).isEqualTo(imageObject.getImageObjectId());
    }

    @Test
    void 같은_이미지_객체를_두_콘텐츠의_대표_이미지로_연결할_수_없다() {
        ImageObject imageObject = saveImageObject("content/unique.webp");
        Content firstContent = saveContent("첫 번째 콘텐츠");
        Content secondContent = saveContent("두 번째 콘텐츠");

        firstContent.assignRepresentativeImage(imageObject, Instant.parse("2026-08-01T00:00:00Z"));
        contentRepository.flush();

        secondContent.assignRepresentativeImage(imageObject, Instant.parse("2026-08-02T00:00:00Z"));

        assertThatThrownBy(contentRepository::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Content saveContent(String title) {
        String uniqueKey = Integer.toHexString(title.hashCode());
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE-" + uniqueKey, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(
            new AppUser(
                "operator-" + uniqueKey + "@example.com",
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            )
        );

        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.APPROVED,
            title,
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
    }

    private ImageObject saveImageObject(String objectKey) {
        return imageObjectRepository.saveAndFlush(new ImageObject(
            objectKey,
            "image/webp",
            1L,
            "sha256:" + objectKey,
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        ));
    }
}
