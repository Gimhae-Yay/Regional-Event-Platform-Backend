package io.regionevent.regioneventbackend.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.global.security.refresh.RefreshToken;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;
import io.regionevent.regioneventbackend.support.redis.RedisTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisRefreshTokenStoreIntegrationTest extends RedisTestSupport {

    private static final Duration TTL_TEST_DURATION = Duration.ofSeconds(2);
    private static final long EXPIRY_WAIT_BUFFER_MILLIS = 100L;

    @Autowired
    private RedisRefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearRedis() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void authRedis_usesNoevictionPolicy() {
        Properties configuration = stringRedisTemplate.getConnectionFactory().getConnection()
            .serverCommands()
            .getConfig("maxmemory-policy");

        assertThat(configuration.getProperty("maxmemory-policy")).isEqualTo("noeviction");
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
        assertThat(refreshTokenStore.completeRotation(current, nextTokenId, firstAttemptId))
            .isEqualTo(RefreshTokenStore.RotationCompletionResult.COMPLETED);
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
    void startRotation_whenUserFamilyIndexIsMissing_returnsInvalid() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        stringRedisTemplate.delete("auth:refresh:user:1:families");

        assertThat(refreshTokenStore.startRotation(current, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
    }

    @Test
    void startRotation_whenConsumedTokenMarkerIsMissing_revokesFamily() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        UUID attemptId = UUID.randomUUID();
        UUID nextTokenId = UUID.randomUUID();
        assertThat(refreshTokenStore.startRotation(current, attemptId))
            .isEqualTo(RefreshTokenStore.RotationStartResult.STARTED);
        assertThat(refreshTokenStore.completeRotation(current, nextTokenId, attemptId))
            .isEqualTo(RefreshTokenStore.RotationCompletionResult.COMPLETED);
        stringRedisTemplate.delete("auth:refresh:token:" + current.tokenId() + ":consumed");

        assertThat(refreshTokenStore.startRotation(current, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
        assertThat(refreshTokenStore.startRotation(nextToken(current, nextTokenId), UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.INVALID);
    }

    @Test
    void revokeFamily_removesCurrentRotationMarkerAndUserIndex() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        UUID attemptId = UUID.randomUUID();
        assertThat(refreshTokenStore.startRotation(current, attemptId))
            .isEqualTo(RefreshTokenStore.RotationStartResult.STARTED);

        refreshTokenStore.revokeFamily(current);

        assertThat(stringRedisTemplate.hasKey("auth:refresh:family:" + current.familyId() + ":active")).isFalse();
        assertThat(stringRedisTemplate.hasKey("auth:refresh:token:" + current.tokenId() + ":rotation")).isFalse();
        assertThat(stringRedisTemplate.hasKey("auth:refresh:user:1:families")).isFalse();
        assertThat(refreshTokenStore.completeRotation(current, UUID.randomUUID(), attemptId))
            .isEqualTo(RefreshTokenStore.RotationCompletionResult.CONFLICT);
    }

    @Test
    void revokeFamily_whenPresentedTokenIsConsumed_keepsNewActiveToken() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        UUID attemptId = UUID.randomUUID();
        UUID nextTokenId = UUID.randomUUID();
        assertThat(refreshTokenStore.startRotation(current, attemptId))
            .isEqualTo(RefreshTokenStore.RotationStartResult.STARTED);
        assertThat(refreshTokenStore.completeRotation(current, nextTokenId, attemptId))
            .isEqualTo(RefreshTokenStore.RotationCompletionResult.COMPLETED);

        RefreshToken next = nextToken(current, nextTokenId);
        refreshTokenStore.revokeFamily(current);

        assertThat(stringRedisTemplate.opsForValue().get("auth:refresh:family:" + current.familyId() + ":active"))
            .isEqualTo(nextTokenId.toString());
        assertThat(stringRedisTemplate.hasKey("auth:refresh:family:" + current.familyId() + ":revoked")).isFalse();
        assertThat(refreshTokenStore.startRotation(next, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.STARTED);
    }

    @Test
    void revokeFamily_whenActiveStateIsMissing_doesNotChangeUserIndexOrSetRevocationMarker() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        stringRedisTemplate.delete("auth:refresh:family:" + current.familyId() + ":active");

        refreshTokenStore.revokeFamily(current);

        assertThat(stringRedisTemplate.hasKey("auth:refresh:user:1:families")).isTrue();
        assertThat(stringRedisTemplate.hasKey("auth:refresh:family:" + current.familyId() + ":revoked")).isFalse();
    }

    @Test
    void revokeFamily_whenUserIndexIsMissing_doesNotChangeActiveStateOrSetRevocationMarker() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        stringRedisTemplate.delete("auth:refresh:user:1:families");

        refreshTokenStore.revokeFamily(current);

        assertThat(stringRedisTemplate.opsForValue().get("auth:refresh:family:" + current.familyId() + ":active"))
            .isEqualTo(current.tokenId().toString());
        assertThat(stringRedisTemplate.hasKey("auth:refresh:family:" + current.familyId() + ":revoked")).isFalse();
    }

    @Test
    void revokeFamily_whenRevocationMarkerExists_doesNotChangeFamilyState() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        stringRedisTemplate.opsForValue().set("auth:refresh:family:" + current.familyId() + ":revoked", "1");

        refreshTokenStore.revokeFamily(current);

        assertThat(stringRedisTemplate.opsForValue().get("auth:refresh:family:" + current.familyId() + ":active"))
            .isEqualTo(current.tokenId().toString());
        assertThat(stringRedisTemplate.hasKey("auth:refresh:user:1:families")).isTrue();
        assertThat(stringRedisTemplate.hasKey("auth:refresh:family:" + current.familyId() + ":revoked")).isTrue();
    }

    @Test
    void completeRotation_whenRotationMarkerExpires_returnsConflict() {
        RefreshToken current = refreshToken(1L);
        refreshTokenStore.createFamily(current);
        UUID attemptId = UUID.randomUUID();
        refreshTokenStore.startRotation(current, attemptId);
        stringRedisTemplate.delete("auth:refresh:token:" + current.tokenId() + ":rotation");

        assertThat(refreshTokenStore.completeRotation(current, UUID.randomUUID(), attemptId))
            .isEqualTo(RefreshTokenStore.RotationCompletionResult.CONFLICT);
        assertThat(refreshTokenStore.startRotation(current, UUID.randomUUID()))
            .isEqualTo(RefreshTokenStore.RotationStartResult.STARTED);
    }

    @Test
    void expiredFamilyState_isRemovedByRedisTtl() throws InterruptedException {
        RefreshToken current = refreshToken(1L, TTL_TEST_DURATION);
        refreshTokenStore.createFamily(current);

        Long remainingTtl = stringRedisTemplate.getExpire(
            "auth:refresh:family:" + current.familyId() + ":active",
            TimeUnit.MILLISECONDS
        );
        assertThat(remainingTtl).isPositive().isLessThanOrEqualTo(TTL_TEST_DURATION.toMillis());

        Thread.sleep(remainingTtl + EXPIRY_WAIT_BUFFER_MILLIS);

        assertThat(stringRedisTemplate.hasKey("auth:refresh:family:" + current.familyId() + ":active")).isFalse();
        assertThat(stringRedisTemplate.hasKey("auth:refresh:user:1:families")).isFalse();
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
        return refreshToken(userId, Duration.ofHours(1));
    }

    private RefreshToken refreshToken(Long userId, Duration expiresIn) {
        Instant issuedAt = Instant.now();
        return new RefreshToken(
            userId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            issuedAt,
            issuedAt.plus(expiresIn)
        );
    }

    private RefreshToken nextToken(RefreshToken current, UUID nextTokenId) {
        return new RefreshToken(
            current.userId(),
            nextTokenId,
            current.familyId(),
            Instant.now(),
            current.expiresAt()
        );
    }
}
