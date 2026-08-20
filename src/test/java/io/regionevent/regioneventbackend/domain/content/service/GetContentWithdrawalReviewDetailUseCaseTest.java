package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ExtendWith(OutputCaptureExtension.class)
class GetContentWithdrawalReviewDetailUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long REQUEST_ID = 7001L;
    private static final Long CONTENT_ID = 101L;
    private static final Long REQUESTER_ID = 20L;
    private static final Instant PUBLISH_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-16T04:00:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentWithdrawalRequestService contentWithdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final GetContentWithdrawalReviewDetailUseCase useCase =
        new GetContentWithdrawalReviewDetailUseCase(
            regionAdminAuthorizationService,
            contentWithdrawalRequestService
        );

    @Test
    void 권한_확인_후_대기_요청의_콘텐츠와_요청자를_조립한다() {
        ContentWithdrawalRequest request = validRequest(REGION_ID, requester());
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(request);

        ContentWithdrawalReviewDetailResult result = useCase.get(USER_ID, REQUEST_ID);

        assertThat(result.withdrawalRequestId()).isEqualTo(REQUEST_ID);
        assertThat(result.status()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
        assertThat(result.content().contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.content().contentType()).isEqualTo(ContentType.EVENT_EXPERIENCE);
        assertThat(result.content().title()).isEqualTo("김해 가야문화 체험");
        assertThat(result.content().status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(result.content().publishAt()).isEqualTo(PUBLISH_AT);
        assertThat(result.requester().userId()).isEqualTo(REQUESTER_ID);
        assertThat(result.requester().name()).isEqualTo("김운영");
        assertThat(result.requestReason()).isEqualTo("운영 계획 변경");
        assertThat(result.requestedAt()).isEqualTo(REQUESTED_AT);
        InOrder inOrder = inOrder(
            regionAdminAuthorizationService,
            contentWithdrawalRequestService
        );
        inOrder.verify(regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
        inOrder.verify(contentWithdrawalRequestService).findReviewDetailById(REQUEST_ID);
    }

    @Test
    void 권한_확인이_실패하면_요청을_조회하거나_입력_ID를_로그에_남기지_않는다(
        CapturedOutput output
    ) {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertError(ErrorCode.FORBIDDEN, () -> useCase.get(USER_ID, REQUEST_ID));

        verifyNoInteractions(contentWithdrawalRequestService);
        assertThat(output).contains(
            "Content withdrawal review detail queried.",
            "withdrawalRequestId=null",
            "resultCode=FORBIDDEN"
        ).doesNotContain("withdrawalRequestId=7001");
    }

    @Test
    void 다른_담당_지역의_요청은_FORBIDDEN으로_거부한다() {
        ContentWithdrawalRequest request = validRequest(20L, requester());
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID))
            .thenReturn(request);

        assertError(ErrorCode.FORBIDDEN, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @Test
    void 요청이_없으면_NOT_FOUND를_반환한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertError(ErrorCode.NOT_FOUND, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @Test
    void 조회된_요청_ID가_입력과_다르면_정합성_오류를_반환한다() {
        ContentWithdrawalRequest request = validRequest(REGION_ID, requester());
        when(request.getContentWithdrawalRequestId()).thenReturn(7002L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(request);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @ParameterizedTest
    @EnumSource(
        value = ContentWithdrawalRequestStatus.class,
        names = {"APPROVED", "REJECTED", "INVALIDATED"}
    )
    void 종결_요청은_NOT_FOUND를_반환한다(ContentWithdrawalRequestStatus status) {
        ContentWithdrawalRequest request = validRequest(REGION_ID, requester());
        when(request.getStatus()).thenReturn(status);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(request);

        assertError(ErrorCode.NOT_FOUND, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @Test
    void 콘텐츠_연결이나_지역_식별자가_없으면_정합성_오류를_반환한다() {
        ContentWithdrawalRequest missingContent = validRequest(REGION_ID, requester());
        when(missingContent.getContent()).thenReturn(null);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID))
            .thenReturn(missingContent);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> useCase.get(USER_ID, REQUEST_ID));

        ContentWithdrawalRequest missingRegion = validRequest(REGION_ID, requester());
        when(missingRegion.getContent().getRegion()).thenReturn(null);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID))
            .thenReturn(missingRegion);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @Test
    void 콘텐츠가_삭제됐거나_PUBLISHED가_아니면_정합성_오류를_반환한다() {
        ContentWithdrawalRequest deleted = validRequest(REGION_ID, requester());
        when(deleted.getContent().getDeletedAt()).thenReturn(REQUESTED_AT);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(deleted);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> useCase.get(USER_ID, REQUEST_ID));

        ContentWithdrawalRequest approved = validRequest(REGION_ID, requester());
        when(approved.getContent().getStatus()).thenReturn(ContentStatus.APPROVED);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(approved);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @Test
    void 요청자_연결이_없으면_다른_관계로_추론하지_않고_null을_반환한다() {
        ContentWithdrawalRequest request = validRequest(REGION_ID, null);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(request);

        ContentWithdrawalReviewDetailResult result = useCase.get(USER_ID, REQUEST_ID);

        assertThat(result.requester()).isNull();
        verify(request, never()).getIdempotencyKeyHash();
        verify(request, never()).getReviewedBy();
        verify(request, never()).getInvalidatedBy();
    }

    @Test
    void 연결된_요청자의_필수값이_없으면_정합성_오류를_반환한다() {
        AppUser requester = requester();
        when(requester.getName()).thenReturn(" ");
        ContentWithdrawalRequest request = validRequest(REGION_ID, requester);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(request);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR, () -> useCase.get(USER_ID, REQUEST_ID));
    }

    @Test
    void 구조화_로그에는_식별자와_결과만_남기고_개인정보와_사유를_남기지_않는다(
        CapturedOutput output
    ) {
        ContentWithdrawalRequest request = validRequest(REGION_ID, requester());
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findReviewDetailById(REQUEST_ID)).thenReturn(request);

        useCase.get(USER_ID, REQUEST_ID);

        assertThat(output).contains(
            "Content withdrawal review detail queried.",
            "regionId=10",
            "withdrawalRequestId=7001",
            "resultCode=SUCCESS"
        ).doesNotContain(
            "김운영",
            "운영 계획 변경",
            "idempotency",
            "reviewedBy",
            "invalidatedBy"
        );
    }

    private ContentWithdrawalRequest validRequest(Long regionId, AppUser requester) {
        ContentWithdrawalRequest request = mock(ContentWithdrawalRequest.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        when(request.getContentWithdrawalRequestId()).thenReturn(REQUEST_ID);
        when(request.getStatus()).thenReturn(ContentWithdrawalRequestStatus.PENDING);
        when(request.getContent()).thenReturn(content);
        when(request.getRequestedBy()).thenReturn(requester);
        when(request.getRequestReason()).thenReturn("운영 계획 변경");
        when(request.getRequestedAt()).thenReturn(REQUESTED_AT);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getTitle()).thenReturn("김해 가야문화 체험");
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getPublishAt()).thenReturn(PUBLISH_AT);
        when(region.getRegionId()).thenReturn(regionId);
        return request;
    }

    private AppUser requester() {
        AppUser requester = mock(AppUser.class);
        when(requester.getUserId()).thenReturn(REQUESTER_ID);
        when(requester.getName()).thenReturn("김운영");
        return requester;
    }

    private void assertError(ErrorCode expected, ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected)
            );
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void run();
    }
}
