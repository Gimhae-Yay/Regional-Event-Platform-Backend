package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class AppUserServiceTest {

    @Test
    void createActiveUser_whenLoginIdentifierUniqueConstraintIsViolated_throwsDuplicateLoginIdentifierException() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AppUserService appUserService = new AppUserService(appUserRepository, passwordEncoder);

        when(appUserRepository.existsByLoginIdentifier("visitor@example.com")).thenReturn(false);
        when(passwordEncoder.encode("LocalStamp!2026")).thenReturn("{bcrypt}password-hash");
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
            .thenThrow(new DataIntegrityViolationException("uk_app_user_login_identifier"));

        assertThatThrownBy(() -> appUserService.createActiveUser(
            "visitor@example.com",
            "LocalStamp!2026",
            "visitor",
            "01012345678"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER)
        );
    }

    @Test
    void findActiveUserForUpdate_whenUserIsWithdrawing_returnsEmpty() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AppUserService appUserService = new AppUserService(appUserRepository, passwordEncoder);
        AppUser user = mock(AppUser.class);

        when(appUserRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(AppUserStatus.WITHDRAWING);

        assertThat(appUserService.findActiveUserForUpdate(1L)).isEmpty();
    }

    @Test
    void findActiveOrdinaryUser_whenPrivilegedUser_throwsForbidden() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AppUserService appUserService = new AppUserService(appUserRepository, passwordEncoder);
        AppUser user = mock(AppUser.class);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(user.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);

        assertThatThrownBy(() -> appUserService.findActiveOrdinaryUser(1L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }
}
