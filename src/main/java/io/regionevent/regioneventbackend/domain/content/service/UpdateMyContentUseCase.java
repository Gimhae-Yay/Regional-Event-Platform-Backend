package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService.UpdateContentCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectCleanupService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService.DeletePendingImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateMyContentUseCase {

    private static final ZoneOffset REQUIRED_OFFSET = ZoneOffset.ofHours(9);
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("[1-9]\\d*");

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RepresentativeImageConnectionService representativeImageConnectionService;
    private final ContentService contentService;
    private final ImageObjectService imageObjectService;
    private final ImageObjectCleanupService imageObjectCleanupService;
    private final Clock clock;

    public UpdateMyContentUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        RepresentativeImageConnectionService representativeImageConnectionService,
        ContentService contentService,
        ImageObjectService imageObjectService,
        ImageObjectCleanupService imageObjectCleanupService,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.representativeImageConnectionService = representativeImageConnectionService;
        this.contentService = contentService;
        this.imageObjectService = imageObjectService;
        this.imageObjectCleanupService = imageObjectCleanupService;
        this.clock = clock;
    }

    @Transactional
    public UpdateMyContentResponse updateContent(
        Long authenticatedUserId,
        Long contentId,
        UpdateMyContentRequest request
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        Content content = contentService.findRejectedOwnedContentForUpdate(
            contentId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
        ImageObject previousImageObject = content.getRepresentativeImageObject();
        Long replacementImageObjectId = parseOptionalPositiveImageObjectId(request.representativeImageObjectId());
        ImageObject replacementImageObject = findReplacementImageObject(
            content,
            replacementImageObjectId,
            operator
        );
        Content updatedContent = contentService.updateRejectedContent(
            content,
            toContentCommand(request),
            replacementImageObject,
            clock.instant()
        );
        if (replacementImageObject != null) {
            imageObjectService.markDeletePendingIfUnreferenced(previousImageObject, replacementImageObject)
                .ifPresent(this::deleteImageObjectAfterCommit);
        }

        return UpdateMyContentResponse.from(updatedContent);
    }

    private void deleteImageObjectAfterCommit(DeletePendingImageObject deletePendingImageObject) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                imageObjectCleanupService.deletePendingObject(
                    deletePendingImageObject.imageObjectId(),
                    deletePendingImageObject.objectKey()
                );
            }
        });
    }

    private ImageObject findReplacementImageObject(
        Content content,
        Long replacementImageObjectId,
        AuthorizedOperator operator
    ) {
        if (replacementImageObjectId == null || content.hasRepresentativeImage(replacementImageObjectId)) {
            return null;
        }
        return representativeImageConnectionService.validateAndMarkConnected(
            replacementImageObjectId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
    }

    private void validateRequest(UpdateMyContentRequest request) {
        if (request == null) {
            throw invalidInput();
        }
        validateSeoulOffset(request.publishAt());
    }

    private void validateSeoulOffset(OffsetDateTime dateTime) {
        if (dateTime == null || !REQUIRED_OFFSET.equals(dateTime.getOffset())) {
            throw invalidInput();
        }
    }

    private UpdateContentCommand toContentCommand(UpdateMyContentRequest request) {
        return new UpdateContentCommand(
            request.title(),
            request.description(),
            request.locationText(),
            request.operatingHoursText(),
            request.contactText(),
            request.precautions(),
            request.ageRequirement(),
            request.materials(),
            request.cancellationPolicyText(),
            request.publishAt().toInstant()
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
            Long parsedValue = Long.valueOf(imageObjectId);
            if (parsedValue <= 0) {
                throw invalidInput();
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw invalidInput();
        }
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
