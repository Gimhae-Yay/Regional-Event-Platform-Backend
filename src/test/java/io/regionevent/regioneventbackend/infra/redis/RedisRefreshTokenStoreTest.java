package io.regionevent.regioneventbackend.infra.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import io.regionevent.regioneventbackend.global.security.RefreshToken;
import io.regionevent.regioneventbackend.global.security.RefreshTokenStoreUnavailableException;

class RedisRefreshTokenStoreTest {

    @Test
    void createFamily_whenRedisCommandFails_throwsStoreUnavailableException() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        doThrow(new DataAccessResourceFailureException("Redis OOM command failure"))
            .when(stringRedisTemplate)
            .execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                ArgumentMatchers.<Object>any(),
                ArgumentMatchers.<Object>any(),
                ArgumentMatchers.<Object>any(),
                ArgumentMatchers.<Object>any()
            );
        RedisRefreshTokenStore refreshTokenStore = new RedisRefreshTokenStore(
            stringRedisTemplate,
            Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC)
        );
        RefreshToken refreshToken = new RefreshToken(
            1L,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-07-31T00:00:00Z"),
            Instant.parse("2026-08-14T00:00:00Z")
        );

        assertThatThrownBy(() -> refreshTokenStore.createFamily(refreshToken))
            .isInstanceOf(RefreshTokenStoreUnavailableException.class)
            .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }
}
