package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionService.UpdateContentRevisionCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateContentRevisionUseCase {

    private static final ZoneOffset REQUIRED_OFFSET = ZoneOffset.ofHours(9);
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("[1-9]\\d*");

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RepresentativeImageConnectionService representativeImageConnectionService;
    private final ContentRevisionService contentRevisionService;

    public UpdateContentRevisionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        RepresentativeImageConnectionService representativeImageConnectionService,
        ContentRevisionService contentRevisionService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.representativeImageConnectionService = representativeImageConnectionService;
        this.contentRevisionService = contentRevisionService;
    }

    @Transactional
    public UpdateContentRevisionResponse updateRevision(
        Long authenticatedUserId,
        Long revisionId,
        UpdateContentRevisionRequest request
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        ContentRevision contentRevision = contentRevisionService.findRejectedRevisionForUpdate(revisionId);
        validateOwnership(contentRevision, operator);
        ImageObject candidateImageObject = resolveCandidateImage(request.representativeImageObjectId(), operator);

        Instant publishAt = resolvePublishAt(contentRevision, request.publishAt());
        ContentRevision updatedContentRevision = contentRevisionService.updateRejectedRevision(
            contentRevision,
            toCommand(request, publishAt, candidateImageObject)
        );
        return UpdateContentRevisionResponse.from(updatedContentRevision);
    }

    private void validateRequest(UpdateContentRevisionRequest request) {
        if (request == null) {
            throw invalidInput();
        }
    }

    private void validateOwnership(
        ContentRevision contentRevision,
        AuthorizedOperator operator
    ) {
        Content content = contentRevision.getContent();
        if (content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!content.getOperator().getUserId().equals(operator.user().getUserId())
            || !content.getRegion().getRegionId().equals(operator.region().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private ImageObject resolveCandidateImage(
        JsonNode representativeImageObjectId,
        AuthorizedOperator operator
    ) {
        Long imageObjectId = parseOptionalPositiveImageObjectId(representativeImageObjectId);
        if (imageObjectId == null) {
            return null;
        }
        return representativeImageConnectionService.validateAndMarkConnected(
            imageObjectId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
    }

    private Long parseOptionalPositiveImageObjectId(JsonNode value) {
        if (value == null || value.isNull()) {
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

    private Instant resolvePublishAt(
        ContentRevision contentRevision,
        OffsetDateTime requestedPublishAt
    ) {
        if (contentRevision.hasCandidatePublishAt()) {
            validateSeoulOffset(requestedPublishAt);
            return requestedPublishAt.toInstant();
        }
        if (requestedPublishAt != null) {
            throw invalidInput();
        }
        return null;
    }

    private void validateSeoulOffset(OffsetDateTime dateTime) {
        if (dateTime == null || !REQUIRED_OFFSET.equals(dateTime.getOffset())) {
            throw invalidInput();
        }
    }

    private UpdateContentRevisionCommand toCommand(
        UpdateContentRevisionRequest request,
        Instant publishAt,
        ImageObject candidateImageObject
    ) {
        return new UpdateContentRevisionCommand(
            request.title(),
            request.description(),
            request.locationText(),
            request.operatingHoursText(),
            request.contactText(),
            request.precautions(),
            request.ageRequirement(),
            request.materials(),
            request.cancellationPolicyText(),
            publishAt,
            candidateImageObject,
            candidateImageObject == null ? null : candidateImageObject.getLinkedAt()
        );
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
