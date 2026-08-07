package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.RegionHomeContentSessionVerificationProjection;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentCache;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentCacheAside;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentStaticInfo;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetRegionHomeUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final int VERSION_NO = 3;
    private static final Instant BASE_TIME = Instant.parse("2026-08-05T00:00:00Z");

    private final RegionService regionService = mock(RegionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final PublicRegionCache publicRegionCache = mock(PublicRegionCache.class);
    private final PublicContentCache publicContentCache = mock(PublicContentCache.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetRegionHomeUseCase useCase = new GetRegionHomeUseCase(
        regionService,
        contentService,
        new PublicRegionCacheAside(publicRegionCache),
        new PublicContentCacheAside(publicContentCache),
        representativeImageViewUrlService
    );

    @Test
    void get_진행_회차를_우선하고_표시_회차와_목록_정렬을_결정한다() {
        ImageObject imageObject = imageObject();
        givenPublicRegion();
        when(contentService.findRegionHomeContentSessionVerifications(REGION_ID)).thenReturn(List.of(
            verification(200L, 1001L, BASE_TIME.minusSeconds(3_600), BASE_TIME.plusSeconds(7_200), true, true, imageObject),
            verification(200L, 1002L, BASE_TIME.minusSeconds(1_800), BASE_TIME.plusSeconds(3_600), true, true, imageObject),
            verification(200L, 1003L, BASE_TIME.plusSeconds(7_200), BASE_TIME.plusSeconds(10_800), true, false, imageObject),
            verification(202L, 2002L, BASE_TIME.plusSeconds(7_200), BASE_TIME.plusSeconds(10_800), false, false, imageObject),
            verification(201L, 2001L, BASE_TIME.plusSeconds(7_200), BASE_TIME.plusSeconds(10_800), true, false, imageObject)
        ));
        givenContentCacheHit();
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        RegionHomeResult result = useCase.get(REGION_ID);

        assertThat(result.ongoingContents())
            .extracting(RegionHomeResult.Content::contentId)
            .containsExactly(200L);
        assertThat(result.ongoingContents().getFirst().displaySession().sessionId()).isEqualTo(1002L);
        assertThat(result.upcomingContents())
            .extracting(RegionHomeResult.Content::contentId)
            .containsExactly(201L, 202L);
        assertThat(result.upcomingContents().getFirst().displaySession().sessionId()).isEqualTo(2001L);
        verify(contentService, never()).findPublicContentStaticInfo(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void get_진행_목록을_고정_정렬로_최대_10건까지만_반환한다() {
        ImageObject imageObject = imageObject();
        givenPublicRegion();
        List<RegionHomeContentSessionVerificationProjection> verifications = java.util.stream.LongStream
            .rangeClosed(1L, 11L)
            .mapToObj(contentId -> verification(
                contentId,
                contentId,
                BASE_TIME.minusSeconds(3_600),
                BASE_TIME.plusSeconds(3_600),
                true,
                true,
                imageObject
            ))
            .toList();
        when(contentService.findRegionHomeContentSessionVerifications(REGION_ID)).thenReturn(verifications);
        givenContentCacheHit();
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        RegionHomeResult result = useCase.get(REGION_ID);

        assertThat(result.ongoingContents())
            .extracting(RegionHomeResult.Content::contentId)
            .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(result.upcomingContents()).isEmpty();
    }

    @Test
    void get_콘텐츠_캐시의_버전이_검증_값과_다르면_MySQL_정적_정보를_사용한다() {
        ImageObject imageObject = imageObject();
        RegionHomeContentSessionVerificationProjection projection = verification(
            200L,
            1001L,
            BASE_TIME.plusSeconds(3_600),
            BASE_TIME.plusSeconds(7_200),
            true,
            false,
            imageObject
        );
        givenPublicRegion();
        when(contentService.findRegionHomeContentSessionVerifications(REGION_ID)).thenReturn(List.of(projection));
        when(publicContentCache.findContent(REGION_ID, 200L, VERSION_NO))
            .thenReturn(Optional.of(staticInfo(200L, VERSION_NO - 1, "이전 제목")));
        PublicContentStaticInfo currentStaticInfo = staticInfo(200L, VERSION_NO, "현재 제목");
        when(contentService.findPublicContentStaticInfo(REGION_ID, 200L, VERSION_NO))
            .thenReturn(currentStaticInfo);
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        RegionHomeResult result = useCase.get(REGION_ID);

        assertThat(result.upcomingContents().getFirst().title()).isEqualTo("현재 제목");
        verify(contentService).findPublicContentStaticInfo(REGION_ID, 200L, VERSION_NO);
        verify(publicContentCache).saveContent(currentStaticInfo);
    }

    @Test
    void get_대표_이미지_정합성이_깨지면_정상_콘텐츠로_대체하지_않는다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(false);
        givenPublicRegion();
        when(contentService.findRegionHomeContentSessionVerifications(REGION_ID)).thenReturn(List.of(
            verification(200L, 1001L, BASE_TIME.plusSeconds(3_600), BASE_TIME.plusSeconds(7_200), true, false, imageObject)
        ));
        givenContentCacheHit();

        assertThatThrownBy(() -> useCase.get(REGION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private void givenPublicRegion() {
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(new Region("GIMHAE", "김해시", true));
        when(publicRegionCache.findRegion(REGION_ID))
            .thenReturn(Optional.of(new PublicRegionStaticInfo(REGION_ID, "GIMHAE", "김해시")));
    }

    private void givenContentCacheHit() {
        when(publicContentCache.findContent(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(invocation -> Optional.of(staticInfo(
                invocation.getArgument(1),
                invocation.getArgument(2),
                "콘텐츠 " + invocation.getArgument(1)
            )));
    }

    private static RegionHomeContentSessionVerificationProjection verification(
        Long contentId,
        Long sessionId,
        Instant startsAt,
        Instant endsAt,
        boolean reservationAvailable,
        boolean ongoing,
        ImageObject imageObject
    ) {
        return new RegionHomeContentSessionVerificationProjection(
            REGION_ID,
            contentId,
            VERSION_NO,
            imageObject,
            BASE_TIME,
            sessionId,
            startsAt,
            endsAt,
            4,
            reservationAvailable,
            ongoing
        );
    }

    private static PublicContentStaticInfo staticInfo(
        Long contentId,
        int versionNo,
        String title
    ) {
        return new PublicContentStaticInfo(
            REGION_ID,
            contentId,
            versionNo,
            ContentType.EVENT_EXPERIENCE,
            title,
            "콘텐츠 설명",
            "김해시",
            "10:00~18:00",
            "우천 시 취소",
            "전 연령",
            "없음",
            "당일 취소 불가"
        );
    }

    private static ImageObject imageObject() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        return imageObject;
    }

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
