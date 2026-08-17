package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionCache;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApproveContentWithdrawalUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long WITHDRAWAL_REQUEST_ID = 7001L;
    private static final UUID REQUEST_ID = UUID.fromString("4d7c2044-b64f-4bd5-a718-5390198a6819");
    private static final Instant APPROVED_AT = Instant.parse("2026-08-16T06:00:00Z");
    private static final Instant FAILURE_AT = Instant.parse("2026-08-16T06:00:00.123456789Z");

    private final ContentWithdrawalRequestService withdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final ContentRevisionInvalidationService revisionInvalidationService =
        mock(ContentRevisionInvalidationService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePaymentUseCase =
        mock(ExpirePendingPaymentForTerminatedHoldUseCase.class);
    private final RegionAdminAuthorizationService authorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final PublicCatalogCacheInvalidator cacheInvalidator = mock(PublicCatalogCacheInvalidator.class);
    private final Clock clock = Clock.fixed(FAILURE_AT, ZoneOffset.UTC);
    private final ApproveContentWithdrawalUseCase useCase = new ApproveContentWithdrawalUseCase(
        withdrawalRequestService,
        contentService,
        regionService,
        revisionInvalidationService,
        contentSessionService,
        contentLogService,
        capacityHoldService,
        expirePaymentUseCase,
        authorizationService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        cacheInvalidator,
        clock
    );

    private final RegionAdminAuthorizationService.AuthorizedRegionAdmin authorizedAdmin =
        mock(RegionAdminAuthorizationService.AuthorizedRegionAdmin.class);
    private final UserRoleAssignment assignment = mock(UserRoleAssignment.class);
    private final AppUser admin = mock(AppUser.class);
    private final Region region = mock(Region.class);
    private final Content content = mock(Content.class);
    private final ContentWithdrawalRequest withdrawalRequest = mock(ContentWithdrawalRequest.class);

    @BeforeEach
    void setUp() {
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenReturn(authorizedAdmin);
        when(authorizedAdmin.roleAssignment()).thenReturn(assignment);
        when(withdrawalRequestService.findContentId(WITHDRAWAL_REQUEST_ID)).thenReturn(CONTENT_ID);
        when(contentService.findContentRegionId(CONTENT_ID)).thenReturn(REGION_ID);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(region);
        when(contentService.findForUpdate(CONTENT_ID)).thenReturn(content);
        when(authorizedAdmin.authorize(REGION_ID)).thenReturn(assignment);
        when(withdrawalRequestService.findReviewTargetForUpdate(WITHDRAWAL_REQUEST_ID))
            .thenReturn(withdrawalRequest);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(300L);
        when(assignment.getAppUser()).thenReturn(admin);
        when(admin.getUserId()).thenReturn(USER_ID);
        when(admin.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getVersionNo()).thenReturn(4);
        when(withdrawalRequest.getContentWithdrawalRequestId()).thenReturn(WITHDRAWAL_REQUEST_ID);
        when(withdrawalRequest.getRequestReason()).thenReturn("운영 계획 변경");
        when(withdrawalRequest.getReviewedAt()).thenReturn(APPROVED_AT);
    }

    @Test
    void 최초_승인은_잠금_순서대로_연계_상태를_한번만_종결한다() {
        when(withdrawalRequest.getStatus())
            .thenReturn(
                ContentWithdrawalRequestStatus.PENDING,
                ContentWithdrawalRequestStatus.APPROVED
            );
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED, ContentStatus.WITHDRAWN);
        when(contentService.findCurrentDatabaseTime()).thenReturn(APPROVED_AT);
        when(contentService.withdraw(content, APPROVED_AT)).thenReturn(content);
        when(revisionInvalidationService.invalidateActiveRevisionForContent(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        CapacityHoldService.TerminatedCapacityHold terminatedCapacityHold =
            new CapacityHoldService.TerminatedCapacityHold(
                500L,
                region,
                2,
                CapacityHoldStatus.INVALIDATED,
                "CONTENT_WITHDRAWN",
                APPROVED_AT
            );
        when(capacityHoldService.invalidateAllActiveHoldsForContent(CONTENT_ID, "CONTENT_WITHDRAWN"))
            .thenReturn(List.of(terminatedCapacityHold));

        ApproveContentWithdrawalResult result = useCase.approve(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            REQUEST_ID
        );

        assertThat(result.requestStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.WITHDRAWN);
        assertThat(result.approvedAt()).isEqualTo(APPROVED_AT);
        InOrder order = inOrder(
            authorizationService,
            withdrawalRequestService,
            contentService,
            regionService,
            authorizedAdmin,
            revisionInvalidationService,
            contentSessionService,
            contentLogService,
            capacityHoldService
        );
        order.verify(authorizationService).requireAuthorizedRegionAdminForUpdate(USER_ID);
        order.verify(withdrawalRequestService).findContentId(WITHDRAWAL_REQUEST_ID);
        order.verify(contentService).findContentRegionId(CONTENT_ID);
        order.verify(regionService).findRegionForUpdate(REGION_ID);
        order.verify(contentService).findForUpdate(CONTENT_ID);
        order.verify(withdrawalRequestService).findReviewTargetForUpdate(WITHDRAWAL_REQUEST_ID);
        order.verify(authorizedAdmin).authorize(REGION_ID);
        order.verify(contentService).findCurrentDatabaseTime();
        order.verify(withdrawalRequestService).approve(withdrawalRequest, admin, APPROVED_AT);
        order.verify(contentService).withdraw(content, APPROVED_AT);
        order.verify(revisionInvalidationService).invalidateActiveRevisionForContent(
            CONTENT_ID,
            admin,
            APPROVED_AT,
            ContentRevisionInvalidationReason.CONTENT_WITHDRAWN
        );
        order.verify(contentSessionService).lockSuspendTargetsForUpdate(CONTENT_ID);
        order.verify(contentLogService).recordWithdrawn(
            content,
            admin,
            APPROVED_AT,
            "운영 계획 변경"
        );
        order.verify(capacityHoldService).invalidateAllActiveHoldsForContent(
            CONTENT_ID,
            "CONTENT_WITHDRAWN"
        );
        verify(expirePaymentUseCase).expire(
            eq(terminatedCapacityHold),
            eq(REQUEST_ID),
            any()
        );
        verify(cacheInvalidator).invalidateContentAfterCommit(REGION_ID, CONTENT_ID, 4);

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase, times(2)).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
            .extracting(AuditEventCommand::targetType, AuditEventCommand::reasonCode)
            .containsExactly(
                tuple(
                    AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
                    "CONTENT_WITHDRAWAL_APPROVED"
                ),
                tuple(AuditEventTargetType.CONTENT, "CONTENT_WITHDRAWN")
            );
    }

    @Test
    void 이미_승인된_요청은_저장_결과만_반환하고_부수_효과를_반복하지_않는다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.APPROVED);
        when(content.getStatus()).thenReturn(ContentStatus.WITHDRAWN);

        ApproveContentWithdrawalResult result = useCase.approve(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            REQUEST_ID
        );

        assertThat(result.requestStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.WITHDRAWN);
        verify(contentService, never()).findCurrentDatabaseTime();
        verify(withdrawalRequestService, never()).approve(any(), any(), any());
        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(
            revisionInvalidationService,
            contentSessionService,
            contentLogService,
            capacityHoldService,
            expirePaymentUseCase,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            cacheInvalidator
        );
    }

    @Test
    void 대기가_아닌_요청은_상태_충돌을_그대로_반환하고_실패_감사를_등록한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.REJECTED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );

        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
        assertFailureAudit(
            ErrorCode.CONTENT_STATE_CONFLICT,
            ContentWithdrawalRequestStatus.REJECTED
        );
    }

    @Test
    void 무효화된_요청은_상태_충돌_실패_감사를_등록한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.INVALIDATED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );

        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
        assertFailureAudit(
            ErrorCode.CONTENT_STATE_CONFLICT,
            ContentWithdrawalRequestStatus.INVALIDATED
        );
    }

    @Test
    void 콘텐츠가_공개_상태가_아니면_요청_현재상태로_실패_감사를_등록한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.PENDING);
        when(content.getStatus()).thenReturn(ContentStatus.APPROVED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );

        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
        assertFailureAudit(
            ErrorCode.CONTENT_STATE_CONFLICT,
            ContentWithdrawalRequestStatus.PENDING
        );
    }

    @Test
    void 존재하지_않는_요청은_감사와_전용로그_없이_조회_오류를_그대로_반환한다() {
        BusinessException originalException = new BusinessException(ErrorCode.NOT_FOUND);
        when(withdrawalRequestService.findContentId(WITHDRAWAL_REQUEST_ID)).thenThrow(originalException);
        ListAppender<ILoggingEvent> appender = attachUseCaseLogAppender();

        try {
            assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
                .isSameAs(originalException);
        } finally {
            detachUseCaseLogAppender(appender);
        }

        verify(regionService, never()).findRegionForUpdate(any());
        verifyNoInteractions(recordAuditEventUseCase, recordFailedAuditEventUseCase, cacheInvalidator);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 담당_지역이_아니면_잠긴_요청_상태로_실패_감사를_등록하고_권한_오류를_보존한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.PENDING);
        BusinessException originalException = new BusinessException(ErrorCode.FORBIDDEN);
        when(authorizedAdmin.authorize(REGION_ID)).thenThrow(originalException);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isSameAs(originalException);

        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
        assertFailureAudit(ErrorCode.FORBIDDEN, ContentWithdrawalRequestStatus.PENDING);
    }

    @Test
    void 식별_전_권한_오류는_비개인_고정필드_로그만_한번_기록한다() {
        BusinessException originalException = new BusinessException(ErrorCode.FORBIDDEN);
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenThrow(originalException);

        assertBeforeIdentificationLog(originalException, ErrorCode.FORBIDDEN);
    }

    @Test
    void 식별_전_상태_충돌은_공통_분류에서_비개인_고정필드_로그만_한번_기록한다() {
        BusinessException originalException = new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenThrow(originalException);

        assertBeforeIdentificationLog(originalException, ErrorCode.CONTENT_STATE_CONFLICT);
    }

    @Test
    void 식별_전_런타임_예외는_원문_없이_내부오류_로그만_한번_기록하고_원예외를_보존한다() {
        IllegalStateException originalException =
            new IllegalStateException("7001 admin@example.com token-secret");
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenThrow(originalException);

        assertBeforeIdentificationLog(originalException, ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 식별_후_런타임_예외는_내부오류_실패감사를_등록하고_원예외를_보존한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.PENDING);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        IllegalStateException originalException = new IllegalStateException("processing failure");
        when(contentService.findCurrentDatabaseTime()).thenThrow(originalException);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isSameAs(originalException);

        assertFailureAudit(
            ErrorCode.INTERNAL_SERVER_ERROR,
            ContentWithdrawalRequestStatus.PENDING
        );
    }

    @Test
    void 커밋후_캐시삭제가_실패해도_성공결과를_유지하고_실패감사를_등록하지_않는다() {
        when(withdrawalRequest.getStatus())
            .thenReturn(
                ContentWithdrawalRequestStatus.PENDING,
                ContentWithdrawalRequestStatus.APPROVED
            );
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED, ContentStatus.WITHDRAWN);
        when(contentService.findCurrentDatabaseTime()).thenReturn(APPROVED_AT);
        when(contentService.withdraw(content, APPROVED_AT)).thenReturn(content);
        when(revisionInvalidationService.invalidateActiveRevisionForContent(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(capacityHoldService.invalidateAllActiveHoldsForContent(CONTENT_ID, "CONTENT_WITHDRAWN"))
            .thenReturn(List.of());
        PublicRegionCache publicRegionCache = mock(PublicRegionCache.class);
        PublicContentCache publicContentCache = mock(PublicContentCache.class);
        doThrow(new IllegalStateException("cache failure"))
            .when(publicContentCache)
            .evictContent(REGION_ID, CONTENT_ID, 4);
        PublicCatalogCacheInvalidator failingCacheInvalidator =
            new PublicCatalogCacheInvalidator(publicRegionCache, publicContentCache);
        ApproveContentWithdrawalUseCase cacheFailureUseCase = createUseCase(failingCacheInvalidator);
        TransactionSynchronizationManager.initSynchronization();

        ApproveContentWithdrawalResult result;
        try {
            result = cacheFailureUseCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(result.requestStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.WITHDRAWN);
        verify(recordFailedAuditEventUseCase, never()).record(any());
    }

    private void assertBeforeIdentificationLog(
        RuntimeException originalException,
        ErrorCode errorCode
    ) {
        ListAppender<ILoggingEvent> appender = attachUseCaseLogAppender();
        try {
            assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
                .isSameAs(originalException);
        } finally {
            detachUseCaseLogAppender(appender);
        }

        verifyNoInteractions(recordAuditEventUseCase, recordFailedAuditEventUseCase, cacheInvalidator);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                .isEqualTo("Content withdrawal approval failed before identification")
                .doesNotContain(WITHDRAWAL_REQUEST_ID.toString())
                .doesNotContain(USER_ID.toString())
                .doesNotContain(originalException.getMessage());
            assertThat(event.getKeyValuePairs())
                .extracting(pair -> pair.key, pair -> pair.value)
                .containsExactly(
                    tuple("requestId", REQUEST_ID),
                    tuple("targetType", AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST),
                    tuple("errorCode", errorCode.code()),
                    tuple("identificationStage", "BEFORE_IDENTIFICATION")
                );
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private void assertFailureAudit(
        ErrorCode errorCode,
        ContentWithdrawalRequestStatus previousState
    ) {
        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(captor.capture());
        AuditEventCommand command = captor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.region()).isSameAs(region);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST);
        assertThat(command.targetId()).isEqualTo(WITHDRAWAL_REQUEST_ID);
        assertThat(command.previousState()).isEqualTo(previousState.name());
        assertThat(command.nextState()).isNull();
        assertThat(command.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(command.actor().getAppUser()).isSameAs(admin);
        assertThat(command.reasonCode()).isEqualTo(errorCode.code());
        assertThat(command.reason()).isNull();
        assertThat(command.evidenceReference()).isNull();
        assertThat(command.occurredAt()).isEqualTo(Instant.parse("2026-08-16T06:00:00.123456Z"));
    }

    private ListAppender<ILoggingEvent> attachUseCaseLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApproveContentWithdrawalUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachUseCaseLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ApproveContentWithdrawalUseCase.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private ApproveContentWithdrawalUseCase createUseCase(
        PublicCatalogCacheInvalidator publicCatalogCacheInvalidator
    ) {
        return new ApproveContentWithdrawalUseCase(
            withdrawalRequestService,
            contentService,
            regionService,
            revisionInvalidationService,
            contentSessionService,
            contentLogService,
            capacityHoldService,
            expirePaymentUseCase,
            authorizationService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            publicCatalogCacheInvalidator,
            clock
        );
    }
}
