package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.payment.dto.RetryRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneResponseException;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RetryRefundUseCase {

    private static final int MAX_ATTEMPT_COUNT = 3;
    private static final String RETRY_REASON = "MANUAL_REFUND_RETRY";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RefundService refundService;
    private final RefundAttemptService refundAttemptService;
    private final RestoreCouponUseCase restoreCouponUseCase;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final PortOnePaymentGateway portOnePaymentGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RetryRefundUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RefundService refundService,
        RefundAttemptService refundAttemptService,
        RestoreCouponUseCase restoreCouponUseCase,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PortOnePaymentGateway portOnePaymentGateway,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.refundService = refundService;
        this.refundAttemptService = refundAttemptService;
        this.restoreCouponUseCase = restoreCouponUseCase;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.portOnePaymentGateway = portOnePaymentGateway;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RetryRefundResponse retry(
        Long actorUserId,
        String refundIdValue,
        UUID requestId
    ) {
        long refundId = toPositiveId(refundIdValue);
        PreparedRetry preparedRetry = executeInTransaction(
            () -> prepareRetry(actorUserId, refundId)
        );

        try {
            PortOnePaymentGateway.PortOneCancellation cancellation = portOnePaymentGateway.cancelPayment(
                preparedRetry.portonePaymentId(),
                preparedRetry.amount(),
                RETRY_REASON
            );
            return executeInTransaction(
                () -> confirmResponse(actorUserId, preparedRetry, cancellation, requestId)
            );
        } catch (PortOneNoResponseException exception) {
            return executeInTransaction(
                () -> confirmNoResponse(actorUserId, preparedRetry, exception.getFailureReasonCode(), requestId)
            );
        } catch (PortOneResponseException exception) {
            executeInTransaction(() -> confirmReceivedError(actorUserId, preparedRetry, exception, requestId));
            throw exception;
        }
    }

    private PreparedRetry prepareRetry(
        Long actorUserId,
        long refundId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdminForUpdate(actorUserId);
        Refund refund = refundService.findByRefundIdForUpdate(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (refund.getStatus() != RefundStatus.FAILED) {
            throw new BusinessException(ErrorCode.REFUND_STATE_CONFLICT);
        }

        int nextAttemptNo = findNextAttemptNo(refund.getRefundId());
        Instant attemptedAt = Instant.now(clock);
        refund.retry();
        RefundAttempt attempt = refundAttemptService.create(new RefundAttempt(
            refund,
            nextAttemptNo,
            toInitiatorKind(assignment),
            attemptedAt
        ));
        return new PreparedRetry(
            refund.getRefundId(),
            attempt.getRefundAttemptId(),
            refund.getPayment().getPortonePaymentId(),
            refund.getAmount()
        );
    }

    private int findNextAttemptNo(Long refundId) {
        List<RefundAttempt> attempts = refundAttemptService.findAllByRefundId(refundId);
        int lastAttemptNo = attempts.stream()
            .map(RefundAttempt::getAttemptNo)
            .max(Comparator.naturalOrder())
            .orElse(0);
        if (lastAttemptNo >= MAX_ATTEMPT_COUNT) {
            throw new BusinessException(ErrorCode.REFUND_STATE_CONFLICT);
        }
        return lastAttemptNo + 1;
    }

    private RetryRefundResponse confirmResponse(
        Long actorUserId,
        PreparedRetry preparedRetry,
        PortOnePaymentGateway.PortOneCancellation cancellation,
        UUID requestId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdminForUpdate(actorUserId);
        Refund refund = findPreparedRefund(preparedRetry.refundId());
        RefundAttempt attempt = findPreparedAttempt(preparedRetry.refundAttemptId());
        attempt.respond(cancellation.cancellationId(), cancellation.status(), cancellation.resultHash());
        Instant completedAt = Instant.now(clock);
        if (cancellation.isSucceeded()) {
            refund.succeed(completedAt);
            restoreCouponUseCase.restoreForRefund(refund, requestId, new AuditEventActor(assignment));
        } else if (cancellation.isExplicitlyFailed()) {
            refund.fail(completedAt);
        } else {
            refund.markDiscrepant(completedAt);
        }
        recordRefundAudit(refund, assignment, requestId, completedAt);
        return RetryRefundResponse.from(refund, attempt);
    }

    private RetryRefundResponse confirmNoResponse(
        Long actorUserId,
        PreparedRetry preparedRetry,
        RefundFailureReasonCode failureReasonCode,
        UUID requestId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdminForUpdate(actorUserId);
        Refund refund = findPreparedRefund(preparedRetry.refundId());
        RefundAttempt attempt = findPreparedAttempt(preparedRetry.refundAttemptId());
        attempt.noResponse(failureReasonCode);
        Instant completedAt = Instant.now(clock);
        refund.markDiscrepant(completedAt);
        recordRefundAudit(refund, assignment, requestId, completedAt);
        return RetryRefundResponse.from(refund, attempt);
    }

    private RetryRefundResponse confirmReceivedError(
        Long actorUserId,
        PreparedRetry preparedRetry,
        PortOneResponseException exception,
        UUID requestId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdminForUpdate(actorUserId);
        Refund refund = findPreparedRefund(preparedRetry.refundId());
        RefundAttempt attempt = findPreparedAttempt(preparedRetry.refundAttemptId());
        attempt.respond(null, exception.getExternalStatus(), exception.getResultHash());
        Instant completedAt = Instant.now(clock);
        refund.markDiscrepant(completedAt);
        recordRefundAudit(refund, assignment, requestId, completedAt);
        return RetryRefundResponse.from(refund, attempt);
    }

    private Refund findPreparedRefund(Long refundId) {
        return refundService.findByRefundIdForUpdate(refundId)
            .orElseThrow(() -> new IllegalStateException("prepared refund does not exist"));
    }

    private RefundAttempt findPreparedAttempt(Long refundAttemptId) {
        return refundAttemptService.findByRefundAttemptIdForUpdate(refundAttemptId)
            .orElseThrow(() -> new IllegalStateException("prepared refund attempt does not exist"));
    }

    private RefundAttemptInitiatorKind toInitiatorKind(PlatformAdminAssignment assignment) {
        return assignment.getGrade() == PlatformAdminGrade.SUPER_ADMIN
            ? RefundAttemptInitiatorKind.SUPER_ADMIN
            : RefundAttemptInitiatorKind.PLATFORM_ADMIN;
    }

    private void recordRefundAudit(
        Refund refund,
        PlatformAdminAssignment assignment,
        UUID requestId,
        Instant completedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            refund.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.REFUND,
            refund.getRefundId(),
            RefundStatus.FAILED.name(),
            refund.getStatus().name(),
            AuditEventResult.SUCCESS,
            RETRY_REASON,
            null,
            null,
            new AuditEventActor(assignment),
            completedAt
        ));
    }

    private long toPositiveId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!value.matches("[0-9]+")) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new BusinessException(ErrorCode.INVALID_TYPE);
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("transaction result must not be null");
        }
        return result;
    }

    private record PreparedRetry(
        Long refundId,
        Long refundAttemptId,
        String portonePaymentId,
        long amount
    ) {
    }
}
