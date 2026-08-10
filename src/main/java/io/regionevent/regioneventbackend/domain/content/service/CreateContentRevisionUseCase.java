package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionService.CreateContentRevisionCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateContentRevisionUseCase {

    private static final ZoneOffset REQUIRED_OFFSET = ZoneOffset.ofHours(9);
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("[1-9]\\d*");

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RepresentativeImageConnectionService representativeImageConnectionService;
    private final ContentService contentService;
    private final ContentRevisionService contentRevisionService;
    private final ContentLogService contentLogService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public CreateContentRevisionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        RepresentativeImageConnectionService representativeImageConnectionService,
        ContentService contentService,
        ContentRevisionService contentRevisionService,
        ContentLogService contentLogService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.representativeImageConnectionService = representativeImageConnectionService;
        this.contentService = contentService;
        this.contentRevisionService = contentRevisionService;
        this.contentLogService = contentLogService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CreateContentRevisionResponse createRevision(
        Long authenticatedUserId,
        Long contentId,
        CreateContentRevisionRequest request,
        String requestId
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        Instant submittedAt = clock.instant();
        Content content = contentService.findOwnedContentForRevisionCreation(
            contentId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
        ContentStatus originalStatus = content.getStatus();
        validatePendingRevisionEligibility(content, originalStatus);
        Instant publishAt = resolvePublishAt(originalStatus, request.publishAt());
        if (originalStatus == ContentStatus.APPROVED) {
            content = contentService.markPrePublicationRevisionPending(content);
        }

        CandidateImage candidateImage = resolveCandidateImage(request.representativeImageObjectId(), content, operator);
        ContentRevision contentRevision = contentRevisionService.createEditRequestedRevision(
            content,
            operator.user(),
            toCommand(request, publishAt),
            candidateImage.imageObject(),
            candidateImage.assignedAt(),
            submittedAt
        );
        if (originalStatus == ContentStatus.APPROVED) {
            recordPendingStateChange(content, operator, requestId, submittedAt);
        }
        return CreateContentRevisionResponse.from(contentRevision);
    }

    private void validateRequest(CreateContentRevisionRequest request) {
        if (request == null) {
            throw invalidInput();
        }
        validateReservationPrice(request.reservationPrice());
    }

    private void validatePendingRevisionEligibility(
        Content content,
        ContentStatus originalStatus
    ) {
        if (originalStatus == ContentStatus.PENDING
            && !contentLogService.hasApprovedToPendingRevisionHistory(content)) {
            throw stateConflict();
        }
    }

    private Instant resolvePublishAt(
        ContentStatus status,
        OffsetDateTime requestedPublishAt
    ) {
        if (status == ContentStatus.PUBLISHED) {
            if (requestedPublishAt != null) {
                throw stateConflict();
            }
            return null;
        }
        if (status == ContentStatus.APPROVED) {
            validateSeoulOffset(requestedPublishAt);
            return requestedPublishAt.toInstant();
        }
        if (status == ContentStatus.PENDING) {
            validateSeoulOffset(requestedPublishAt);
            return requestedPublishAt.toInstant();
        }
        throw stateConflict();
    }

    private CandidateImage resolveCandidateImage(
        JsonNode representativeImageObjectId,
        Content content,
        AuthorizedOperator operator
    ) {
        Long imageObjectId = parseOptionalImageObjectId(representativeImageObjectId);
        if (imageObjectId != null) {
            ImageObject imageObject = representativeImageConnectionService.validateAndMarkConnected(
                imageObjectId,
                operator.user().getUserId(),
                operator.region().getRegionId()
            );
            return new CandidateImage(imageObject, imageObject.getLinkedAt());
        }
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        Instant representativeImageAssignedAt = content.getRepresentativeImageAssignedAt();
        if (representativeImageObject == null || representativeImageAssignedAt == null) {
            throw stateConflict();
        }
        return new CandidateImage(representativeImageObject, representativeImageAssignedAt);
    }

    private Long parseOptionalImageObjectId(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        String imageObjectId = value.stringValue();
        if (!POSITIVE_DECIMAL_PATTERN.matcher(imageObjectId).matches()) {
            throw invalidInput();
        }
        try {
            return Long.valueOf(imageObjectId);
        } catch (NumberFormatException exception) {
            throw invalidInput();
        }
    }

    private void validateSeoulOffset(OffsetDateTime dateTime) {
        if (dateTime == null || !REQUIRED_OFFSET.equals(dateTime.getOffset())) {
            throw stateConflict();
        }
    }

    private CreateContentRevisionCommand toCommand(
        CreateContentRevisionRequest request,
        Instant publishAt
    ) {
        return new CreateContentRevisionCommand(
            request.title(),
            request.description(),
            request.locationText(),
            request.operatingHoursText(),
            request.contactText(),
            request.precautions(),
            request.ageRequirement(),
            request.materials(),
            request.cancellationPolicyText(),
            request.reservationPrice(),
            publishAt
        );
    }

    private void recordPendingStateChange(
        Content content,
        AuthorizedOperator operator,
        String requestId,
        Instant submittedAt
    ) {
        contentLogService.recordPending(content, operator.user(), submittedAt);
        recordAuditEventUseCase.record(new AuditEventCommand(
            UUID.fromString(requestId),
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentStatus.APPROVED.name(),
            ContentStatus.PENDING.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(operator.roleAssignment()),
            submittedAt
        ));
    }

    private void validateReservationPrice(Integer reservationPrice) {
        if (reservationPrice == null || reservationPrice < 0) {
            throw invalidInput();
        }
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
    }

    private record CandidateImage(
        ImageObject imageObject,
        Instant assignedAt
    ) {
    }
}
