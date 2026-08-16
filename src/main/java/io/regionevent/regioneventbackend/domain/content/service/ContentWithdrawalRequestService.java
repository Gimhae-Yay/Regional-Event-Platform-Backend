package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentWithdrawalRequestService {

    private static final String CONTENT_KEY_UNIQUE_CONSTRAINT =
        "uk_content_withdrawal_request_content_key";
    private static final String ACTIVE_CONTENT_UNIQUE_CONSTRAINT =
        "uk_content_withdrawal_request_active_content";

    private final ContentWithdrawalRequestRepository contentWithdrawalRequestRepository;

    public ContentWithdrawalRequestService(
        ContentWithdrawalRequestRepository contentWithdrawalRequestRepository
    ) {
        this.contentWithdrawalRequestRepository = contentWithdrawalRequestRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContentWithdrawalRequest> findByIdempotencyKeyForUpdate(
        Long contentId,
        String idempotencyKeyHash
    ) {
        return contentWithdrawalRequestRepository.findByContentIdAndIdempotencyKeyHashForUpdate(
            contentId,
            idempotencyKeyHash
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContentWithdrawalRequest> findPendingForUpdate(Long contentId) {
        return contentWithdrawalRequestRepository.findByContentIdAndStatusForUpdate(
            contentId,
            ContentWithdrawalRequestStatus.PENDING
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentWithdrawalRequest createPending(
        Content content,
        AppUser requester,
        String idempotencyKeyHash,
        String requestReason,
        Instant requestedAt
    ) {
        ContentWithdrawalRequest request = ContentWithdrawalRequest.createPending(
            content,
            requester,
            idempotencyKeyHash,
            requestReason,
            requestedAt
        );
        try {
            return contentWithdrawalRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException exception) {
            if (isConstraintViolation(exception, CONTENT_KEY_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception);
            }
            if (isConstraintViolation(exception, ACTIVE_CONTENT_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT, exception);
            }
            throw exception;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContentWithdrawalRequest> invalidatePendingByUser(
        Long contentId,
        AppUser invalidator,
        Instant invalidatedAt,
        ContentWithdrawalRequestInvalidationReason reason
    ) {
        Optional<ContentWithdrawalRequest> pendingRequest = findPendingForUpdate(contentId);
        pendingRequest.ifPresent(request -> request.invalidateByUser(
            invalidator,
            invalidatedAt,
            reason
        ));
        return pendingRequest;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContentWithdrawalRequest> invalidatePendingBySystem(
        Long contentId,
        Instant invalidatedAt,
        ContentWithdrawalRequestInvalidationReason reason
    ) {
        Optional<ContentWithdrawalRequest> pendingRequest = findPendingForUpdate(contentId);
        pendingRequest.ifPresent(request -> request.invalidateBySystem(invalidatedAt, reason));
        return pendingRequest;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unlinkUserReferencesByUserId(Long userId) {
        contentWithdrawalRequestRepository.unlinkRequesterByUserId(userId);
        contentWithdrawalRequestRepository.unlinkReviewerByUserId(userId);
        contentWithdrawalRequestRepository.unlinkInvalidatorByUserId(userId);
    }

    private boolean isConstraintViolation(
        DataIntegrityViolationException exception,
        String expectedConstraintName
    ) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                && matchesConstraintName(
                    constraintViolationException.getConstraintName(),
                    expectedConstraintName
                )) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expectedConstraintName)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean matchesConstraintName(String actualName, String expectedName) {
        return expectedName.equalsIgnoreCase(actualName)
            || actualName != null
                && actualName.toLowerCase(Locale.ROOT).endsWith("." + expectedName);
    }
}
