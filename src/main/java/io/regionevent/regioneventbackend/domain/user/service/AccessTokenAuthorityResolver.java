package io.regionevent.regioneventbackend.domain.user.service;

import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;

@Service
public class AccessTokenAuthorityResolver {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenAuthorityResolver.class);

    private final AppUserRepository appUserRepository;

    public AccessTokenAuthorityResolver(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public List<AccessTokenAuthority> resolve(AppUser user) {
        Long userId = requireUserId(user);
        List<UserRole> activeRoles = appUserRepository.findActiveUserRolesByUserId(userId);
        List<PlatformAdminGrade> activeGrades = appUserRepository.findActivePlatformAdminGradesByUserId(userId);

        if (isInconsistent(user.getAccountKind(), activeRoles, activeGrades)) {
            log.warn(
                "Access token authority source rejected. userId={}, accountKind={}, activeRoles={}, activeGrades={}",
                userId,
                user.getAccountKind(),
                activeRoles,
                activeGrades
            );
            throw new AccessTokenAuthoritySourceConflictException();
        }

        if (user.getAccountKind() == AppUserAccountKind.ORDINARY) {
            EnumSet<UserRole> uniqueRoles = EnumSet.noneOf(UserRole.class);
            uniqueRoles.addAll(activeRoles);
            return uniqueRoles
                .stream()
                .map(this::toAuthority)
                .toList();
        }
        return activeGrades.stream()
            .map(this::toAuthority)
            .toList();
    }

    private boolean isInconsistent(
        AppUserAccountKind accountKind,
        List<UserRole> activeRoles,
        List<PlatformAdminGrade> activeGrades
    ) {
        if (accountKind == AppUserAccountKind.ORDINARY) {
            return !activeGrades.isEmpty();
        }
        return !activeRoles.isEmpty() || activeGrades.size() > 1;
    }

    private AccessTokenAuthority toAuthority(UserRole role) {
        return switch (role) {
            case VISITOR -> AccessTokenAuthority.VISITOR;
            case OPERATOR -> AccessTokenAuthority.OPERATOR;
            case REGION_ADMIN -> AccessTokenAuthority.REGION_ADMIN;
        };
    }

    private AccessTokenAuthority toAuthority(PlatformAdminGrade grade) {
        return switch (grade) {
            case SUPER_ADMIN -> AccessTokenAuthority.SUPER_ADMIN;
            case PLATFORM_ADMIN -> AccessTokenAuthority.PLATFORM_ADMIN;
        };
    }

    private Long requireUserId(AppUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0) {
            throw new IllegalArgumentException("user must have a positive userId");
        }
        return user.getUserId();
    }
}
