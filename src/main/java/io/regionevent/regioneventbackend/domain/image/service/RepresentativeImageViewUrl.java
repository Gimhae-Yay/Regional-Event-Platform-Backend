package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;

public record RepresentativeImageViewUrl(
    String url,
    Instant expiresAt
) {
}
