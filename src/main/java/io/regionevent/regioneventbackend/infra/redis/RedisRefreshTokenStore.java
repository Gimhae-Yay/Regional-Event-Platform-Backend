package io.regionevent.regioneventbackend.infra.redis;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.global.security.RefreshToken;
import io.regionevent.regioneventbackend.global.security.RefreshTokenStore;
import io.regionevent.regioneventbackend.global.security.RefreshTokenStoreUnavailableException;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String ACTIVE_FAMILY_SUFFIX = ":active";
    private static final String REVOKED_FAMILY_SUFFIX = ":revoked";
    private static final String CONSUMED_TOKEN_SUFFIX = ":consumed";
    private static final String ROTATION_SUFFIX = ":rotation";
    private static final long ROTATION_TTL_MILLIS = 5_000L;

    private static final long CREATED = 1L;
    private static final long STARTED = 1L;
    private static final long CONFLICT = 2L;

    private static final DefaultRedisScript<Long> CREATE_FAMILY_SCRIPT = script("""
        local expiry = tonumber(ARGV[1])
        local now = tonumber(ARGV[2])
        if expiry <= now then
            return 0
        end
        if redis.call('EXISTS', KEYS[1]) == 1 then
            return 0
        end
        redis.call('SET', KEYS[1], ARGV[3])
        redis.call('PEXPIREAT', KEYS[1], expiry)
        redis.call('ZADD', KEYS[2], expiry, ARGV[4])
        local latest = redis.call('ZRANGE', KEYS[2], -1, -1, 'WITHSCORES')
        redis.call('PEXPIREAT', KEYS[2], tonumber(latest[2]))
        return 1
        """);

    private static final DefaultRedisScript<Long> START_ROTATION_SCRIPT = script("""
        local expiry = tonumber(ARGV[1])
        local now = tonumber(ARGV[2])
        if expiry <= now then
            return 0
        end
        if redis.call('EXISTS', KEYS[2]) == 1 then
            return 0
        end
        local activeTokenId = redis.call('GET', KEYS[1])
        local indexedExpiry = redis.call('ZSCORE', KEYS[4], ARGV[4])
        if not activeTokenId or not indexedExpiry or tonumber(indexedExpiry) ~= expiry then
            return 0
        end
        if activeTokenId ~= ARGV[3] or redis.call('EXISTS', KEYS[3]) == 1 then
            redis.call('SET', KEYS[2], '1')
            redis.call('PEXPIREAT', KEYS[2], expiry)
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[4], ARGV[4])
            local latest = redis.call('ZRANGE', KEYS[4], -1, -1, 'WITHSCORES')
            if #latest == 0 then
                redis.call('DEL', KEYS[4])
            else
                redis.call('PEXPIREAT', KEYS[4], tonumber(latest[2]))
            end
            return 0
        end
        if redis.call('SET', KEYS[5], ARGV[5], 'NX', 'PX', ARGV[6]) then
            return 1
        end
        return 2
        """);

    private static final DefaultRedisScript<Long> COMPLETE_ROTATION_SCRIPT = script("""
        local expiry = tonumber(ARGV[1])
        local now = tonumber(ARGV[2])
        if expiry <= now then
            return 0
        end
        if redis.call('GET', KEYS[4]) ~= ARGV[5] then
            return 0
        end
        local indexedExpiry = redis.call('ZSCORE', KEYS[5], ARGV[4])
        if not indexedExpiry or tonumber(indexedExpiry) ~= expiry then
            redis.call('DEL', KEYS[4])
            return 0
        end
        if redis.call('EXISTS', KEYS[2]) == 1 or redis.call('GET', KEYS[1]) ~= ARGV[3] then
            redis.call('DEL', KEYS[4])
            return 0
        end
        redis.call('SET', KEYS[3], ARGV[4])
        redis.call('PEXPIREAT', KEYS[3], expiry)
        redis.call('SET', KEYS[1], ARGV[6])
        redis.call('PEXPIREAT', KEYS[1], expiry)
        redis.call('DEL', KEYS[4])
        return 1
        """);

    private static final DefaultRedisScript<Long> CANCEL_ROTATION_SCRIPT = script("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        end
        return 0
        """);

    private static final DefaultRedisScript<Long> REVOKE_FAMILY_SCRIPT = script("""
        local expiry = tonumber(ARGV[1])
        local now = tonumber(ARGV[2])
        redis.call('DEL', KEYS[1])
        redis.call('ZREM', KEYS[3], ARGV[3])
        if expiry > now then
            redis.call('SET', KEYS[2], '1')
            redis.call('PEXPIREAT', KEYS[2], expiry)
        end
        local latest = redis.call('ZRANGE', KEYS[3], -1, -1, 'WITHSCORES')
        if #latest == 0 then
            redis.call('DEL', KEYS[3])
        else
            redis.call('PEXPIREAT', KEYS[3], tonumber(latest[2]))
        end
        return 1
        """);

    private static final DefaultRedisScript<Long> REVOKE_ALL_FAMILIES_SCRIPT = script("""
        local now = tonumber(ARGV[1])
        local families = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
        for index = 1, #families, 2 do
            local familyId = families[index]
            local expiry = tonumber(families[index + 1])
            local activeKey = ARGV[2] .. familyId .. ARGV[4]
            local revokedKey = ARGV[2] .. familyId .. ARGV[3]
            local activeTokenId = redis.call('GET', activeKey)
            if expiry > now then
                redis.call('SET', revokedKey, '1')
                redis.call('PEXPIREAT', revokedKey, expiry)
            end
            if activeTokenId then
                redis.call('DEL', ARGV[5] .. activeTokenId .. ARGV[6])
            end
            redis.call('DEL', activeKey)
        end
        redis.call('DEL', KEYS[1])
        return 1
        """);

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    public RedisRefreshTokenStore(StringRedisTemplate stringRedisTemplate, Clock clock) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void createFamily(RefreshToken refreshToken) {
        long result = execute(
            CREATE_FAMILY_SCRIPT,
            List.of(activeFamilyKey(refreshToken.familyId()), userFamiliesKey(refreshToken.userId())),
            Long.toString(expiresAtMillis(refreshToken)),
            Long.toString(currentMillis()),
            refreshToken.tokenId().toString(),
            refreshToken.familyId().toString()
        );
        if (result != CREATED) {
            throw new RefreshTokenStoreUnavailableException(new IllegalStateException("Unable to create refresh token family"));
        }
    }

    @Override
    public RotationStartResult startRotation(RefreshToken refreshToken, UUID attemptId) {
        long result = execute(
            START_ROTATION_SCRIPT,
            List.of(
                activeFamilyKey(refreshToken.familyId()),
                revokedFamilyKey(refreshToken.familyId()),
                consumedTokenKey(refreshToken.tokenId()),
                userFamiliesKey(refreshToken.userId()),
                rotationKey(refreshToken.tokenId())
            ),
            Long.toString(expiresAtMillis(refreshToken)),
            Long.toString(currentMillis()),
            refreshToken.tokenId().toString(),
            refreshToken.familyId().toString(),
            attemptId.toString(),
            Long.toString(ROTATION_TTL_MILLIS)
        );
        if (result == STARTED) {
            return RotationStartResult.STARTED;
        }
        if (result == CONFLICT) {
            return RotationStartResult.CONFLICT;
        }
        return RotationStartResult.INVALID;
    }

    @Override
    public boolean completeRotation(RefreshToken refreshToken, UUID nextTokenId, UUID attemptId) {
        long result = execute(
            COMPLETE_ROTATION_SCRIPT,
            List.of(
                activeFamilyKey(refreshToken.familyId()),
                revokedFamilyKey(refreshToken.familyId()),
                consumedTokenKey(refreshToken.tokenId()),
                rotationKey(refreshToken.tokenId()),
                userFamiliesKey(refreshToken.userId())
            ),
            Long.toString(expiresAtMillis(refreshToken)),
            Long.toString(currentMillis()),
            refreshToken.tokenId().toString(),
            refreshToken.familyId().toString(),
            attemptId.toString(),
            nextTokenId.toString()
        );
        return result == CREATED;
    }

    @Override
    public void cancelRotation(RefreshToken refreshToken, UUID attemptId) {
        execute(CANCEL_ROTATION_SCRIPT, List.of(rotationKey(refreshToken.tokenId())), attemptId.toString());
    }

    @Override
    public void revokeFamily(RefreshToken refreshToken) {
        execute(
            REVOKE_FAMILY_SCRIPT,
            List.of(
                activeFamilyKey(refreshToken.familyId()),
                revokedFamilyKey(refreshToken.familyId()),
                userFamiliesKey(refreshToken.userId())
            ),
            Long.toString(expiresAtMillis(refreshToken)),
            Long.toString(currentMillis()),
            refreshToken.familyId().toString()
        );
    }

    @Override
    public void revokeAllFamilies(Long userId) {
        execute(
            REVOKE_ALL_FAMILIES_SCRIPT,
            List.of(userFamiliesKey(userId)),
            Long.toString(currentMillis()),
            KEY_PREFIX + "family:",
            REVOKED_FAMILY_SUFFIX,
            ACTIVE_FAMILY_SUFFIX,
            KEY_PREFIX + "token:",
            ROTATION_SUFFIX
        );
    }

    private long execute(DefaultRedisScript<Long> script, List<String> keys, String... arguments) {
        try {
            Long result = stringRedisTemplate.execute(script, keys, (Object[]) arguments);
            if (result == null) {
                throw new RefreshTokenStoreUnavailableException(new IllegalStateException("Redis script returned no result"));
            }
            return result;
        } catch (DataAccessException exception) {
            throw new RefreshTokenStoreUnavailableException(exception);
        }
    }

    private long expiresAtMillis(RefreshToken refreshToken) {
        return refreshToken.expiresAt().toEpochMilli();
    }

    private long currentMillis() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli()).toEpochMilli();
    }

    private String activeFamilyKey(UUID familyId) {
        return KEY_PREFIX + "family:" + familyId + ACTIVE_FAMILY_SUFFIX;
    }

    private String revokedFamilyKey(UUID familyId) {
        return KEY_PREFIX + "family:" + familyId + REVOKED_FAMILY_SUFFIX;
    }

    private String consumedTokenKey(UUID tokenId) {
        return KEY_PREFIX + "token:" + tokenId + CONSUMED_TOKEN_SUFFIX;
    }

    private String rotationKey(UUID tokenId) {
        return KEY_PREFIX + "token:" + tokenId + ROTATION_SUFFIX;
    }

    private String userFamiliesKey(Long userId) {
        return KEY_PREFIX + "user:" + userId + ":families";
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }
}
