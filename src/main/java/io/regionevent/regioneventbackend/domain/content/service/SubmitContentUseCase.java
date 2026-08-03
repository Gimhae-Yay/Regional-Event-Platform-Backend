package io.regionevent.regioneventbackend.domain.content.service;

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
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SubmitContentUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitContentUseCase.class);

    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public SubmitContentUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        OperatorAuthorizationService operatorAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public SubmitContentResult submit(
        Long userId,
        Long contentId,
        UUID requestId
    ) {
        Content content = null;
        ContentStatus failurePreviousState = null;
        try {
            AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
            content = contentService.findRejectedOwnedContentForUpdate(
                contentId,
                operator.user().getUserId(),
                operator.region().getRegionId()
            );
            failurePreviousState = content.getStatus();
            contentService.validateSubmitRequirements(content);
            contentSessionService.validatePendingSessionExists(contentId);

            Instant submittedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Content submittedContent = contentService.submitForReview(content, submittedAt);
            contentLogService.recordPending(submittedContent, operator.user(), submittedAt);
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                submittedContent.getRegion(),
                AuditEventTargetType.CONTENT,
                submittedContent.getContentId(),
                ContentStatus.REJECTED.name(),
                ContentStatus.PENDING.name(),
                AuditEventResult.SUCCESS,
                null,
                new AuditEventActor(operator.roleAssignment()),
                submittedAt
            ));
            return SubmitContentResult.from(submittedContent, submittedAt);
        } catch (BusinessException exception) {
            recordFailure(requestId, content, failurePreviousState, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, content, failurePreviousState, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void recordFailure(
        UUID requestId,
        Content content,
        ContentStatus previousState,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content == null ? null : content.getRegion(),
            AuditEventTargetType.CONTENT,
            content == null ? null : content.getContentId(),
            previousState == null ? null : previousState.name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            null,
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
        log.warn(
            "콘텐츠 승인 재요청을 거부했습니다. requestId={}, contentId={}, errorCode={}",
            requestId,
            content == null ? null : content.getContentId(),
            errorCode.code()
        );
    }
}
