package io.regionevent.regioneventbackend.domain.review.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class DeleteReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteReviewUseCase.class);

    private final AppUserService appUserService;
    private final ReviewService reviewService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public DeleteReviewUseCase(
        AppUserService appUserService,
        ReviewService reviewService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.reviewService = reviewService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public void delete(Long userId, Long reviewId, UUID requestId) {
        AuditEventActor actor = null;
        Review review = null;
        ReviewStatus previousStatus = null;

        try {
            AppUser user = appUserService.findActiveOrdinaryUserForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            actor = new AuditEventActor(user, UserRole.VISITOR);
            review = reviewService.findByIdForUpdate(reviewId);
            previousStatus = review.getStatus();
            validatePublished(review);
            validateOwner(review, user);
            Instant deletedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            review.delete(deletedAt);
            recordSuccess(requestId, review, actor, deletedAt);
        } catch (BusinessException exception) {
            if (previousStatus != ReviewStatus.DELETED) {
                recordFailure(requestId, review, previousStatus, actor, exception.getErrorCode());
            }
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, review, previousStatus, actor, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void validatePublished(Review review) {
        if (review.getStatus() != ReviewStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void validateOwner(Review review, AppUser user) {
        if (review.getUser() == null || !review.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void recordSuccess(
        UUID requestId,
        Review review,
        AuditEventActor actor,
        Instant deletedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            review.getRegion(),
            AuditEventTargetType.REVIEW,
            review.getReviewId(),
            ReviewStatus.PUBLISHED.name(),
            ReviewStatus.DELETED.name(),
            AuditEventResult.SUCCESS,
            null,
            actor,
            deletedAt
        ));
    }

    private void recordFailure(
        UUID requestId,
        Review review,
        ReviewStatus previousStatus,
        AuditEventActor actor,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            review == null ? null : review.getRegion(),
            AuditEventTargetType.REVIEW,
            review == null ? null : review.getReviewId(),
            previousStatus == null ? null : previousStatus.name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            actor,
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
        log.warn(
            "Review deletion rejected. requestId={}, reviewId={}, errorCode={}",
            requestId,
            review == null ? null : review.getReviewId(),
            errorCode.code()
        );
    }

}
