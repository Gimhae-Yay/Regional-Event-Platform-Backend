package io.regionevent.regioneventbackend.domain.review.service;

import java.time.Instant;
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
import io.regionevent.regioneventbackend.domain.review.dto.UpdateReviewRequest;
import io.regionevent.regioneventbackend.domain.review.dto.UpdateReviewResponse;
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateReviewUseCase.class);

    private final AppUserService appUserService;
    private final ReviewService reviewService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public UpdateReviewUseCase(
        AppUserService appUserService,
        ReviewService reviewService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.reviewService = reviewService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public UpdateReviewResponse update(
        Long userId,
        Long reviewId,
        UpdateReviewRequest request,
        UUID requestId
    ) {
        AuditEventActor actor = null;
        try {
            AppUser user = appUserService.findActiveOrdinaryUserForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            actor = new AuditEventActor(user, UserRole.VISITOR);
            Review review = reviewService.updatePublishedByAuthorWithinThirtyDays(
                reviewId,
                user.getUserId(),
                request.rating(),
                request.reviewText()
            );
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                review.getRegion(),
                AuditEventTargetType.REVIEW,
                review.getReviewId(),
                review.getStatus().name(),
                review.getStatus().name(),
                AuditEventResult.SUCCESS,
                null,
                actor,
                review.getUpdatedAt()
            ));
            return UpdateReviewResponse.from(review);
        } catch (BusinessException exception) {
            recordFailure(requestId, actor, reviewId, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, actor, reviewId, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void recordFailure(
        UUID requestId,
        AuditEventActor actor,
        Long reviewId,
        ErrorCode errorCode
    ) {
        Region region = reviewService.findRegionByReviewId(reviewId);
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.REVIEW,
            reviewId,
            null,
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            actor,
            Instant.now()
        ));
        log.warn(
            "Review update rejected. requestId={}, reviewId={}, errorCode={}",
            requestId,
            reviewId,
            errorCode.code()
        );
    }
}
