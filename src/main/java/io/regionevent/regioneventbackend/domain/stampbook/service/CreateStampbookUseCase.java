package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateStampbookUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final CouponPolicyService couponPolicyService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public CreateStampbookUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        CouponPolicyService couponPolicyService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.couponPolicyService = couponPolicyService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CreateStampbookResult create(
        Long userId,
        CreateStampbookCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        validateRegionScope(operator, command.regionId());

        List<Content> contents = contentService.findOwnedContentsForStampbookCreation(
            command.contentIds(),
            operator.user().getUserId(),
            command.regionId()
        );
        CouponPolicy rewardCouponPolicy = couponPolicyService.findStampbookRewardPolicy(
            command.rewardCouponPolicyId(),
            command.regionId()
        );
        Instant createdAt = clock.instant();
        Stampbook stampbook = stampbookService.create(
            operator.region(),
            rewardCouponPolicy,
            command.title()
        );
        stampbookContentService.connect(stampbook, contents);
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            operator.region(),
            AuditEventTargetType.STAMPBOOK,
            stampbook.getStampbookId(),
            null,
            StampbookStatus.DRAFT.name(),
            AuditEventResult.SUCCESS,
            null,
            command.reason(),
            null,
            new AuditEventActor(operator.roleAssignment()),
            createdAt
        ));
        return CreateStampbookResult.from(stampbook, contents.size(), createdAt);
    }

    private void validateCommand(CreateStampbookCommand command) {
        if (command == null
            || command.title() == null
            || command.title().isBlank()
            || command.title().length() > 100
            || command.regionId() == null
            || command.regionId() <= 0
            || command.rewardCouponPolicyId() == null
            || command.rewardCouponPolicyId() <= 0
            || command.contentIds() == null
            || command.contentIds().isEmpty()
            || command.reason() == null
            || command.reason().isBlank()
            || command.reason().length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (command.contentIds().stream().anyMatch(contentId -> contentId == null || contentId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<Long> distinctContentIds = new HashSet<>(command.contentIds());
        if (distinctContentIds.size() != command.contentIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Long regionId
    ) {
        if (!operator.region().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public record CreateStampbookCommand(
        String title,
        Long regionId,
        List<Long> contentIds,
        Long rewardCouponPolicyId,
        String reason
    ) {

        public CreateStampbookCommand {
            title = title == null ? null : title.strip();
            contentIds = contentIds == null ? null : List.copyOf(contentIds);
            reason = reason == null ? null : reason.strip();
        }
    }
}
