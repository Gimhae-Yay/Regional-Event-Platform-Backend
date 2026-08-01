package io.regionevent.regioneventbackend.global.security.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void rotate_whenStoreStartsRotation_preservesFamilyAndExpiry() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingRefreshTokenStore refreshTokenStore = new RecordingRefreshTokenStore();
        JwtRefreshTokenService jwtRefreshTokenService = createJwtService(clock);
        RefreshTokenService refreshTokenService = new RefreshTokenService(jwtRefreshTokenService, refreshTokenStore, clock);

        String issuedToken = refreshTokenService.issue(1L);
        RefreshToken issued = jwtRefreshTokenService.authenticate(issuedToken);
        String rotatedToken = refreshTokenService.rotate(issuedToken);
        RefreshToken rotated = jwtRefreshTokenService.authenticate(rotatedToken);

        assertThat(rotated.familyId()).isEqualTo(issued.familyId());
        assertThat(rotated.expiresAt()).isEqualTo(issued.expiresAt());
        assertThat(rotated.tokenId()).isNotEqualTo(issued.tokenId());
        assertThat(refreshTokenStore.completedTokenId).isEqualTo(rotated.tokenId());
    }

    @Test
    void rotate_whenStoreReportsConflict_throwsRefreshTokenConflictException() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingRefreshTokenStore refreshTokenStore = new RecordingRefreshTokenStore();
        refreshTokenStore.rotationStartResult = RefreshTokenStore.RotationStartResult.CONFLICT;
        RefreshTokenService refreshTokenService = new RefreshTokenService(createJwtService(clock), refreshTokenStore, clock);

        String token = refreshTokenService.issue(1L);

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
            .isInstanceOf(RefreshTokenConflictException.class);
    }

    @Test
    void rotate_whenStoreReportsInvalid_throwsInvalidRefreshTokenException() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingRefreshTokenStore refreshTokenStore = new RecordingRefreshTokenStore();
        refreshTokenStore.rotationStartResult = RefreshTokenStore.RotationStartResult.INVALID;
        RefreshTokenService refreshTokenService = new RefreshTokenService(createJwtService(clock), refreshTokenStore, clock);

        String token = refreshTokenService.issue(1L);

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotate_whenVerificationFailsAfterStartingRotation_cancelsRotation() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingRefreshTokenStore refreshTokenStore = new RecordingRefreshTokenStore();
        RefreshTokenService refreshTokenService = new RefreshTokenService(createJwtService(clock), refreshTokenStore, clock);
        String token = refreshTokenService.issue(1L);

        assertThatThrownBy(() -> refreshTokenService.rotate(token, ignored -> {
            throw new InvalidRefreshTokenException();
        })).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokenStore.cancelledAttemptId).isNotNull();
    }

    @Test
    void issue_whenRedisStoreIsUnavailable_throwsRefreshTokenStoreUnavailableException() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingRefreshTokenStore refreshTokenStore = new RecordingRefreshTokenStore();
        refreshTokenStore.createFamilyException = new RefreshTokenStoreUnavailableException(new IllegalStateException());
        RefreshTokenService refreshTokenService = new RefreshTokenService(createJwtService(clock), refreshTokenStore, clock);

        assertThatThrownBy(() -> refreshTokenService.issue(1L))
            .isInstanceOf(RefreshTokenStoreUnavailableException.class);
    }

    private JwtRefreshTokenService createJwtService(Clock clock) {
        JwtRefreshTokenProperties properties = new JwtRefreshTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-refresh");
        properties.setActiveKeyId("refresh-test-key");
        properties.setActiveKey("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        return new JwtRefreshTokenService(properties, clock);
    }

    private static class RecordingRefreshTokenStore implements RefreshTokenStore {

        private RotationStartResult rotationStartResult = RotationStartResult.STARTED;
        private UUID completedTokenId;
        private UUID cancelledAttemptId;
        private RuntimeException createFamilyException;

        @Override
        public void createFamily(RefreshToken refreshToken) {
            if (createFamilyException != null) {
                throw createFamilyException;
            }
        }

        @Override
        public RotationStartResult startRotation(RefreshToken refreshToken, UUID attemptId) {
            return rotationStartResult;
        }

        @Override
        public boolean completeRotation(RefreshToken refreshToken, UUID nextTokenId, UUID attemptId) {
            completedTokenId = nextTokenId;
            return true;
        }

        @Override
        public void cancelRotation(RefreshToken refreshToken, UUID attemptId) {
            cancelledAttemptId = attemptId;
        }

        @Override
        public void revokeFamily(RefreshToken refreshToken) {
        }

        @Override
        public void revokeAllFamilies(Long userId) {
        }
    }
}
