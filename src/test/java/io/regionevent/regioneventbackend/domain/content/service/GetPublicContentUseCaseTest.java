package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPublicContentUseCaseTest {

    private static final Long CONTENT_ID = 200L;

    private final ContentService contentService = mock(ContentService.class);
    private final PublicCatalogCacheAside publicCatalogCacheAside = mock(PublicCatalogCacheAside.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetPublicContentUseCase useCase = new GetPublicContentUseCase(
        contentService,
        publicCatalogCacheAside,
        representativeImageViewUrlService
    );

    @Test
    void get_대표_이미지가_연결된_공개_콘텐츠를_반환한다() {
        Content content = contentWithImage();
        ImageObject imageObject = content.getRepresentativeImageObject();
        when(contentService.findPublicContent(CONTENT_ID)).thenReturn(content);
        returnsMysqlSourceFromCacheAside();
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        PublicContentDetailResult result = useCase.get(CONTENT_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.title()).isEqualTo("지역 축제");
        assertThat(result.representativeImageUrl()).isEqualTo("https://image.example/content.webp");
    }

    @Test
    void get_대표_이미지가_없으면_서버_오류를_반환한다() {
        Content content = contentWithImage();
        when(content.getRepresentativeImageObject()).thenReturn(null);
        when(contentService.findPublicContent(CONTENT_ID)).thenReturn(content);
        returnsMysqlSourceFromCacheAside();

        assertThatThrownBy(() -> useCase.get(CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_대표_이미지_연결_시각이_없으면_서버_오류를_반환한다() {
        Content content = contentWithImage();
        when(content.getRepresentativeImageAssignedAt()).thenReturn(null);
        when(contentService.findPublicContent(CONTENT_ID)).thenReturn(content);
        returnsMysqlSourceFromCacheAside();

        assertThatThrownBy(() -> useCase.get(CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private Content contentWithImage() {
        Content content = mock(Content.class);
        ImageObject imageObject = mock(ImageObject.class);
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(10L);
        when(content.getRegion()).thenReturn(region);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getVersionNo()).thenReturn(3);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getTitle()).thenReturn("지역 축제");
        when(content.getDescription()).thenReturn("축제 설명");
        when(content.getLocationText()).thenReturn("김해시");
        when(content.getOperatingHoursText()).thenReturn("10:00~18:00");
        when(content.getContactText()).thenReturn("055-000-0000");
        when(content.getPrecautions()).thenReturn("우천 시 취소");
        when(content.getAgeRequirement()).thenReturn("전 연령");
        when(content.getMaterials()).thenReturn("없음");
        when(content.getCancellationPolicyText()).thenReturn("당일 취소 불가");
        when(content.getRepresentativeImageObject()).thenReturn(imageObject);
        when(content.getRepresentativeImageAssignedAt()).thenReturn(Instant.parse("2026-08-05T00:00:00Z"));
        return content;
    }

    private void returnsMysqlSourceFromCacheAside() {
        when(publicCatalogCacheAside.resolveContent(any(PublicContentStaticInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
