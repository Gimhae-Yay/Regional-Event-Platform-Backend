package io.regionevent.regioneventbackend.domain.user.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
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
        validateLoginIdentifierAvailable(email);

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

    public void startWithdrawal(AppUser user) {
        user.startWithdrawal();
        appUserRepository.saveAndFlush(user);
    }

    public void delete(AppUser user) {
        appUserRepository.delete(user);
    }

    private void validateLoginIdentifierAvailable(String email) {
        if (appUserRepository.existsByLoginIdentifier(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER);
        }
    }
}
