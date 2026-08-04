package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.user.dto.LoginRequest;
import io.regionevent.regioneventbackend.domain.user.dto.LoginResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    AppUserService.class,
    UserRoleAssignmentService.class,
    LoginUseCase.class,
    LoginUseCaseMySqlTest.LoginTestConfiguration.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoginUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String EMAIL = "visitor@example.com";
    private static final String PASSWORD = "LocalStamp!2026";

    private final LoginUseCase loginUseCase;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    LoginUseCaseMySqlTest(
        LoginUseCase loginUseCase,
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.loginUseCase = loginUseCase;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @Timeout(10)
    void 로그인_토큰_발급_중에는_탈퇴_상태_전환이_대기한다() throws Exception {
        AppUser user = createActiveUser();
        CountDownLatch refreshTokenIssuanceStarted = new CountDownLatch(1);
        CountDownLatch releaseRefreshTokenIssuance = new CountDownLatch(1);
        Long userId = user.getUserId();

        doAnswer(invocation -> {
            refreshTokenIssuanceStarted.countDown();
            await(releaseRefreshTokenIssuance);
            return "refresh-token";
        }).when(refreshTokenService).issue(userId);
        when(jwtAccessTokenService.issue(userId)).thenReturn("access-token");

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<LoginResult> login = executorService.submit(
                () -> loginUseCase.login(new LoginRequest(EMAIL, PASSWORD))
            );
            assertThat(refreshTokenIssuanceStarted.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Integer> withdrawal = executorService.submit(
                () -> inTransaction(() -> jdbcTemplate.update(
                    "UPDATE app_user SET status = 'WITHDRAWING' WHERE user_id = ? AND status = 'ACTIVE'",
                    userId
                ))
            );
            try {
                assertThatThrownBy(() -> withdrawal.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            } finally {
                releaseRefreshTokenIssuance.countDown();
            }

            assertThat(login.get(3, TimeUnit.SECONDS).response().userId()).isEqualTo(userId.toString());
            assertThat(withdrawal.get(3, TimeUnit.SECONDS)).isOne();
        }

        assertThat(appUserRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(AppUserStatus.WITHDRAWING);
    }

    private AppUser createActiveUser() {
        return inTransaction(() -> appUserRepository.saveAndFlush(new AppUser(
            EMAIL,
            passwordEncoder.encode(PASSWORD),
            "홍길동",
            "01012345678",
            AppUserStatus.ACTIVE
        )));
    }

    private <T> T inTransaction(TransactionalSupplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }

    @FunctionalInterface
    private interface TransactionalSupplier<T> {

        T get();
    }

    @TestConfiguration
    static class LoginTestConfiguration {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        RefreshTokenService refreshTokenService() {
            return mock(RefreshTokenService.class);
        }

        @Bean
        JwtAccessTokenService jwtAccessTokenService() {
            return mock(JwtAccessTokenService.class);
        }
    }
}
