package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.util.Map;

public record PresignedImageUploadResult(
    String imageObjectId,
    String uploadUrl,
    Instant expiresAt,
    Map<String, String> uploadHeaders
) {
}
