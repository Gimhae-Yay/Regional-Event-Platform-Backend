package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class PresignedImageUploadService {

    private static final String CONTENT_REPRESENTATIVE_USAGE = "CONTENT_REPRESENTATIVE";
    private static final int SHA_256_BYTE_LENGTH = 32;
    private static final int SHA_256_BASE64_LENGTH = 44;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private final ImageObjectRepository imageObjectRepository;
    private final ImageObjectKeyGenerator imageObjectKeyGenerator;
    private final ImageStorageGateway imageStorageGateway;
    private final PresignedImageUploadSettings presignedImageUploadSettings;
    private final Clock clock;

    public PresignedImageUploadService(
        ImageObjectRepository imageObjectRepository,
        ImageObjectKeyGenerator imageObjectKeyGenerator,
        ImageStorageGateway imageStorageGateway,
        PresignedImageUploadSettings presignedImageUploadSettings,
        Clock clock
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.imageObjectKeyGenerator = imageObjectKeyGenerator;
        this.imageStorageGateway = imageStorageGateway;
        this.presignedImageUploadSettings = presignedImageUploadSettings;
        this.clock = clock;
    }

    public PresignedImageUploadResult createUpload(
        AppUser operator,
        Region region,
        PresignedImageUploadCommand command
    ) {
        validateRequest(command);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(presignedImageUploadSettings.uploadUrlTtl());
        String objectKey = imageObjectKeyGenerator.generate(command.mediaType(), now);

        ImageObject imageObject = ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            command.mediaType(),
            command.byteSize(),
            command.checksum(),
            expiresAt
        );
        imageObjectRepository.saveAndFlush(imageObject);

        PresignedUpload presignedUpload = imageStorageGateway.createPresignedPutUpload(
            objectKey,
            command.mediaType(),
            command.byteSize(),
            command.checksum(),
            expiresAt
        );

        return new PresignedImageUploadResult(
            imageObject.getImageObjectId().toString(),
            presignedUpload.uploadUrl(),
            presignedUpload.expiresAt(),
            presignedUpload.uploadHeaders()
        );
    }

    private static void validateRequest(PresignedImageUploadCommand command) {
        if (!ALLOWED_MEDIA_TYPES.contains(command.mediaType())
            || !CONTENT_REPRESENTATIVE_USAGE.equals(command.usage())
            || !isSha256Base64(command.checksum())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static boolean isSha256Base64(String checksum) {
        if (checksum.length() != SHA_256_BASE64_LENGTH) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(checksum).length == SHA_256_BYTE_LENGTH;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
