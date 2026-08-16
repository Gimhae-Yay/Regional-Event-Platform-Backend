package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.regionevent.regioneventbackend.domain.region.service.PublicRegionCache;

@Component
public class PublicCatalogCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(PublicCatalogCacheInvalidator.class);

    private final PublicRegionCache publicRegionCache;
    private final PublicContentCache publicContentCache;

    public PublicCatalogCacheInvalidator(
        PublicRegionCache publicRegionCache,
        PublicContentCache publicContentCache
    ) {
        this.publicRegionCache = Objects.requireNonNull(publicRegionCache, "publicRegionCache must not be null");
        this.publicContentCache = Objects.requireNonNull(publicContentCache, "publicContentCache must not be null");
    }

    public void invalidateRegionAfterCommit(Long regionId) {
        runAfterCommit(() -> publicRegionCache.evictRegion(regionId));
    }

    public void invalidateContentAfterCommit(
        Long regionId,
        Long contentId,
        int versionNo
    ) {
        runAfterCommit(() -> publicContentCache.evictContent(regionId, contentId, versionNo));
    }

    private void runAfterCommit(Runnable operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            operation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    log.warn("Public catalog cache invalidation failed after commit", exception);
                }
            }
        });
    }
}
