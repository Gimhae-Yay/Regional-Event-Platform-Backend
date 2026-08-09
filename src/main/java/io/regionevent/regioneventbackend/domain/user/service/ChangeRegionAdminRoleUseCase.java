package io.regionevent.regioneventbackend.domain.user.service;

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
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ChangeRegionAdminRoleUseCase {

    private static final String REGION_ADMIN_REASSIGNMENT = "REGION_ADMIN_REASSIGNMENT";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final RegionService regionService;
    private final ContentService contentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ChangeRegionAdminRoleUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        RegionService regionService,
        ContentService contentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.regionService = regionService;
        this.contentService = contentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RegionAdminRoleChangeResult change(
        Long actorUserId,
        Long targetUserId,
        RegionAdminRoleChange roleChange,
        Long regionId,
        String reasonCode,
        String evidenceReference,
        UUID requestId
    ) {
        PlatformAdminAssignment actor = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        AppUser targetUser = appUserService.findActiveUserForUpdate(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOrdinaryTarget(targetUser);

        UserRoleAssignment activeAssignment = userRoleAssignmentService
            .findActiveRegionAdminForUpdate(targetUserId)
            .orElse(null);
        Instant changedAt = clock.instant();

        return switch (roleChange) {
            case REGION_ADMIN -> appoint(
                targetUser,
                activeAssignment,
                regionId,
                reasonCode,
                evidenceReference,
                requestId,
                actor,
                changedAt
            );
            case NONE -> revoke(
                activeAssignment,
                reasonCode,
                evidenceReference,
                requestId,
                actor,
                changedAt
            );
        };
    }

    private RegionAdminRoleChangeResult appoint(
        AppUser targetUser,
        UserRoleAssignment activeAssignment,
        Long requestedRegionId,
        String reasonCode,
        String evidenceReference,
        UUID requestId,
        PlatformAdminAssignment actor,
        Instant changedAt
    ) {
        if (activeAssignment != null
            && activeAssignment.getRegion().getRegionId().equals(requestedRegionId)) {
            return RegionAdminRoleChangeResult.active(activeAssignment);
        }

        Region requestedRegion;
        if (activeAssignment == null) {
            requestedRegion = regionService.findRegionForUpdate(requestedRegionId);
        } else {
            RegionLockResult regions = lockRegions(
                activeAssignment.getRegion().getRegionId(),
                requestedRegionId
            );
            validateRevocationAllowed(regions.previousRegion());
            UserRoleAssignment revokedAssignment = userRoleAssignmentService.revoke(
                activeAssignment,
                changedAt,
                REGION_ADMIN_REASSIGNMENT
            );
            recordAudit(
                revokedAssignment,
                UserRoleAssignmentStatus.ACTIVE,
                UserRoleAssignmentStatus.REVOKED,
                reasonCode,
                evidenceReference,
                requestId,
                actor,
                changedAt
            );
            requestedRegion = regions.requestedRegion();
        }

        UserRoleAssignment assigned = userRoleAssignmentService.assignRegionAdmin(
            targetUser,
            requestedRegion,
            changedAt
        );
        recordAudit(
            assigned,
            null,
            UserRoleAssignmentStatus.ACTIVE,
            reasonCode,
            evidenceReference,
            requestId,
            actor,
            changedAt
        );
        return RegionAdminRoleChangeResult.active(assigned);
    }

    private RegionAdminRoleChangeResult revoke(
        UserRoleAssignment activeAssignment,
        String reasonCode,
        String evidenceReference,
        UUID requestId,
        PlatformAdminAssignment actor,
        Instant changedAt
    ) {
        if (activeAssignment == null) {
            throw new BusinessException(ErrorCode.ROLE_ASSIGNMENT_CONFLICT);
        }

        Region region = regionService.findRegionForUpdate(activeAssignment.getRegion().getRegionId());
        validateRevocationAllowed(region);
        UserRoleAssignment revoked = userRoleAssignmentService.revoke(
            activeAssignment,
            changedAt,
            reasonCode
        );
        recordAudit(
            revoked,
            UserRoleAssignmentStatus.ACTIVE,
            UserRoleAssignmentStatus.REVOKED,
            reasonCode,
            evidenceReference,
            requestId,
            actor,
            changedAt
        );
        return RegionAdminRoleChangeResult.revoked(revoked);
    }

    private RegionLockResult lockRegions(Long previousRegionId, Long requestedRegionId) {
        if (previousRegionId < requestedRegionId) {
            Region previousRegion = regionService.findRegionForUpdate(previousRegionId);
            Region requestedRegion = regionService.findRegionForUpdate(requestedRegionId);
            return new RegionLockResult(previousRegion, requestedRegion);
        }

        Region requestedRegion = regionService.findRegionForUpdate(requestedRegionId);
        Region previousRegion = regionService.findRegionForUpdate(previousRegionId);
        return new RegionLockResult(previousRegion, requestedRegion);
    }

    private void validateOrdinaryTarget(AppUser targetUser) {
        if (targetUser.getAccountKind() != AppUserAccountKind.ORDINARY) {
            throw new BusinessException(ErrorCode.ROLE_ASSIGNMENT_CONFLICT);
        }
    }

    private void validateRevocationAllowed(Region region) {
        if (contentService.hasUndeletedContentInRegion(region.getRegionId())
            && userRoleAssignmentService.countActiveRegionAdmins(region.getRegionId()) <= 1) {
            throw new BusinessException(ErrorCode.ROLE_ASSIGNMENT_CONFLICT);
        }
    }

    private void recordAudit(
        UserRoleAssignment assignment,
        UserRoleAssignmentStatus previousStatus,
        UserRoleAssignmentStatus nextStatus,
        String reasonCode,
        String evidenceReference,
        UUID requestId,
        PlatformAdminAssignment actor,
        Instant changedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            assignment.getRegion(),
            AuditEventTargetType.USER_ROLE_ASSIGNMENT,
            assignment.getRoleAssignmentId(),
            previousStatus == null ? null : previousStatus.name(),
            nextStatus.name(),
            AuditEventResult.SUCCESS,
            reasonCode,
            evidenceReference,
            new AuditEventActor(actor),
            changedAt
        ));
    }

    private record RegionLockResult(Region previousRegion, Region requestedRegion) {
    }
}
