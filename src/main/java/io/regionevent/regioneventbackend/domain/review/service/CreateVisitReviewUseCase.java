package io.regionevent.regioneventbackend.domain.review.service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

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
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateVisitReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateVisitReviewUseCase.class);
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final VisitService visitService;
    private final ReviewService reviewService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public CreateVisitReviewUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        VisitService visitService,
        ReviewService reviewService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.visitService = visitService;
        this.reviewService = reviewService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public CreateVisitReviewResponse create(
        Long userId,
        String visitIdValue,
        CreateVisitReviewRequest request,
        UUID requestId
    ) {
        Long visitId = toPositiveVisitId(visitIdValue);
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuditEventActor actor = new AuditEventActor(userRoleAssignmentService.findActiveVisitor(userId));
        Visit visit = findVisitOrRecordFailure(requestId, actor, visitId);

        try {
            validateOwner(visit, user);
            Review review = reviewService.createPublished(visit, user, request.rating(), request.reviewText());
            recordSuccessfulAuditEvent(requestId, actor, review);
            return CreateVisitReviewResponse.from(review);
        } catch (BusinessException exception) {
            recordFailure(requestId, actor, visit, exception.getErrorCode());
            throw exception;
        }
    }

    private Visit findVisitOrRecordFailure(
        UUID requestId,
        AuditEventActor actor,
        Long visitId
    ) {
        try {
            return visitService.findById(visitId);
        } catch (BusinessException exception) {
            recordFailure(requestId, actor, null, exception.getErrorCode());
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

    private Long toPositiveVisitId(String value) {
        Long visitId;
        try {
            visitId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return visitId;
    }
}
