package io.regionevent.regioneventbackend.domain.image.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ExpiredImageUploadCleanupScheduler {

    private final CleanupExpiredImageUploadUseCase cleanupExpiredImageUploadUseCase;

    public ExpiredImageUploadCleanupScheduler(CleanupExpiredImageUploadUseCase cleanupExpiredImageUploadUseCase) {
        this.cleanupExpiredImageUploadUseCase = cleanupExpiredImageUploadUseCase;
    }

    @Scheduled(fixedDelayString = "${storage.image.cleanup.fixed-delay-ms:600000}")
    public void cleanupExpiredUploadCandidates() {
        cleanupExpiredImageUploadUseCase.cleanupExpiredUploadCandidates();
    }
}
