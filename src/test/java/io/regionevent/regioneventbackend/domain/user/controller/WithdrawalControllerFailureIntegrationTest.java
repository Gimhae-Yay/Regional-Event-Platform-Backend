package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.WithdrawUserUseCase;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;

@DataJpaTest
@Import(AtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class WithdrawalControllerFailureIntegrationTest {

    @Autowired
    private WithdrawUserUseCase withdrawUserUseCase;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @MockitoBean
    private ReviewRepository reviewRepository;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @Test
    void withdraw_whenMySqlTerminationFails_keepsAccountAndDoesNotRestoreRevokedRefreshTokens() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            "mysql-failure@example.com",
            "password-hash",
            "홍길동",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        doThrow(new IllegalStateException("simulated MySQL termination failure"))
            .when(reviewRepository)
            .unlinkAuthorByUserId(user.getUserId());

        assertThatThrownBy(() -> withdrawUserUseCase.withdraw(user.getUserId()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(appUserRepository.findById(user.getUserId()))
            .hasValueSatisfying(unchanged -> assertThat(unchanged.getStatus()).isEqualTo(AppUserStatus.ACTIVE));
        verify(refreshTokenService).revokeAllFamilies(user.getUserId());
    }
}
