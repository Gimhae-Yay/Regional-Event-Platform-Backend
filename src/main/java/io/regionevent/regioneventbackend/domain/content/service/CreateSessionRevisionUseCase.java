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
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.SessionRevisionService.CreateSessionRevisionCommand;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateSessionRevisionUseCase {

    private static final ZoneOffset REQUIRED_OFFSET = ZoneOffset.ofHours(9);
    private static final List<ContentStatus> REVISION_CREATABLE_CONTENT_STATUSES = List.of(
        ContentStatus.APPROVED,
        ContentStatus.PUBLISHED
    );

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final SessionRevisionService sessionRevisionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public CreateSessionRevisionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        SessionRevisionService sessionRevisionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.sessionRevisionService = sessionRevisionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CreateSessionRevisionResult create(
        Long userId,
        Long sessionId,
        CreateContentSessionRequest request,
        UUID requestId
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Long contentId = contentSessionService.findContentIdBySessionId(sessionId);
        Content content = contentService.findOwnedContentForRevisionCreation(
            contentId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
        ContentSession targetSession = contentSessionService.findRevisionTargetForUpdate(sessionId);
        validateTarget(content, targetSession, request.startsAt().toInstant());

        Instant submittedAt = clock.instant();
        SessionRevision revision = sessionRevisionService.createPending(
            targetSession,
            operator.user(),
            toCommand(request),
            submittedAt
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            targetSession.getSessionId(),
            null,
            SessionRevisionStatus.PENDING.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(operator.roleAssignment()),
            submittedAt
        ));
        return CreateSessionRevisionResult.from(revision);
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
        if (!startsAt.isBefore(endsAt)
            || !checkinOpenAt.isBefore(checkinCloseAt)
            || !endsAt.isAfter(checkinCloseAt)) {
            throw invalidInput();
        }
    }

    private void validateTarget(
        Content content,
        ContentSession targetSession,
        Instant startsAt
    ) {
        if (!REVISION_CREATABLE_CONTENT_STATUSES.contains(content.getStatus())
            || targetSession.getStatus() != ContentSessionStatus.SCHEDULED
            || !contentSessionService.isBeforeStartByDatabaseTime(targetSession.getSessionId())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
        if (!startsAt.isAfter(clock.instant()) || !startsAt.isAfter(content.getPublishAt())) {
            throw invalidInput();
        }
    }

    private void validateSeoulOffset(OffsetDateTime dateTime) {
        if (dateTime == null || !REQUIRED_OFFSET.equals(dateTime.getOffset())) {
            throw invalidInput();
        }
    }

    private CreateSessionRevisionCommand toCommand(CreateContentSessionRequest request) {
        return new CreateSessionRevisionCommand(
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
