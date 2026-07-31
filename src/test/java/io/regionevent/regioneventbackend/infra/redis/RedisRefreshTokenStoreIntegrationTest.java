package io.regionevent.regioneventbackend.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.global.security.RefreshToken;
import io.regionevent.regioneventbackend.global.security.RefreshTokenStore;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisRefreshTokenStoreIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
        .withExposedPorts(6379);

    @Autowired
    private RedisRefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void clearRedis() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void startRotation_whenSameTokenIsInProgress_returnsConflictThenInvalidatesFamilyAfterReuse() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);

        UUID firstAttemptId = UUID.randomUUID();
        assertThat(refreshTokenStore.startRotation(current, firstAttemptId))
            .isEqualTo(RefreshTokenStore.RotationStartResult.STARTED);
        assertThat(refreshTokenStore.startRotation(current, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.CONFLICT);

        UUID nextTokenId = UUID.randomUUID();
        assertThat(refreshTokenStore.completeRotation(current, nextTokenId, firstAttemptId)).isTrue();
        assertThat(refreshTokenStore.startRotation(current, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);

        RefreshToken next = new RefreshToken(
            current.userId(),
            nextTokenId,
            current.familyId(),
            Instant.now(),
            current.expiresAt()
        );
        assertThat(refreshTokenStore.startRotation(next, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
    }

    @Test
    void startRotation_whenActiveFamilyStateIsMissing_returnsInvalid() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        stringRedisTemplate.delete("auth:refresh:family:" + current.familyId() + ":active");

        assertThat(refreshTokenStore.startRotation(current, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
    }

    @Test
    void revokeAllFamilies_invalidatesEveryActiveFamilyAndRemovesUserIndex() {
        RefreshToken first = refreshToken(1L);
        RefreshToken second = refreshToken(1L);
        refreshTokenStore.createFamily(first);
        refreshTokenStore.createFamily(second);

        refreshTokenStore.revokeAllFamilies(1L);

        assertThat(refreshTokenStore.startRotation(first, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
        assertThat(refreshTokenStore.startRotation(second, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
        assertThat(stringRedisTemplate.hasKey("auth:refresh:user:1:families")).isFalse();
    }

    private RefreshToken refreshToken(Long userId) {
        Instant issuedAt = Instant.now();
        return new RefreshToken(
            userId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            issuedAt,
            issuedAt.plusSeconds(3_600)
        );
    }
}
