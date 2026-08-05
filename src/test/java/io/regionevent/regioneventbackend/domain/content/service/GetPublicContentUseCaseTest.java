package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentDetailVerificationProjection;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPublicContentUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final int VERSION_NO = 3;

    private final ContentService contentService = mock(ContentService.class);
    private final PublicContentCache publicContentCache = mock(PublicContentCache.class);
    private final PublicContentCacheAside publicContentCacheAside =
        new PublicContentCacheAside(publicContentCache);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetPublicContentUseCase useCase = new GetPublicContentUseCase(
        contentService,
        publicContentCacheAside,
        representativeImageViewUrlService
    );

    @Test
    void get_캐시_적중이면_정적_표시_정보를_MySQL에서_추가_조회하지_않는다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(contentService.findPublicContentDetailVerification(CONTENT_ID)).thenReturn(verification(imageObject));
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(Optional.of(staticInfo("캐시 제목")));
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        PublicContentDetailResult result = useCase.get(CONTENT_ID);

        assertThat(result.title()).isEqualTo("캐시 제목");
        assertThat(result.contactText()).isEqualTo("055-000-0000");
        verify(contentService, never()).findPublicContentStaticInfo(REGION_ID, CONTENT_ID, VERSION_NO);
    }

    @Test
    void get_캐시_미스이면_정적_표시_정보를_조회해_상세_응답을_조립한다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(contentService.findPublicContentDetailVerification(CONTENT_ID)).thenReturn(verification(imageObject));
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO)).thenReturn(Optional.empty());
        when(contentService.findPublicContentStaticInfo(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(staticInfo("MySQL 제목"));
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());

        PublicContentDetailResult result = useCase.get(CONTENT_ID);

        assertThat(result.title()).isEqualTo("MySQL 제목");
        verify(contentService).findPublicContentStaticInfo(REGION_ID, CONTENT_ID, VERSION_NO);
        verify(publicContentCache).saveContent(staticInfo("MySQL 제목"));
    }

    @Test
    void get_대표_이미지가_없으면_서버_오류를_반환한다() {
        when(contentService.findPublicContentDetailVerification(CONTENT_ID)).thenReturn(
            new PublicContentDetailVerificationProjection(
                REGION_ID,
                CONTENT_ID,
                VERSION_NO,
                null,
                Instant.parse("2026-08-05T00:00:00Z"),
                "055-000-0000"
            )
        );
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(Optional.of(staticInfo("캐시 제목")));

        assertThatThrownBy(() -> useCase.get(CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_대표_이미지_연결_시각이_없으면_서버_오류를_반환한다() {
        ImageObject imageObject = mock(ImageObject.class);
        when(contentService.findPublicContentDetailVerification(CONTENT_ID)).thenReturn(
            new PublicContentDetailVerificationProjection(
                REGION_ID,
                CONTENT_ID,
                VERSION_NO,
                imageObject,
                null,
                "055-000-0000"
            )
        );
        when(publicContentCache.findContent(REGION_ID, CONTENT_ID, VERSION_NO))
            .thenReturn(Optional.of(staticInfo("캐시 제목")));

        assertThatThrownBy(() -> useCase.get(CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private static PublicContentDetailVerificationProjection verification(ImageObject imageObject) {
        return new PublicContentDetailVerificationProjection(
            REGION_ID,
            CONTENT_ID,
            VERSION_NO,
            imageObject,
            Instant.parse("2026-08-05T00:00:00Z"),
            "055-000-0000"
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

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
