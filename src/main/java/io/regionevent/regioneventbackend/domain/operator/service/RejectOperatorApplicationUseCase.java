package io.regionevent.regioneventbackend.domain.operator.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.operator.dto.RejectOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectOperatorApplicationUseCase {

    private static final String REJECTED_REASON_CODE = "OPERATOR_APPLICATION_REJECTED";

    private final OperatorApplicationService operatorApplicationService;
    private final AppUserService appUserService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public RejectOperatorApplicationUseCase(
        OperatorApplicationService operatorApplicationService,
        AppUserService appUserService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorApplicationService = operatorApplicationService;
        this.appUserService = appUserService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RejectOperatorApplicationResponse reject(
        Long reviewerUserId,
        Long operatorApplicationId,
        String rejectedReason,
        UUID requestId
    ) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(reviewerUserId);
        OperatorApplicationStatus status = operatorApplicationService.findReviewStatus(
            operatorApplicationId,
            regionId
        );
        if (status == OperatorApplicationStatus.REJECTED) {
            return RejectOperatorApplicationResponse.from(
                operatorApplicationService.findReviewTargetForUpdate(operatorApplicationId, regionId)
            );
        }
        if (status != OperatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT);
        }
        appUserService.findActiveUserForUpdate(
            operatorApplicationService.findReviewApplicantUserId(operatorApplicationId, regionId)
        ).orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT));
        OperatorApplication application = operatorApplicationService.findReviewTargetForUpdate(
            operatorApplicationId,
            regionId
        );
        if (application.getStatus() == OperatorApplicationStatus.REJECTED) {
            return RejectOperatorApplicationResponse.from(application);
        }
        if (application.getStatus() != OperatorApplicationStatus.PENDING || application.getApplicant() == null) {
            throw new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT);
        }

        Instant rejectedAt = clock.instant();
        UserRoleAssignment reviewerAssignment = regionAdminAuthorizationService.authorize(reviewerUserId, regionId);
        application.reject(reviewerAssignment.getAppUser(), rejectedReason, rejectedAt);
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            application.getRequestedRegion(),
            AuditEventTargetType.OPERATOR_APPLICATION,
            application.getOperatorApplicationId(),
            OperatorApplicationStatus.PENDING.name(),
            OperatorApplicationStatus.REJECTED.name(),
            AuditEventResult.SUCCESS,
            REJECTED_REASON_CODE,
            new AuditEventActor(reviewerAssignment),
            application.getUpdatedAt()
        ));
        return RejectOperatorApplicationResponse.from(application);
    }
}
