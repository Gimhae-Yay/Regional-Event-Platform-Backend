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
import io.regionevent.regioneventbackend.domain.operator.dto.ApproveOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveOperatorApplicationUseCase {

    private static final String APPROVED_REASON_CODE = "OPERATOR_APPLICATION_APPROVED";

    private final OperatorApplicationService operatorApplicationService;
    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ApproveOperatorApplicationUseCase(
        OperatorApplicationService operatorApplicationService,
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorApplicationService = operatorApplicationService;
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveOperatorApplicationResponse approve(
        Long reviewerUserId,
        Long operatorApplicationId,
        UUID requestId
    ) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(reviewerUserId);
        OperatorApplicationStatus status = operatorApplicationService.findApprovalStatus(
            operatorApplicationId,
            regionId
        );
        if (status == OperatorApplicationStatus.APPROVED) {
            return ApproveOperatorApplicationResponse.from(
                operatorApplicationService.findApprovalTargetForUpdate(operatorApplicationId, regionId)
            );
        }
        if (status != OperatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT);
        }
        AppUser applicant = appUserService.findActiveUserForUpdate(
            operatorApplicationService.findApprovalApplicantUserId(operatorApplicationId, regionId)
        )
            .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT));
        OperatorApplication application = operatorApplicationService.findApprovalTargetForUpdate(
            operatorApplicationId,
            regionId
        );

        if (application.getStatus() == OperatorApplicationStatus.APPROVED) {
            return ApproveOperatorApplicationResponse.from(application);
        }
        if (application.getStatus() != OperatorApplicationStatus.PENDING || application.getApplicant() == null) {
            throw new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT);
        }

        UserRoleAssignment reviewerAssignment = regionAdminAuthorizationService.authorize(reviewerUserId, regionId);
        Instant approvedAt = clock.instant();
        application.approve(reviewerAssignment.getAppUser(), approvedAt);
        userRoleAssignmentService.assignOperator(applicant, application.getRequestedRegion());
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            application.getRequestedRegion(),
            AuditEventTargetType.OPERATOR_APPLICATION,
            application.getOperatorApplicationId(),
            OperatorApplicationStatus.PENDING.name(),
            OperatorApplicationStatus.APPROVED.name(),
            AuditEventResult.SUCCESS,
            APPROVED_REASON_CODE,
            new AuditEventActor(reviewerAssignment),
            application.getUpdatedAt()
        ));
        return ApproveOperatorApplicationResponse.from(application);
    }
}
