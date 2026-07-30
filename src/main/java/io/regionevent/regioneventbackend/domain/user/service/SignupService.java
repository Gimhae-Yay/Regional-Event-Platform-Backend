package io.regionevent.regioneventbackend.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.dto.SignupRequest;
import io.regionevent.regioneventbackend.domain.user.dto.SignupResponse;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SignupService {

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_BUSINESS_INFORMATION_LENGTH = 2_000;

    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final RegionRepository regionRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        RegionRepository regionRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.regionRepository = regionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String name = normalizeRequiredText(request.name());
        String phone = normalizePhone(request.phone());
        validatePasswordByteLength(request.password());
        UserRole requestedRole = toRequestedRole(request.requestedRole());

        validateDuplicateLoginIdentifier(email);
        Region requestedRegion = requestedRole == UserRole.OPERATOR
            ? findPublicRequestedRegion(request.requestedRegionId())
            : validateVisitorRequest(request);
        String businessInformation = requestedRole == UserRole.OPERATOR
            ? normalizeBusinessInformation(request.businessInformation())
            : null;

        AppUser user = saveUser(email, request.password(), name, phone);
        if (requestedRole == UserRole.VISITOR) {
            userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.VISITOR, null));
            return new SignupResponse(user.getUserId().toString(), UserRole.VISITOR.name(), UserRole.VISITOR.name(), null);
        }

        operatorApplicationRepository.save(new OperatorApplication(
            user,
            requestedRegion,
            businessInformation,
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
        return new SignupResponse(user.getUserId().toString(), UserRole.OPERATOR.name(), null, OperatorApplicationStatus.PENDING.name());
    }

    private void validateDuplicateLoginIdentifier(String email) {
        if (appUserRepository.existsByLoginIdentifier(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER);
        }
    }

    private Region findPublicRequestedRegion(String requestedRegionId) {
        Long regionId = toPositiveId(requestedRegionId);
        return regionRepository.findByRegionIdAndIsPublicTrue(regionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Region validateVisitorRequest(SignupRequest request) {
        if (request.requestedRegionId() != null || request.businessInformation() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return null;
    }

    private AppUser saveUser(
        String email,
        String password,
        String name,
        String phone
    ) {
        try {
            return appUserRepository.saveAndFlush(new AppUser(
                email,
                passwordEncoder.encode(password),
                name,
                phone,
                AppUserStatus.ACTIVE
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER, exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value) {
        String normalizedValue = value.strip();
        if (normalizedValue.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedValue;
    }

    private String normalizePhone(String phone) {
        String normalizedPhone = phone.replace("-", "");
        if (!normalizedPhone.matches("\\d{10,11}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedPhone;
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

    private Long toPositiveId(String value) {
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

    private String normalizeBusinessInformation(String businessInformation) {
        if (businessInformation == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String normalizedBusinessInformation = businessInformation.strip();
        if (normalizedBusinessInformation.isEmpty()
            || normalizedBusinessInformation.length() > MAX_BUSINESS_INFORMATION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedBusinessInformation;
    }
}
