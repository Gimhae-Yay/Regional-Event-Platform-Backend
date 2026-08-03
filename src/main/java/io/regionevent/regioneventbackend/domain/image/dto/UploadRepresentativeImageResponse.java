package io.regionevent.regioneventbackend.domain.image.dto;

import java.time.Instant;
import java.util.Map;

public record UploadRepresentativeImageResponse(
    String imageObjectId,
    String uploadUrl,
    Instant expiresAt,
    Map<String, String> uploadHeaders
) {
}
