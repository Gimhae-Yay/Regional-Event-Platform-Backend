package io.regionevent.regioneventbackend.global.security.refresh;

import java.util.UUID;

public interface RefreshTokenStore {

    void createFamily(RefreshToken refreshToken);

    RotationStartResult startRotation(RefreshToken refreshToken, UUID attemptId);

    RotationCompletionResult completeRotation(RefreshToken refreshToken, UUID nextTokenId, UUID attemptId);

    void cancelRotation(RefreshToken refreshToken, UUID attemptId);

    void revokeFamily(RefreshToken refreshToken);

    void revokeAllFamilies(Long userId);

    enum RotationStartResult {
        STARTED,
        CONFLICT,
        INVALID
    }

    enum RotationCompletionResult {
        COMPLETED,
        CONFLICT,
        INVALID
    }
}
