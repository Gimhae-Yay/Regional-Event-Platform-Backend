package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentListVerificationProjection;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class PublicContentRepositoryTest {

    private static final Instant SAME_PUBLISH_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;

    @Autowired
    PublicContentRepositoryTest(
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ImageObjectRepository imageObjectRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository
    ) {
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
    }

    @Test
    void 공개_콘텐츠는_예약_가능_여부를_DB_현재시각으로_계산하고_공개시각과_식별자로_정렬한다() {
        Region region = saveRegion("PUBLIC");
        AppUser operator = saveUser();
        Content first = saveContent(region, operator, "첫 번째 콘텐츠", SAME_PUBLISH_AT);
        Content second = saveContent(region, operator, "두 번째 콘텐츠", SAME_PUBLISH_AT);
        Content earlier = saveContent(
            region,
            operator,
            "이전 콘텐츠",
            SAME_PUBLISH_AT.minusSeconds(1)
        );
        saveScheduledSession(first, region, operator, Instant.now().plusSeconds(3_600), 10);
        saveScheduledSession(second, region, operator, Instant.now().plusSeconds(3_600), 0);
        saveScheduledSession(earlier, region, operator, Instant.now().minusSeconds(3_600), 10);

        List<PublicContentListVerificationProjection> results = contentRepository.findPublicContentListVerifications(
            region.getRegionId(),
            null,
            null,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );

        assertThat(results)
            .extracting(PublicContentListVerificationProjection::contentId)
            .containsExactly(second.getContentId(), first.getContentId(), earlier.getContentId());
        assertThat(results)
            .extracting(PublicContentListVerificationProjection::reservationAvailable)
            .containsExactly(false, true, false);
        assertThat(results)
            .extracting(PublicContentListVerificationProjection::regionId)
            .containsOnly(region.getRegionId());
        assertThat(results)
            .extracting(PublicContentListVerificationProjection::versionNo)
            .containsOnly(0);
    }

    @Test
    void 공개_콘텐츠_단건_조회는_공개_지역의_현재_공개본만_반환한다() {
        Region publicRegion = saveRegion("DETAIL-PUBLIC");
        Region privateRegion = regionRepository.saveAndFlush(
            new Region("REGION-DETAIL-PRIVATE", "비공개 지역", false)
        );
        AppUser operator = saveUser();
        Content publicContent = saveContent(publicRegion, operator, "공개 콘텐츠", SAME_PUBLISH_AT);
        Content privateContent = saveContent(privateRegion, operator, "비공개 콘텐츠", SAME_PUBLISH_AT);

        assertThat(contentRepository.findPublicContentByContentId(
            publicContent.getContentId(),
            ContentStatus.PUBLISHED
        )).contains(publicContent);
        assertThat(contentRepository.findPublicContentByContentId(
            privateContent.getContentId(),
            ContentStatus.PUBLISHED
        )).isEmpty();
    }

    @Test
    void 예약_가능_여부_필터는_다른_공개_조건과_함께_적용한다() {
        Region region = saveRegion("FILTER");
        AppUser operator = saveUser();
        Content reservable = saveContent(region, operator, "예약 가능", SAME_PUBLISH_AT);
        Content unavailable = saveContent(region, operator, "예약 불가", SAME_PUBLISH_AT.minusSeconds(1));
        saveScheduledSession(reservable, region, operator, Instant.now().plusSeconds(3_600), 10);
        saveScheduledSession(unavailable, region, operator, Instant.now().plusSeconds(3_600), 0);

        List<PublicContentListVerificationProjection> reservableResults = contentRepository.findPublicContentListVerifications(
            region.getRegionId(),
            ContentType.EVENT_EXPERIENCE,
            true,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
        List<PublicContentListVerificationProjection> unavailableResults = contentRepository.findPublicContentListVerifications(
            region.getRegionId(),
            ContentType.EVENT_EXPERIENCE,
            false,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );

        assertThat(reservableResults)
            .extracting(PublicContentListVerificationProjection::contentId)
            .containsExactly(reservable.getContentId());
        assertThat(unavailableResults)
            .extracting(PublicContentListVerificationProjection::contentId)
            .containsExactly(unavailable.getContentId());
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "테스트 지역", true));
    }

    private AppUser saveUser() {
        return appUserRepository.saveAndFlush(new AppUser(
            "operator-" + appUserRepository.count() + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String title,
        Instant publishAt
    ) {
        ImageObject representativeImageObject = imageObjectRepository.saveAndFlush(
            ImageObject.createUploadCandidate(
                "contents/" + imageObjectRepository.count() + ".webp",
                operator,
                region,
                "image/webp",
                524_288L,
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                Instant.now().plusSeconds(3_600)
            )
        );
        representativeImageObject.markLinked(Instant.now());
        imageObjectRepository.saveAndFlush(representativeImageObject);

        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            title,
            "설명",
            "위치",
            "운영 시간",
            "055-000-0000",
            "유의사항",
            "연령 조건",
            "준비물",
            "취소 규정",
            publishAt
        );
        content.assignRepresentativeImage(representativeImageObject, Instant.now());
        return contentRepository.saveAndFlush(content);
    }

    private void saveScheduledSession(
        Content content,
        Region region,
        AppUser reviewer,
        Instant startsAt,
        int capacity
    ) {
        int sessionCapacity = Math.max(capacity, 1);
        ContentSession contentSession = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(3_600),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(1_800),
            sessionCapacity
        );
        contentSession.approve(reviewer, Instant.now());
        ContentSession savedContentSession = contentSessionRepository.saveAndFlush(contentSession);
        if (capacity == 0) {
            contentSessionRepository.decreaseRemainingCapacityIfReservable(
                savedContentSession.getSessionId(),
                sessionCapacity,
                ContentStatus.PUBLISHED,
                ContentSessionStatus.SCHEDULED
            );
        }
    }
}
