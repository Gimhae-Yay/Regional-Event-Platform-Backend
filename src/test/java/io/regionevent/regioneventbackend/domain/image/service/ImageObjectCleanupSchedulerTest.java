package io.regionevent.regioneventbackend.domain.image.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ImageObjectCleanupSchedulerTest {

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenScheduled_delegatesToService() {
        ImageObjectCleanupService imageObjectCleanupService = mock(ImageObjectCleanupService.class);
        ImageObjectCleanupScheduler scheduler = new ImageObjectCleanupScheduler(imageObjectCleanupService);

        scheduler.cleanupExpiredUnlinkedUploadCandidates();

        verify(imageObjectCleanupService).cleanupExpiredUnlinkedUploadCandidates();
    }
}
