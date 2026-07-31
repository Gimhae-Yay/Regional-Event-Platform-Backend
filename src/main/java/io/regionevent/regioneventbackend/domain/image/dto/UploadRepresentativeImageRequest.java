package io.regionevent.regioneventbackend.domain.image.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadRepresentativeImageRequest(
    @NotBlank
    String mediaType,

    @NotNull
    @Min(1)
    @Max(5_242_880)
    Long byteSize,

    @NotBlank
    String checksum,

    @NotBlank
    String usage
) {
}
