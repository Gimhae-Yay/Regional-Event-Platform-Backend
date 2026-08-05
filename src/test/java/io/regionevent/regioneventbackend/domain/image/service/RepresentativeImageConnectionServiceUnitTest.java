package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RepresentativeImageConnectionServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Long IMAGE_OBJECT_ID = 1L;
    private static final Long OPERATOR_USER_ID = 2L;
    private static final Long REGION_ID = 3L;
    private static final long BYTE_SIZE = 1024L;
    private static final String CHECKSUM = "checksum";

    private ImageObjectRepository imageObjectRepository;
    private ImageStorageGateway imageStorageGateway;
    private RepresentativeImageConnectionService service;

    @BeforeEach
    void setUp() {
        imageObjectRepository = mock(ImageObjectRepository.class);
        imageStorageGateway = mock(ImageStorageGateway.class);
        service = new RepresentativeImageConnectionService(
            imageObjectRepository,
            imageStorageGateway,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void validateAndMarkConnected_whenOperatorIsDifferent_rejectsConnection() {
        ImageObject imageObject = mockImageObject(false, true, true);

        assertInvalidInputThrownBy(() -> validate(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void validateAndMarkConnected_whenRegionIsDifferent_rejectsConnection() {
        ImageObject imageObject = mockImageObject(true, false, true);

        assertInvalidInputThrownBy(() -> validate(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void validateAndMarkConnected_whenUploadCandidateIsExpired_rejectsConnection() {
        ImageObject imageObject = mockImageObject(true, true, false);

        assertInvalidInputThrownBy(() -> validate(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void validateAndMarkConnected_whenImageObjectIsAlreadyLinked_rejectsConnection() {
        ImageObject imageObject = mockImageObject(true, true, false);

        assertInvalidInputThrownBy(() -> validate(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void validateAndMarkConnected_whenImageObjectIsDeletePending_rejectsConnection() {
        ImageObject imageObject = mockImageObject(true, true, false);

        assertInvalidInputThrownBy(() -> validate(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void validateAndMarkConnected_whenStoredByteSizeIsDifferent_rejectsConnection() {
        ImageObject imageObject = mockImageObject(true, true, true);
        when(imageStorageGateway.findMetadata(imageObject.getObjectKey()))
            .thenReturn(new StoredObjectMetadata(BYTE_SIZE + 1, CHECKSUM));

        assertInvalidInputThrownBy(() -> validate(imageObject));
    }

    @Test
    void validateAndMarkConnected_whenStoredChecksumIsDifferent_rejectsConnection() {
        ImageObject imageObject = mockImageObject(true, true, true);
        when(imageStorageGateway.findMetadata(imageObject.getObjectKey()))
            .thenReturn(new StoredObjectMetadata(BYTE_SIZE, "other-checksum"));

        assertInvalidInputThrownBy(() -> validate(imageObject));
    }

    private ImageObject mockImageObject(
        boolean ownedByOperator,
        boolean scopedToRegion,
        boolean connectable
    ) {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.getObjectKey()).thenReturn("content/candidate.webp");
        when(imageObject.getByteSize()).thenReturn(BYTE_SIZE);
        when(imageObject.getChecksum()).thenReturn(CHECKSUM);
        when(imageObject.isOwnedBy(OPERATOR_USER_ID)).thenReturn(ownedByOperator);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(scopedToRegion);
        when(imageObject.isConnectableAt(NOW)).thenReturn(connectable);
        when(imageObjectRepository.findByImageObjectId(IMAGE_OBJECT_ID)).thenReturn(Optional.of(imageObject));
        return imageObject;
    }

    private void validate(ImageObject imageObject) {
        ImageObject validatedImageObject = service.validateAndMarkConnected(
            IMAGE_OBJECT_ID,
            OPERATOR_USER_ID,
            REGION_ID
        );
        assertThat(validatedImageObject).isSameAs(imageObject);
    }

    private void assertInvalidInputThrownBy(ThrowingCallable throwingCallable) {
        assertThatThrownBy(throwingCallable)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }
}
