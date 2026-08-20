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
import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewRequest;
import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewResponse;
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateVisitReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateVisitReviewUseCase.class);
    private final AppUserService appUserService;
    private final VisitService visitService;
    private final ReviewService reviewService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public CreateVisitReviewUseCase(
        AppUserService appUserService,
        VisitService visitService,
        ReviewService reviewService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.visitService = visitService;
        this.reviewService = reviewService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public CreateVisitReviewResponse create(
        Long userId,
        Long visitId,
        CreateVisitReviewRequest request,
        UUID requestId
    ) {
        AuditEventActor actor = null;
        Visit visit = null;

        try {
            AppUser user = appUserService.findActiveOrdinaryUserForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            actor = new AuditEventActor(user, UserRole.VISITOR);
            visit = visitService.findById(visitId);
            validateOwner(visit, user);
            Review review = reviewService.createPublished(visit, user, request.rating(), request.reviewText());
            recordSuccessfulAuditEvent(requestId, actor, review);
            return CreateVisitReviewResponse.from(review);
        } catch (BusinessException exception) {
            recordFailure(requestId, actor, visit, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, actor, visit, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void validateOwner(Visit visit, AppUser user) {
        if (visit.getUser() == null || !visit.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void recordSuccessfulAuditEvent(
        UUID requestId,
        AuditEventActor actor,
        Review review
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            review.getRegion(),
            AuditEventTargetType.REVIEW,
            review.getReviewId(),
            null,
            review.getStatus().name(),
            AuditEventResult.SUCCESS,
            null,
            actor,
            review.getCreatedAt()
        ));
    }

    private void recordFailure(
        UUID requestId,
        AuditEventActor actor,
        Visit visit,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            visit == null ? null : visit.getRegion(),
            AuditEventTargetType.REVIEW,
            null,
            null,
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            actor,
            Instant.now()
        ));
        log.warn(
            "Review creation rejected. requestId={}, visitId={}, errorCode={}",
            requestId,
            visit == null ? null : visit.getVisitId(),
            errorCode.code()
        );
    }

}
