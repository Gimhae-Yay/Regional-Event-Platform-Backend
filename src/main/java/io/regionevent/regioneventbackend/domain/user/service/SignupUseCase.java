package io.regionevent.regioneventbackend.domain.user.service;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.service.OperatorApplicationService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.dto.SignupRequest;
import io.regionevent.regioneventbackend.domain.user.dto.SignupResponse;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SignupUseCase {

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_BUSINESS_INFORMATION_LENGTH = 2_000;

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final OperatorApplicationService operatorApplicationService;
    private final RegionService regionService;

    public SignupUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        OperatorApplicationService operatorApplicationService,
        RegionService regionService
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.operatorApplicationService = operatorApplicationService;
        this.regionService = regionService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validatePasswordByteLength(request.password());
        UserRole requestedRole = toRequestedRole(request.requestedRole());

        if (requestedRole == UserRole.VISITOR) {
            validateVisitorRequest(request);
            AppUser user = appUserService.createActiveUser(
                request.email(),
                request.password(),
                request.name(),
                request.phone()
            );
            userRoleAssignmentService.assignVisitor(user);
            return new SignupResponse(
                user.getUserId().toString(),
                UserRole.VISITOR.name(),
                UserRole.VISITOR.name(),
                null
            );
        }

        Region requestedRegion = regionService.findPublicRegion(
            toPositiveRegionId(request.requestedRegionId())
        );
        validateBusinessInformation(request.businessInformation());
        AppUser user = appUserService.createActiveUser(
            request.email(),
            request.password(),
            request.name(),
            request.phone()
        );
        operatorApplicationService.createPendingApplication(
            user,
            requestedRegion,
            request.businessInformation()
        );
        return new SignupResponse(
            user.getUserId().toString(),
            UserRole.OPERATOR.name(),
            null,
            OperatorApplicationStatus.PENDING.name()
        );
    }

    private void validateVisitorRequest(SignupRequest request) {
        if (request.requestedRegionId() != null || request.businessInformation() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePasswordByteLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private UserRole toRequestedRole(String requestedRole) {
        if (UserRole.VISITOR.name().equals(requestedRole)) {
            return UserRole.VISITOR;
        }
        if (UserRole.OPERATOR.name().equals(requestedRole)) {
            return UserRole.OPERATOR;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private Long toPositiveRegionId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            Long id = Long.valueOf(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateBusinessInformation(String businessInformation) {
        if (businessInformation == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (businessInformation.isEmpty()
            || businessInformation.length() > MAX_BUSINESS_INFORMATION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
