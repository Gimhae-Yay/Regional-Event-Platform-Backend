package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class EndStampbookUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;
    private final StampbookProgressService stampbookProgressService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public EndStampbookUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        StampbookProgressService stampbookProgressService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.stampbookProgressService = stampbookProgressService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public EndStampbookResult end(
        Long userId,
        EndStampbookCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Stampbook stampbook = stampbookService.findForUpdate(command.stampbookId());
        validateRegionScope(operator, stampbook);
        stampbookService.validatePublished(stampbook);
        validateTargetContents(operator, stampbook);

        Instant endedAt = clock.instant();
        stampbook.end(endedAt);
        stampbookProgressService.endIncompleteProgresses(stampbook.getStampbookId());
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            stampbook.getRegion(),
            AuditEventTargetType.STAMPBOOK,
            stampbook.getStampbookId(),
            StampbookStatus.PUBLISHED.name(),
            StampbookStatus.ENDED.name(),
            AuditEventResult.SUCCESS,
            null,
            command.reason(),
            null,
            new AuditEventActor(operator.roleAssignment()),
            endedAt
        ));
        return EndStampbookResult.from(stampbook, endedAt);
    }

    private void validateCommand(EndStampbookCommand command) {
        if (command == null
            || command.stampbookId() == null
            || command.stampbookId() <= 0
            || command.reason() == null
            || command.reason().isBlank()
            || command.reason().length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Stampbook stampbook
    ) {
        if (!operator.region().getRegionId().equals(stampbook.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateTargetContents(
        AuthorizedOperator operator,
        Stampbook stampbook
    ) {
        List<Long> contentIds = stampbookContentService.findContentIds(stampbook.getStampbookId());
        contentService.findOwnedContentsForStampbookCreation(
            contentIds,
            operator.user().getUserId(),
            stampbook.getRegion().getRegionId()
        );
    }

    public record EndStampbookCommand(
        Long stampbookId,
        String reason
    ) {

        public EndStampbookCommand {
            reason = reason == null ? null : reason.strip();
        }
    }
}
