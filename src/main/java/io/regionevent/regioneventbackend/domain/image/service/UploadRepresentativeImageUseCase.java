package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.image.dto.UploadRepresentativeImageRequest;
import io.regionevent.regioneventbackend.domain.image.dto.UploadRepresentativeImageResponse;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UploadRepresentativeImageUseCase {

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );
    private static final String REPRESENTATIVE_IMAGE_USAGE = "CONTENT_REPRESENTATIVE";
    private static final int SHA_256_BASE64_LENGTH = 44;
    private static final int SHA_256_BYTE_LENGTH = 32;
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy/MM/dd")
        .withZone(ZoneOffset.UTC);

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ImageObjectRepository imageObjectRepository;
    private final ImageStorageGateway imageStorageGateway;
    private final Clock clock;

    public UploadRepresentativeImageUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ImageObjectRepository imageObjectRepository,
        ImageStorageGateway imageStorageGateway,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.imageObjectRepository = imageObjectRepository;
        this.imageStorageGateway = imageStorageGateway;
        this.clock = clock;
    }

    @Transactional
    public UploadRepresentativeImageResponse createUpload(
        Long authenticatedUserId,
        UploadRepresentativeImageRequest request
    ) {
        validateRequest(request);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(authenticatedUserId);
        String objectKey = generateObjectKey(request.mediaType());
        PresignedUpload presignedUpload = createPresignedUpload(request, objectKey);
        ImageObject imageObject = ImageObject.createUploadCandidate(
            objectKey,
            operator.user(),
            operator.region(),
            request.mediaType(),
            request.byteSize(),
            request.checksum(),
            presignedUpload.expiresAt()
        );
        ImageObject savedImageObject = imageObjectRepository.saveAndFlush(imageObject);

        return new UploadRepresentativeImageResponse(
            savedImageObject.getImageObjectId().toString(),
            presignedUpload.uploadUrl(),
            presignedUpload.expiresAt(),
            presignedUpload.uploadHeaders()
        );
    }

    private PresignedUpload createPresignedUpload(
        UploadRepresentativeImageRequest request,
        String objectKey
    ) {
        try {
            return imageStorageGateway.createPresignedPutUpload(
                objectKey,
                request.mediaType(),
                request.byteSize(),
                request.checksum()
            );
        } catch (ImageStorageException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private void validateRequest(UploadRepresentativeImageRequest request) {
        if (request == null
            || !ALLOWED_MEDIA_TYPES.contains(request.mediaType())
            || !REPRESENTATIVE_IMAGE_USAGE.equals(request.usage())
            || !isSha256Base64(request.checksum())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String generateObjectKey(String mediaType) {
        return "contents/%s/%s.%s".formatted(
            DATE_PATH_FORMATTER.format(clock.instant()),
            UUID.randomUUID(),
            toExtension(mediaType)
        );
    }

    private String toExtension(String mediaType) {
        return switch (mediaType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private boolean isSha256Base64(String checksum) {
        if (checksum == null || checksum.length() != SHA_256_BASE64_LENGTH) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(checksum).length == SHA_256_BYTE_LENGTH;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
