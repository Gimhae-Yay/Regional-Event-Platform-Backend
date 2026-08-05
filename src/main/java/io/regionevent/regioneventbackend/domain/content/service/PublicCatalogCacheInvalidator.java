package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PublicCatalogCacheInvalidator {

    private final PublicCatalogCache publicCatalogCache;

    public PublicCatalogCacheInvalidator(PublicCatalogCache publicCatalogCache) {
        this.publicCatalogCache = Objects.requireNonNull(publicCatalogCache, "publicCatalogCache must not be null");
    }

    public void invalidateRegionAfterCommit(Long regionId) {
        runAfterCommit(() -> publicCatalogCache.evictRegion(regionId));
    }

    public void invalidateContentAfterCommit(
        Long regionId,
        Long contentId,
        int versionNo
    ) {
        runAfterCommit(() -> publicCatalogCache.evictContent(regionId, contentId, versionNo));
    }

    private void runAfterCommit(Runnable operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            operation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                operation.run();
            }
        });
    }
}
