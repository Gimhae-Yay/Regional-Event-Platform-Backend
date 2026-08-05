package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyContentUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long CONTENT_ID = 200L;
    private static final Long REGION_ID = 10L;
    private static final Instant IMAGE_ASSIGNED_AT = Instant.parse("2026-08-05T00:00:00Z");

    private final ContentService contentService = mock(ContentService.class);
    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final ContentLogRepository contentLogRepository = mock(ContentLogRepository.class);
    private final GetMyContentUseCase useCase = new GetMyContentUseCase(
        contentService,
        operatorAuthorizationService,
        representativeImageViewUrlService,
        contentLogRepository
    );

    @Test
    void get_반려_콘텐츠면_대표_이미지와_최신_반려_사유를_반환한다() {
        Content content = contentWithImage(ContentStatus.REJECTED);
        ImageObject imageObject = content.getRepresentativeImageObject();
        ContentLog rejectionLog = mock(ContentLog.class);
        when(contentService.findMyContentDetail(CONTENT_ID)).thenReturn(content);
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());
        when(contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            CONTENT_ID,
            io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus.REJECTED
        )).thenReturn(Optional.of(rejectionLog));
        when(rejectionLog.getReason()).thenReturn("운영 시간 근거가 부족합니다.");

        MyContentDetailResult result = useCase.get(USER_ID, CONTENT_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.representativeImageUrl()).isEqualTo("https://image.example/content.webp");
        assertThat(result.rejectionReason()).isEqualTo("운영 시간 근거가 부족합니다.");
        verify(operatorAuthorizationService).authorizeOwnedContent(
            USER_ID,
            content.getOperator(),
            content.getRegion()
        );
    }

    @Test
    void get_대표_이미지_연결_정보가_없으면_서버_오류를_반환한다() {
        Content content = contentWithImage(ContentStatus.PUBLISHED);
        when(content.getRepresentativeImageAssignedAt()).thenReturn(null);
        when(contentService.findMyContentDetail(CONTENT_ID)).thenReturn(content);

        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService, contentLogRepository);
    }

    @Test
    void get_반려_로그가_없으면_서버_오류를_반환한다() {
        Content content = contentWithImage(ContentStatus.REJECTED);
        ImageObject imageObject = content.getRepresentativeImageObject();
        when(contentService.findMyContentDetail(CONTENT_ID)).thenReturn(content);
        when(representativeImageViewUrlService.createViewUrl(imageObject)).thenReturn(viewUrl());
        when(contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            CONTENT_ID,
            io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus.REJECTED
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    private Content contentWithImage(ContentStatus status) {
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        Region region = mock(Region.class);
        ImageObject imageObject = mock(ImageObject.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getStatus()).thenReturn(status);
        when(content.getOperator()).thenReturn(operator);
        when(content.getRegion()).thenReturn(region);
        when(content.getRepresentativeImageObject()).thenReturn(imageObject);
        when(content.getRepresentativeImageAssignedAt()).thenReturn(IMAGE_ASSIGNED_AT);
        return content;
    }

    private static RepresentativeImageViewUrl viewUrl() {
        return new RepresentativeImageViewUrl(
            "https://image.example/content.webp",
            Instant.parse("2026-08-05T01:00:00Z")
        );
    }
}
