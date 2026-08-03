package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RepresentativeImageViewUrlServiceTest {

    private static final Instant LINKED_AT = Instant.parse("2026-07-31T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-31T00:05:00Z");

    private ImageStorageGateway imageStorageGateway;
    private RepresentativeImageViewUrlService service;

    @BeforeEach
    void setUp() {
        imageStorageGateway = mock(ImageStorageGateway.class);
        service = new RepresentativeImageViewUrlService(imageStorageGateway);
    }

    @Test
    void createViewUrl_whenImageObjectIsActiveAndLinked_returnsViewUrlAndExpiresAt() {
        ImageObject imageObject = mockLinkedImageObject(ImageLifecycleStatus.ACTIVE);
        when(imageStorageGateway.createPresignedGetUrl("contents/image.webp"))
            .thenReturn(new PresignedViewUrl("https://example.com/view", EXPIRES_AT));

        RepresentativeImageViewUrl viewUrl = service.createViewUrl(imageObject);

        assertThat(viewUrl.url()).isEqualTo("https://example.com/view");
        assertThat(viewUrl.expiresAt()).isEqualTo(EXPIRES_AT);
        verify(imageStorageGateway).createPresignedGetUrl("contents/image.webp");
    }

    @Test
    void createViewUrl_whenImageObjectIsDeletePending_rejectsWithoutCallingStorage() {
        ImageObject imageObject = mockLinkedImageObject(ImageLifecycleStatus.DELETE_PENDING);

        assertInternalServerErrorThrownBy(() -> service.createViewUrl(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void createViewUrl_whenImageObjectIsNotLinked_rejectsWithoutCallingStorage() {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.getLifecycleStatus()).thenReturn(ImageLifecycleStatus.ACTIVE);
        when(imageObject.getLinkedAt()).thenReturn(null);

        assertInternalServerErrorThrownBy(() -> service.createViewUrl(imageObject));
        verifyNoInteractions(imageStorageGateway);
    }

    @Test
    void createViewUrl_whenStorageFails_convertsToInternalServerError() {
        ImageObject imageObject = mockLinkedImageObject(ImageLifecycleStatus.ACTIVE);
        ImageStorageException storageException = new ImageStorageException("failed");
        when(imageStorageGateway.createPresignedGetUrl("contents/image.webp"))
            .thenThrow(storageException);

        assertThatThrownBy(() -> service.createViewUrl(imageObject))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                assertThat(exception.getCause()).isEqualTo(storageException);
            });
    }

    @Test
    void representativeImageViewUrl_exposesOnlyUrlAndExpiresAt() {
        String[] componentNames = Arrays.stream(RepresentativeImageViewUrl.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toArray(String[]::new);

        assertThat(componentNames).containsExactly("url", "expiresAt");
    }

    private ImageObject mockLinkedImageObject(ImageLifecycleStatus lifecycleStatus) {
        ImageObject imageObject = mock(ImageObject.class);
        when(imageObject.getLifecycleStatus()).thenReturn(lifecycleStatus);
        when(imageObject.getLinkedAt()).thenReturn(LINKED_AT);
        when(imageObject.getObjectKey()).thenReturn("contents/image.webp");
        return imageObject;
    }

    private void assertInternalServerErrorThrownBy(ThrowingRunnable throwingRunnable) {
        assertThatThrownBy(throwingRunnable::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run();
    }
}
