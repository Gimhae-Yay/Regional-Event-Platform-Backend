package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.dto.SignupRequest;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class SignupServiceTest {

    @Test
    void signup_whenLoginIdentifierUniqueConstraintIsViolated_throwsDuplicateLoginIdentifierException() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SignupService signupService = new SignupService(
            appUserRepository,
            mock(UserRoleAssignmentRepository.class),
            mock(OperatorApplicationRepository.class),
            mock(RegionRepository.class),
            passwordEncoder
        );
        SignupRequest request = new SignupRequest(
            "visitor@example.com",
            "LocalStamp!2026",
            "홍길동",
            "01012345678",
            "VISITOR",
            null,
            null
        );
        when(appUserRepository.existsByLoginIdentifier("visitor@example.com")).thenReturn(false);
        when(passwordEncoder.encode("LocalStamp!2026")).thenReturn("{bcrypt}password-hash");
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
            .thenThrow(new DataIntegrityViolationException("uk_app_user_login_identifier"));

        assertThatThrownBy(() -> signupService.signup(request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER)
            );
    }
}
