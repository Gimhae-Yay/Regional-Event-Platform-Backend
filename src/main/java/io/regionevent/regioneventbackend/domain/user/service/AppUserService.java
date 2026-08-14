package io.regionevent.regioneventbackend.domain.user.service;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminUserListProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser createActiveUser(
        String email,
        String password,
        String name,
        String phone
    ) {
        return createActiveUser(email, password, name, phone, AppUserAccountKind.ORDINARY);
    }

    public AppUser createActivePrivilegedUser(
        String email,
        String password,
        String name,
        String phone
    ) {
        return createActiveUser(email, password, name, phone, AppUserAccountKind.PRIVILEGED);
    }

    public AppUser authenticate(String email, String password) {
        return appUserRepository.findByLoginIdentifier(email)
            .filter(user -> user.getStatus() == AppUserStatus.ACTIVE)
            .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    }

    public AppUser findActiveUser(Long userId) {
        return appUserRepository.findById(userId)
            .filter(user -> user.getStatus() == AppUserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    public Optional<AppUser> findActiveUserForUpdate(Long userId) {
        return appUserRepository.findByIdForUpdate(userId)
            .filter(user -> user.getStatus() == AppUserStatus.ACTIVE);
    }

    public List<AppUser> findUsersForUpdate(Long firstUserId, Long secondUserId) {
        if (firstUserId == null && secondUserId == null) {
            return List.of();
        }
        if (firstUserId == null) {
            return appUserRepository.findAllByUserIdInForUpdate(List.of(secondUserId));
        }
        if (secondUserId == null || firstUserId.equals(secondUserId)) {
            return appUserRepository.findAllByUserIdInForUpdate(List.of(firstUserId));
        }
        return appUserRepository.findAllByUserIdInForUpdate(List.of(firstUserId, secondUserId));
    }

    public void startWithdrawal(AppUser user) {
        user.startWithdrawal();
        appUserRepository.saveAndFlush(user);
    }

    public void delete(AppUser user) {
        appUserRepository.delete(user);
        appUserRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminUserListProjection> findPlatformAdminUserList() {
        return appUserRepository.findPlatformAdminUserList();
    }

    private void validateLoginIdentifierAvailable(String email) {
        if (appUserRepository.existsByLoginIdentifier(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER);
        }
    }

    private AppUser createActiveUser(
        String email,
        String password,
        String name,
        String phone,
        AppUserAccountKind accountKind
    ) {
        validateLoginIdentifierAvailable(email);

        try {
            return appUserRepository.saveAndFlush(new AppUser(
                email,
                passwordEncoder.encode(password),
                name,
                phone,
                accountKind,
                AppUserStatus.ACTIVE
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER, exception);
        }
    }
}
