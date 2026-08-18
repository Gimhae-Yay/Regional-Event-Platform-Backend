package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ExtendWith(OutputCaptureExtension.class)
class GetPendingContentWithdrawalRequestsUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-16T04:00:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentWithdrawalRequestService contentWithdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final GetPendingContentWithdrawalRequestsUseCase useCase =
        new GetPendingContentWithdrawalRequestsUseCase(
            regionAdminAuthorizationService,
            contentWithdrawalRequestService
        );

    @Test
    void 대기_요청을_정상_요청자와_null_요청자로_매핑한다() {
        ContentWithdrawalRequest linkedRequester = request(101L, 201L, false);
        ContentWithdrawalRequest unlinkedRequester = request(102L, 202L, true);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findPendingPublishedByRegionId(REGION_ID))
            .thenReturn(List.of(linkedRequester, unlinkedRequester));

        PendingContentWithdrawalRequestListResult result = useCase.get(USER_ID, "PENDING");

        assertThat(result.withdrawalRequests()).hasSize(2);
        assertThat(result.withdrawalRequests().getFirst().requester())
            .isEqualTo(new PendingContentWithdrawalRequestListResult.Requester(301L, "요청자 이름"));
        assertThat(result.withdrawalRequests().getLast().requester()).isNull();
        assertThat(result.withdrawalRequests())
            .extracting(PendingContentWithdrawalRequestListResult.WithdrawalRequest::requestedAt)
            .containsOnly(REQUESTED_AT);
    }

    @Test
    void 조회_결과가_없으면_빈_목록을_반환한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findPendingPublishedByRegionId(REGION_ID))
            .thenReturn(List.of());

        PendingContentWithdrawalRequestListResult result = useCase.get(USER_ID, "PENDING");

        assertThat(result.withdrawalRequests()).isEmpty();
    }

    @Test
    void 권한을_확인한_뒤_status를_검증한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenReturn(REGION_ID);

        assertThatThrownBy(() -> useCase.get(USER_ID, ""))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        InOrder order = inOrder(
            regionAdminAuthorizationService,
            contentWithdrawalRequestService
        );
        order.verify(regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
        order.verifyNoMoreInteractions();
    }

    @Test
    void 권한이_없으면_잘못된_status보다_권한오류를_우선한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.get(USER_ID, "APPROVED"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verifyNoInteractions(contentWithdrawalRequestService);
    }

    @Test
    void 필수_관계나_필드가_계약과_다르면_내부오류로_거부한다() {
        ContentWithdrawalRequest request = request(101L, 201L, false);
        when(request.getRequestedAt()).thenReturn(null);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findPendingPublishedByRegionId(REGION_ID))
            .thenReturn(List.of(request));

        assertThatThrownBy(() -> useCase.get(USER_ID, "PENDING"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    @Test
    void 성공과_실패_로그는_허용된_필드만_포함하고_개인정보를_노출하지_않는다(
        CapturedOutput output
    ) {
        ContentWithdrawalRequest request = request(101L, 201L, false);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenReturn(REGION_ID);
        when(contentWithdrawalRequestService.findPendingPublishedByRegionId(REGION_ID))
            .thenReturn(List.of(request));

        useCase.get(USER_ID, "PENDING");
        assertThatThrownBy(() -> useCase.get(USER_ID, null))
            .isInstanceOf(BusinessException.class);

        assertThat(output.getOut())
            .contains(
                "requestId=",
                "regionId=10, resultCount=1, resultCode=SUCCESS",
                "regionId=10, resultCount=0, resultCode=INVALID_INPUT"
            )
            .doesNotContain(
                "요청자 이름",
                "requestReason",
                "idempotencyKeyHash",
                "reviewedAt",
                "invalidatedAt"
            );
    }

    private ContentWithdrawalRequest request(
        Long withdrawalRequestId,
        Long contentId,
        boolean requesterUnlinked
    ) {
        ContentWithdrawalRequest request = mock(ContentWithdrawalRequest.class);
        Content content = mock(Content.class);
        when(request.getContentWithdrawalRequestId()).thenReturn(withdrawalRequestId);
        when(request.getStatus()).thenReturn(ContentWithdrawalRequestStatus.PENDING);
        when(request.getRequestedAt()).thenReturn(REQUESTED_AT);
        when(request.getContent()).thenReturn(content);
        when(content.getContentId()).thenReturn(contentId);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getTitle()).thenReturn("콘텐츠 제목");
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getDeletedAt()).thenReturn(null);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        if (requesterUnlinked) {
            when(request.getRequestedBy()).thenReturn(null);
        } else {
            AppUser requester = mock(AppUser.class);
            when(requester.getUserId()).thenReturn(301L);
            when(requester.getName()).thenReturn("요청자 이름");
            when(request.getRequestedBy()).thenReturn(requester);
        }
        return request;
    }
}
