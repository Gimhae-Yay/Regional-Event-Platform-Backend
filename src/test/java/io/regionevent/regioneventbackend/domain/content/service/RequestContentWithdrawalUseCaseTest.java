package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RequestContentWithdrawalUseCaseTest {

    private static final Long USER_ID = 10L;
    private static final Long REGION_ID = 20L;
    private static final Long CONTENT_ID = 30L;
    private static final Long REQUEST_ID = 40L;
    private static final String IDEMPOTENCY_KEY = "request-key";
    private static final String KEY_HASH = "b".repeat(64);
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-16T04:00:00Z");

    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ContentWithdrawalRequestService contentWithdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final ContentWithdrawalRequestHasher hasher = mock(ContentWithdrawalRequestHasher.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RequestContentWithdrawalUseCase useCase = new RequestContentWithdrawalUseCase(
        operatorAuthorizationService,
        regionService,
        contentService,
        contentWithdrawalRequestService,
        hasher,
        recordAuditEventUseCase
    );

    private AppUser user;
    private Region region;
    private UserRoleAssignment assignment;
    private Content content;

    @BeforeEach
    void setUp() {
        user = mock(AppUser.class);
        region = mock(Region.class);
        assignment = mock(UserRoleAssignment.class);
        content = mock(Content.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(50L);
        when(assignment.getAppUser()).thenReturn(user);
        AuthorizedOperator authorizedOperator = new AuthorizedOperator(user, region, assignment);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID))
            .thenReturn(authorizedOperator);
        when(contentService.findContentRegionId(CONTENT_ID)).thenReturn(REGION_ID);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(region);
        when(contentService.findWithdrawalRequestTargetForUpdate(CONTENT_ID)).thenReturn(content);
        when(content.isOwnedBy(USER_ID)).thenReturn(true);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(hasher.hashIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(KEY_HASH);
        when(contentWithdrawalRequestService.findByIdempotencyKeyForUpdate(CONTENT_ID, KEY_HASH))
            .thenReturn(Optional.empty());
        when(contentWithdrawalRequestService.findPendingForUpdate(CONTENT_ID))
            .thenReturn(Optional.empty());
        when(contentService.findCurrentDatabaseTime()).thenReturn(REQUESTED_AT);
    }

    @Test
    void 잠금_순서대로_새_요청과_감사를_한_번_기록한다() {
        ContentWithdrawalRequest created = withdrawalRequest("운영 계획 변경");
        when(contentWithdrawalRequestService.createPending(
            content,
            user,
            KEY_HASH,
            "운영 계획 변경",
            REQUESTED_AT
        )).thenReturn(created);
        UUID auditRequestId = UUID.randomUUID();

        RequestContentWithdrawalResult result = useCase.request(
            USER_ID,
            CONTENT_ID,
            IDEMPOTENCY_KEY,
            "  운영 계획 변경  ",
            auditRequestId
        );

        assertThat(result.withdrawalRequestId()).isEqualTo(REQUEST_ID);
        assertThat(result.status()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
        InOrder lockOrder = inOrder(
            operatorAuthorizationService,
            contentService,
            regionService,
            contentWithdrawalRequestService
        );
        lockOrder.verify(operatorAuthorizationService).requireAuthorizedOperatorForUpdate(USER_ID);
        lockOrder.verify(contentService).findContentRegionId(CONTENT_ID);
        lockOrder.verify(regionService).findRegionForUpdate(REGION_ID);
        lockOrder.verify(contentService).findWithdrawalRequestTargetForUpdate(CONTENT_ID);
        lockOrder.verify(contentWithdrawalRequestService)
            .findByIdempotencyKeyForUpdate(CONTENT_ID, KEY_HASH);
        lockOrder.verify(contentWithdrawalRequestService).findPendingForUpdate(CONTENT_ID);
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().requestId()).isEqualTo(auditRequestId);
        assertThat(auditCaptor.getValue().targetType())
            .isEqualTo(AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST);
        assertThat(auditCaptor.getValue().targetId()).isEqualTo(REQUEST_ID);
        assertThat(auditCaptor.getValue().previousState()).isNull();
        assertThat(auditCaptor.getValue().nextState()).isEqualTo("PENDING");
        assertThat(auditCaptor.getValue().reasonCode()).isEqualTo("CONTENT_WITHDRAWAL_REQUESTED");
    }

    @Test
    void 같은_키와_사유의_재시도는_콘텐츠_상태보다_먼저_최초_결과를_반환한다() {
        ContentWithdrawalRequest existing = withdrawalRequest("운영 계획 변경");
        when(contentWithdrawalRequestService.findByIdempotencyKeyForUpdate(CONTENT_ID, KEY_HASH))
            .thenReturn(Optional.of(existing));
        when(content.getStatus()).thenReturn(ContentStatus.ENDED);

        RequestContentWithdrawalResult result = useCase.request(
            USER_ID,
            CONTENT_ID,
            IDEMPOTENCY_KEY,
            "운영 계획 변경",
            UUID.randomUUID()
        );

        assertThat(result.withdrawalRequestId()).isEqualTo(REQUEST_ID);
        assertThat(result.status()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
        verify(contentWithdrawalRequestService, never()).findPendingForUpdate(CONTENT_ID);
        verify(contentWithdrawalRequestService, never()).createPending(any(), any(), any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void 같은_키의_다른_사유는_멱등_키_충돌이다() {
        ContentWithdrawalRequest existing = withdrawalRequest("기존 사유");
        when(contentWithdrawalRequestService.findByIdempotencyKeyForUpdate(CONTENT_ID, KEY_HASH))
            .thenReturn(Optional.of(existing));

        assertBusinessError(
            ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
            () -> useCase.request(
                USER_ID,
                CONTENT_ID,
                IDEMPOTENCY_KEY,
                "다른 사유",
                UUID.randomUUID()
            )
        );
    }

    @Test
    void 다른_키의_대기_요청은_콘텐츠_상태_충돌이다() {
        ContentWithdrawalRequest existing = withdrawalRequest("기존 사유");
        when(contentWithdrawalRequestService.findPendingForUpdate(CONTENT_ID))
            .thenReturn(Optional.of(existing));

        assertBusinessError(
            ErrorCode.CONTENT_STATE_CONFLICT,
            () -> useCase.request(
                USER_ID,
                CONTENT_ID,
                IDEMPOTENCY_KEY,
                "새 사유",
                UUID.randomUUID()
            )
        );
    }

    @Test
    void 담당_지역이나_소유자가_아니면_금지한다() {
        when(content.isOwnedBy(USER_ID)).thenReturn(false);

        assertBusinessError(
            ErrorCode.FORBIDDEN,
            () -> useCase.request(
                USER_ID,
                CONTENT_ID,
                IDEMPOTENCY_KEY,
                "운영 계획 변경",
                UUID.randomUUID()
            )
        );
    }

    private ContentWithdrawalRequest withdrawalRequest(String reason) {
        ContentWithdrawalRequest request = mock(ContentWithdrawalRequest.class);
        when(request.getContentWithdrawalRequestId()).thenReturn(REQUEST_ID);
        when(request.getContent()).thenReturn(content);
        when(request.getRequestReason()).thenReturn(reason);
        when(request.getRequestedAt()).thenReturn(REQUESTED_AT);
        return request;
    }

    private void assertBusinessError(ErrorCode errorCode, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(errorCode)
            );
    }
}
