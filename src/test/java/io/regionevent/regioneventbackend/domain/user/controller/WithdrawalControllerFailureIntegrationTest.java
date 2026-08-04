package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WithdrawalControllerFailureIntegrationTest.WithdrawalFailureTestConfiguration.class)
class WithdrawalControllerFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private ReviewRepository reviewRepository;

    @Test
    void withdraw_whenMySqlTerminationFails_keepsAccountAndDoesNotRestoreRevokedRefreshTokens() throws Exception {
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

        mockMvc.perform(delete("/api/v1/auth/delete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(appUserRepository.findById(user.getUserId()))
            .hasValueSatisfying(unchanged -> assertThat(unchanged.getStatus()).isEqualTo(AppUserStatus.ACTIVE));
        verify(refreshTokenStore).revokeAllFamilies(user.getUserId());
    }

    @TestConfiguration
    static class WithdrawalFailureTestConfiguration {

        @Bean
        @Primary
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }
    }
}
