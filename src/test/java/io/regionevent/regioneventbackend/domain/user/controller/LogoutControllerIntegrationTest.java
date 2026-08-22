package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.service.RefreshAccessTokenUseCase;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutControllerIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://frontend.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshAccessTokenUseCase refreshAccessTokenUseCase;

    @Test
    void logout_제출된토큰을검증하지않고Cookie를만료한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .cookie(new Cookie("refreshToken", "copied-token")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void logout_복사된RefreshToken은만료전까지재발급에사용할수있다() throws Exception {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            "logout-" + System.nanoTime() + "@example.com",
            "hashed-password",
            "로그아웃 사용자",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
        String copiedRefreshToken = refreshTokenService.issue(user.getUserId());

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .cookie(new Cookie("refreshToken", copiedRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        assertThat(refreshAccessTokenUseCase.reissue(copiedRefreshToken).accessToken())
            .isNotBlank();
    }
}
