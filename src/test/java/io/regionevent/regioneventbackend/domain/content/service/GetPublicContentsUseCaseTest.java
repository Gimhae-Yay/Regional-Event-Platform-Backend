package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentProjection;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPublicContentsUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;

    private final RegionService regionService = mock(RegionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final PublicContentCacheAside publicContentCacheAside = mock(PublicContentCacheAside.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetPublicContentsUseCase useCase = new GetPublicContentsUseCase(
        regionService,
        contentService,
        publicContentCacheAside,
        representativeImageViewUrlService
    );

    @Test
    void get_공개_대상이_없으면_빈_목록을_반환한다() {
        PublicContentSearchCondition condition = condition();
        Region region = publicRegion();
        returnsMysqlSourceFromCacheAside();
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(region);
        when(contentService.findPublicContents(REGION_ID, null, null)).thenReturn(List.of());

        PublicContentListResult result = useCase.get(condition);

        assertThat(result.contents()).isEmpty();
        verify(regionService).findPublicRegion(REGION_ID);
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_대표_이미지가_해당_지역에_속하면_공개_목록을_반환한다() {
        ImageObject imageObject = mock(ImageObject.class);
        Region region = publicRegion();
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        PublicContentProjection projection = projection(imageObject, true);
        returnsMysqlSourceFromCacheAside();
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(region);
        when(contentService.findPublicContents(REGION_ID, null, null)).thenReturn(List.of(projection));
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        PublicContentListResult result = useCase.get(condition());

        assertThat(result.contents()).containsExactly(new PublicContentListResult.Content(
            CONTENT_ID,
            ContentType.EVENT_EXPERIENCE,
            "지역 축제",
            "김해시",
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z"),
            true
        ));
    }

    @Test
    void get_대표_이미지의_지역이_다르면_서버_오류를_반환한다() {
        ImageObject imageObject = mock(ImageObject.class);
        Region region = publicRegion();
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(false);
        PublicContentProjection projection = projection(imageObject, true);
        returnsMysqlSourceFromCacheAside();
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(region);
        when(contentService.findPublicContents(REGION_ID, null, null)).thenReturn(List.of(projection));

        assertThatThrownBy(() -> useCase.get(condition()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private static PublicContentSearchCondition condition() {
        return new PublicContentSearchCondition(REGION_ID, null, null);
    }

    private static PublicContentProjection projection(
        ImageObject imageObject,
        boolean reservationAvailable
    ) {
        return new PublicContentProjection(
            REGION_ID,
            CONTENT_ID,
            3,
            ContentType.EVENT_EXPERIENCE,
            "지역 축제",
            "축제 설명",
            "김해시",
            "10:00~18:00",
            "우천 시 취소",
            "전 연령",
            "없음",
            "당일 취소 불가",
            imageObject,
            Instant.parse("2026-08-05T00:00:00Z"),
            reservationAvailable
        );
    }

    private static Region publicRegion() {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(region.getRegionCode()).thenReturn("GIMHAE");
        when(region.getName()).thenReturn("김해시");
        return region;
    }

    private void returnsMysqlSourceFromCacheAside() {
        when(publicContentCacheAside.resolveContent(any(PublicContentStaticInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
