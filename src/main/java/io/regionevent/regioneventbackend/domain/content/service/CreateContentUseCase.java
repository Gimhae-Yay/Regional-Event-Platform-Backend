package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest.SessionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService.CreateContentCommand;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService.CreateContentSessionCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateContentUseCase {

    private static final ZoneOffset REQUIRED_OFFSET = ZoneOffset.ofHours(9);
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("[1-9]\\d*");

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RepresentativeImageConnectionService representativeImageConnectionService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final Clock clock;

    public CreateContentUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        RepresentativeImageConnectionService representativeImageConnectionService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.representativeImageConnectionService = representativeImageConnectionService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.clock = clock;
    }

    @Transactional
    public CreateContentResponse createContent(
        Long authenticatedUserId,
        CreateContentRequest request
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        Instant submittedAt = clock.instant();
        ImageObject representativeImageObject = representativeImageConnectionService.validateAndMarkConnected(
            parsePositiveImageObjectId(request.representativeImageObjectId()),
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
        Content content = contentService.createPendingContent(
            operator.region(),
            operator.user(),
            representativeImageObject,
            toContentCommand(request),
            submittedAt
        );
        contentSessionService.createPendingSessions(
            content,
            operator.region(),
            toSessionCommands(request.sessions())
        );
        contentLogService.recordPending(content, operator.user(), submittedAt);

        return new CreateContentResponse(
            content.getContentId().toString(),
            content.getContentType(),
            content.getStatus(),
            submittedAt
        );
    }

    private void validateRequest(CreateContentRequest request) {
        if (request == null || request.sessions() == null || request.sessions().isEmpty()) {
            throw invalidInput();
        }
        validateReservationPrice(request.reservationPrice());
        validateSeoulOffset(request.publishAt());
        request.sessions().forEach(this::validateSession);
    }

    private void validateSession(SessionRequest session) {
        if (session == null) {
            throw invalidInput();
        }
        validateSeoulOffset(session.startsAt());
        validateSeoulOffset(session.endsAt());
        validateSeoulOffset(session.checkinOpenAt());
        validateSeoulOffset(session.checkinCloseAt());
        Instant startsAt = session.startsAt().toInstant();
        Instant endsAt = session.endsAt().toInstant();
        Instant checkinOpenAt = session.checkinOpenAt().toInstant();
        Instant checkinCloseAt = session.checkinCloseAt().toInstant();

        if (!startsAt.isBefore(endsAt)
            || !checkinOpenAt.isBefore(checkinCloseAt)
            || !endsAt.isAfter(checkinCloseAt)
            || session.capacity() == null
            || session.capacity() <= 0) {
            throw invalidInput();
        }
    }

    private void validateSeoulOffset(OffsetDateTime dateTime) {
        if (dateTime == null || !REQUIRED_OFFSET.equals(dateTime.getOffset())) {
            throw invalidInput();
        }
    }

    private CreateContentCommand toContentCommand(CreateContentRequest request) {
        return new CreateContentCommand(
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
            request.publishAt().toInstant()
        );
    }

    private List<CreateContentSessionCommand> toSessionCommands(List<SessionRequest> sessions) {
        return sessions.stream()
            .map(session -> new CreateContentSessionCommand(
                session.startsAt().toInstant(),
                session.endsAt().toInstant(),
                session.checkinOpenAt().toInstant(),
                session.checkinCloseAt().toInstant(),
                session.capacity()
            ))
            .toList();
    }

    private Long parsePositiveImageObjectId(JsonNode value) {
        if (value == null || !value.isString()) {
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

    private void validateReservationPrice(Long reservationPrice) {
        if (reservationPrice == null || reservationPrice < 0) {
            throw invalidInput();
        }
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
