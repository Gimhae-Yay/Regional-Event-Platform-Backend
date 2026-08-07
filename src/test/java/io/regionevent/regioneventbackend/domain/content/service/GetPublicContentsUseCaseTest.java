package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentListVerificationProjection;
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
    private static final int VERSION_NO = 3;

    private final RegionService regionService = mock(RegionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final PublicContentCache publicContentCache = mock(PublicContentCache.class);
    private final PublicContentCacheAside publicContentCacheAside =
        new PublicContentCacheAside(publicContentCache);
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
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(publicRegion());
        when(contentService.findPublicContentListVerifications(REGION_ID, null, null)).thenReturn(List.of());

        PublicContentListResult result = useCase.get(condition());

        assertThat(result.contents()).isEmpty();
        verify(regionService).findPublicRegion(REGION_ID);
        verifyNoInteractions(publicContentCache, representativeImageViewUrlService);
    }

    @Test
    void get_종료_뒤_캐시_삭제에_실패해도_MySQL_검증에서_목록에_포함하지_않는다() {
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(publicRegion());
        when(contentService.findPublicContentListVerifications(REGION_ID, null, null)).thenReturn(List.of());
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(Optional.of(staticInfo("오래된 캐시 제목")));

        PublicContentListResult result = useCase.get(condition());

        assertThat(result.contents()).isEmpty();
        verifyNoInteractions(publicContentCache, representativeImageViewUrlService);
    }

    @Test
    void get_캐시_적중이면_정적_표시_정보를_MySQL에서_추가_조회하지_않는다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(publicRegion());
        when(contentService.findPublicContentListVerifications(REGION_ID, null, null))
            .thenReturn(List.of(verification(imageObject, true)));
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(Optional.of(staticInfo("캐시 제목")));
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        PublicContentListResult result = useCase.get(condition());

        assertThat(result.contents()).containsExactly(new PublicContentListResult.Content(
            CONTENT_ID,
            ContentType.EVENT_EXPERIENCE,
            "캐시 제목",
            "김해시",
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z"),
            true
        ));
        verify(contentService, never()).findPublicContentStaticInfo(REGION_ID, CONTENT_ID, VERSION_NO);
    }

    @Test
    void get_캐시_미스이면_정적_표시_정보를_조회해_응답을_조립한다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(publicRegion());
        when(contentService.findPublicContentListVerifications(REGION_ID, null, null))
            .thenReturn(List.of(verification(imageObject, true)));
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO)).thenReturn(Optional.empty());
        when(contentService.findPublicContentStaticInfo(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(staticInfo("MySQL 제목"));
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        PublicContentListResult result = useCase.get(condition());

        assertThat(result.contents()).extracting(PublicContentListResult.Content::title)
            .containsExactly("MySQL 제목");
        verify(contentService).findPublicContentStaticInfo(REGION_ID, CONTENT_ID, VERSION_NO);
        verify(publicContentCache).saveContent(staticInfo("MySQL 제목"));
    }

    @Test
    void get_대표_이미지의_지역이_다르면_서버_오류를_반환한다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(false);
        when(regionService.findPublicRegion(REGION_ID)).thenReturn(publicRegion());
        when(contentService.findPublicContentListVerifications(REGION_ID, null, null))
            .thenReturn(List.of(verification(imageObject, true)));
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(Optional.of(staticInfo("캐시 제목")));

        assertThatThrownBy(() -> useCase.get(condition()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private static PublicContentSearchCondition condition() {
        return new PublicContentSearchCondition(REGION_ID, null, null);
    }

    private static PublicContentListVerificationProjection verification(
        ImageObject imageObject,
        boolean reservationAvailable
    ) {
        return new PublicContentListVerificationProjection(
            REGION_ID,
            CONTENT_ID,
            VERSION_NO,
            imageObject,
            Instant.parse("2026-08-05T00:00:00Z"),
            reservationAvailable
        );
    }

    private static PublicContentStaticInfo staticInfo(String title) {
        return new PublicContentStaticInfo(
            REGION_ID,
            CONTENT_ID,
            VERSION_NO,
            ContentType.EVENT_EXPERIENCE,
            title,
            "축제 설명",
            "김해시",
            "10:00~18:00",
            "우천 시 취소",
            "전 연령",
            "없음",
            "당일 취소 불가"
        );
    }

    private static Region publicRegion() {
        return new Region("gimhae", "김해시", true);
    }

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
