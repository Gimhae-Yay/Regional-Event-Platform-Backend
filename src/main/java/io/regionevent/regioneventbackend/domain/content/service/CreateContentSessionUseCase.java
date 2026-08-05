package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService.CreateContentSessionCommand;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateContentSessionUseCase {

    private static final ZoneOffset REQUIRED_OFFSET = ZoneOffset.ofHours(9);
    private static final List<ContentStatus> CREATABLE_CONTENT_STATUSES = List.of(
        ContentStatus.APPROVED,
        ContentStatus.PUBLISHED
    );

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public CreateContentSessionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CreateContentSessionResult create(
        Long userId,
        Long contentId,
        CreateContentSessionRequest request,
        UUID requestId
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Content content = contentService.findOwnedContentForRevisionCreation(
            contentId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
        try {
            validateTarget(content, request.startsAt().toInstant());
            ContentSession contentSession = contentSessionService.createPendingSessions(
                content,
                operator.region(),
                List.of(toCommand(request))
            ).getFirst();
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                content.getRegion(),
                AuditEventTargetType.CONTENT_SESSION,
                contentSession.getSessionId(),
                null,
                ContentSessionStatus.PENDING.name(),
                AuditEventResult.SUCCESS,
                null,
                new AuditEventActor(operator.roleAssignment()),
                clock.instant()
            ));
            return CreateContentSessionResult.from(contentSession);
        } catch (BusinessException exception) {
            recordEndedContentFailure(requestId, content, operator, exception);
            throw exception;
        }
    }

    private void validateRequest(CreateContentSessionRequest request) {
        if (request == null) {
            throw invalidInput();
        }
        validateSeoulOffset(request.startsAt());
        validateSeoulOffset(request.endsAt());
        validateSeoulOffset(request.checkinOpenAt());
        validateSeoulOffset(request.checkinCloseAt());
        if (request.capacity() == null || request.capacity() <= 0) {
            throw invalidInput();
        }

        Instant startsAt = request.startsAt().toInstant();
        Instant endsAt = request.endsAt().toInstant();
        Instant checkinOpenAt = request.checkinOpenAt().toInstant();
        Instant checkinCloseAt = request.checkinCloseAt().toInstant();
        if (!startsAt.isAfter(clock.instant())
            || !startsAt.isBefore(endsAt)
            || !checkinOpenAt.isBefore(checkinCloseAt)
            || !endsAt.isAfter(checkinCloseAt)) {
            throw invalidInput();
        }
    }

    private void validateTarget(Content content, Instant startsAt) {
        if (!CREATABLE_CONTENT_STATUSES.contains(content.getStatus())
            || !startsAt.isAfter(content.getPublishAt())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void recordEndedContentFailure(
        UUID requestId,
        Content content,
        AuthorizedOperator operator,
        BusinessException exception
    ) {
        if (content.getStatus() != ContentStatus.ENDED || exception.getErrorCode() != ErrorCode.NOT_FOUND) {
            return;
        }
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentStatus.ENDED.name(),
            null,
            AuditEventResult.FAILURE,
            ErrorCode.NOT_FOUND.name(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant()
        ));
    }

    private void validateSeoulOffset(OffsetDateTime dateTime) {
        if (dateTime == null || !REQUIRED_OFFSET.equals(dateTime.getOffset())) {
            throw invalidInput();
        }
    }

    private CreateContentSessionCommand toCommand(CreateContentSessionRequest request) {
        return new CreateContentSessionCommand(
            request.startsAt().toInstant(),
            request.endsAt().toInstant(),
            request.checkinOpenAt().toInstant(),
            request.checkinCloseAt().toInstant(),
            request.capacity()
        );
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
