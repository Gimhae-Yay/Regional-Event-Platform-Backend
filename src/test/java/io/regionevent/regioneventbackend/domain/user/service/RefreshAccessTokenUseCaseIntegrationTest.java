package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshAccessTokenUseCaseIntegrationTest extends NonTransactionalMySqlTestSupport {

    @Autowired
    private RefreshAccessTokenUseCase refreshAccessTokenUseCase;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AppUserRepository appUserRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void reissue_같은유효토큰을반복제출해도각각AccessToken을발급한다() {
        AppUser user = createUser(AppUserStatus.ACTIVE);
        String refreshToken = refreshTokenService.issue(user.getUserId());

        var first = refreshAccessTokenUseCase.reissue(refreshToken);
        var second = refreshAccessTokenUseCase.reissue(refreshToken);

        assertThat(first.accessToken()).isNotBlank();
        assertThat(second.accessToken()).isNotBlank();
    }

    @Test
    void reissue_비활성사용자_미인증으로거부한다() {
        AppUser user = createUser(AppUserStatus.WITHDRAWING);
        String refreshToken = refreshTokenService.issue(user.getUserId());

        assertThatThrownBy(() -> refreshAccessTokenUseCase.reissue(refreshToken))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reissue_탈퇴로삭제된사용자_미인증으로거부한다() {
        AppUser user = createUser(AppUserStatus.ACTIVE);
        String refreshToken = refreshTokenService.issue(user.getUserId());
        appUserRepository.delete(user);
        appUserRepository.flush();

        assertThatThrownBy(() -> refreshAccessTokenUseCase.reissue(refreshToken))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private AppUser createUser(AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            "refresh-" + System.nanoTime() + "@example.com",
            "hashed-password",
            "갱신 사용자",
            "01012345678",
            status
        ));
    }
}
