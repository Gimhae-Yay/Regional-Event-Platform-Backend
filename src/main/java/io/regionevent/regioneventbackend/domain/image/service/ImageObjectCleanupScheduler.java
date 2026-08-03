package io.regionevent.regioneventbackend.domain.image.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ImageObjectCleanupScheduler {

    private final ImageObjectCleanupService imageObjectCleanupService;

    public ImageObjectCleanupScheduler(ImageObjectCleanupService imageObjectCleanupService) {
        this.imageObjectCleanupService = imageObjectCleanupService;
    }

    @Scheduled(
        initialDelayString = "${image.cleanup-initial-delay:PT1H}",
        fixedDelayString = "${image.cleanup-fixed-delay:PT1H}"
    )
    public void cleanupExpiredUnlinkedUploadCandidates() {
        imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();
    }
}
