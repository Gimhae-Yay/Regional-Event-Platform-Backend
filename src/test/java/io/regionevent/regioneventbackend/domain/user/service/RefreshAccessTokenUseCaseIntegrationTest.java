package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.JwtRefreshTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshToken;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenConflictException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshAccessTokenUseCaseIntegrationTest extends NonTransactionalMySqlTestSupport {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
        .withCommand("redis-server", "--maxmemory", "64mb", "--maxmemory-policy", "noeviction")
        .withExposedPorts(6379);

    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase;
    private final RefreshTokenService refreshTokenService;
    private final JwtRefreshTokenService jwtRefreshTokenService;
    private final AppUserRepository appUserRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    RefreshAccessTokenUseCaseIntegrationTest(
        RefreshAccessTokenUseCase refreshAccessTokenUseCase,
        RefreshTokenService refreshTokenService,
        JwtRefreshTokenService jwtRefreshTokenService,
        AppUserRepository appUserRepository,
        StringRedisTemplate stringRedisTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.refreshAccessTokenUseCase = refreshAccessTokenUseCase;
        this.refreshTokenService = refreshTokenService;
        this.jwtRefreshTokenService = jwtRefreshTokenService;
        this.appUserRepository = appUserRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @BeforeEach
    void clearRedis() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @Timeout(10)
    void 같은_토큰_갱신_경합은_사용자_행_잠금_중에도_충돌로_처리한다() throws Exception {
        AppUser user = createUser();
        String refreshToken = refreshTokenService.issue(user.getUserId());
        RefreshToken parsedToken = jwtRefreshTokenService.authenticate(refreshToken);
        CountDownLatch userLocked = new CountDownLatch(1);
        CountDownLatch releaseUserLock = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(3)) {
            Future<?> lockHolder = executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                appUserRepository.findByIdForUpdate(user.getUserId()).orElseThrow();
                userLocked.countDown();
                await(releaseUserLock);
            }));
            try {
                assertThat(userLocked.await(3, TimeUnit.SECONDS)).isTrue();

                Future<RefreshAccessTokenResult> firstRequest = executorService.submit(
                    () -> refreshAccessTokenUseCase.reissue(refreshToken)
                );
                awaitRotationMarker(parsedToken, firstRequest);

                Future<Throwable> secondRequest = executorService.submit(() -> {
                    try {
                        refreshAccessTokenUseCase.reissue(refreshToken);
                        return null;
                    } catch (Throwable exception) {
                        return exception;
                    }
                });
                assertThat(secondRequest.get(3, TimeUnit.SECONDS)).isInstanceOf(RefreshTokenConflictException.class);

                releaseUserLock.countDown();
                assertThat(firstRequest.get(3, TimeUnit.SECONDS).accessToken()).isNotBlank();
            } finally {
                releaseUserLock.countDown();
            }
            lockHolder.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(15)
    void 사용자_행_잠금으로_회전_표지가_만료되면_충돌로_처리한다() throws Exception {
        AppUser user = createUser();
        String refreshToken = refreshTokenService.issue(user.getUserId());
        RefreshToken parsedToken = jwtRefreshTokenService.authenticate(refreshToken);
        CountDownLatch userLocked = new CountDownLatch(1);
        CountDownLatch releaseUserLock = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> lockHolder = executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                appUserRepository.findByIdForUpdate(user.getUserId()).orElseThrow();
                userLocked.countDown();
                await(releaseUserLock, 10L);
            }));
            try {
                assertThat(userLocked.await(3, TimeUnit.SECONDS)).isTrue();

                Future<RefreshAccessTokenResult> firstRequest = executorService.submit(
                    () -> refreshAccessTokenUseCase.reissue(refreshToken)
                );
                awaitRotationMarker(parsedToken, firstRequest);
                awaitRotationMarkerExpiry(parsedToken);

                releaseUserLock.countDown();
                assertThatThrownBy(() -> firstRequest.get(3, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(RefreshTokenConflictException.class);
            } finally {
                releaseUserLock.countDown();
            }
            lockHolder.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void accessToken발급실패는_회전완료전에_기존토큰을유지하고_재시도를허용한다() {
        AppUser user = createUser();
        String refreshToken = refreshTokenService.issue(user.getUserId());
        RefreshToken currentToken = jwtRefreshTokenService.authenticate(refreshToken);
        doThrow(new IllegalStateException("access token issuance failed"))
            .when(jwtAccessTokenService)
            .issue(user.getUserId());

        assertThatThrownBy(() -> refreshAccessTokenUseCase.reissue(refreshToken))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("access token issuance failed");

        assertThat(stringRedisTemplate.opsForValue().get(activeFamilyKey(currentToken)))
            .isEqualTo(currentToken.tokenId().toString());
        assertThat(stringRedisTemplate.hasKey(consumedTokenKey(currentToken))).isFalse();
        assertThat(stringRedisTemplate.hasKey(rotationKey(currentToken))).isFalse();

        doReturn("retried-access-token")
            .when(jwtAccessTokenService)
            .issue(user.getUserId());
        RefreshAccessTokenResult result = refreshAccessTokenUseCase.reissue(refreshToken);

        assertThat(result.accessToken()).isEqualTo("retried-access-token");
        assertThat(result.refreshToken()).isNotEqualTo(refreshToken);
    }

    private AppUser createUser() {
        return transactionTemplate.execute(status -> appUserRepository.saveAndFlush(new AppUser(
            "refresh-" + System.nanoTime() + "@example.com",
            "hashed-password",
            "갱신 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        )));
    }

    private void awaitRotationMarker(RefreshToken refreshToken, Future<?> firstRequest) throws Exception {
        String rotationKey = "auth:refresh:token:" + refreshToken.tokenId() + ":rotation";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(rotationKey))) {
                return;
            }
            if (firstRequest.isDone()) {
                firstRequest.get();
            }
            sleep();
        }
        throw new IllegalStateException("refresh token rotation marker was not created");
    }

    private void awaitRotationMarkerExpiry(RefreshToken refreshToken) {
        String rotationKey = "auth:refresh:token:" + refreshToken.tokenId() + ":rotation";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        while (System.nanoTime() < deadline) {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(rotationKey))) {
                return;
            }
            sleep();
        }
        throw new IllegalStateException("refresh token rotation marker did not expire");
    }

    private String activeFamilyKey(RefreshToken refreshToken) {
        return "auth:refresh:family:" + refreshToken.familyId() + ":active";
    }

    private String consumedTokenKey(RefreshToken refreshToken) {
        return "auth:refresh:token:" + refreshToken.tokenId() + ":consumed";
    }

    private String rotationKey(RefreshToken refreshToken) {
        return "auth:refresh:token:" + refreshToken.tokenId() + ":rotation";
    }

    private void await(CountDownLatch latch) {
        await(latch, 5L);
    }

    private void await(CountDownLatch latch, long timeoutSeconds) {
        try {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(20L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }
}
