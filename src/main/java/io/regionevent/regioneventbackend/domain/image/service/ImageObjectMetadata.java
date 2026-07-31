package io.regionevent.regioneventbackend.domain.image.service;

public record ImageObjectMetadata(
    String checksumSha256,
    long contentLength
) {
}
