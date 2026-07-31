package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Duration;

public record PresignedImageUploadSettings(
    Duration uploadUrlTtl
) {
}
