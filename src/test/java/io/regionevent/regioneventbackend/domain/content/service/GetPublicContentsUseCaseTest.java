package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPublicContentsUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;

    private final RegionService regionService = mock(RegionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetPublicContentsUseCase useCase = new GetPublicContentsUseCase(
        regionService,
        contentService,
        representativeImageViewUrlService
    );

    @Test
    void get_공개_대상이_없으면_빈_목록을_반환한다() {
        PublicContentSearchCondition condition = condition();
        when(contentService.findPublicContents(REGION_ID, null, null)).thenReturn(List.of());

        PublicContentListResult result = useCase.get(condition);

        assertThat(result.contents()).isEmpty();
        verify(regionService).findPublicRegion(REGION_ID);
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_대표_이미지가_해당_지역에_속하면_공개_목록을_반환한다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        PublicContentProjection projection = new PublicContentProjection(
            CONTENT_ID,
            ContentType.EVENT_EXPERIENCE,
            "지역 축제",
            "김해시",
            imageObject,
            Instant.parse("2026-08-05T00:00:00Z"),
            true
        );
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
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(false);
        PublicContentProjection projection = new PublicContentProjection(
            CONTENT_ID,
            ContentType.EVENT_EXPERIENCE,
            "지역 축제",
            "김해시",
            imageObject,
            Instant.parse("2026-08-05T00:00:00Z"),
            true
        );
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

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
