package io.regionevent.regioneventbackend.domain.image.service;

public record PresignedImageUploadCommand(
    String mediaType,
    long byteSize,
    String checksum,
    String usage
) {
}
