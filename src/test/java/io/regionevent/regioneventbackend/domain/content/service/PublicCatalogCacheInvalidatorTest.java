package io.regionevent.regioneventbackend.domain.content.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PublicCatalogCacheInvalidatorTest {

    private final PublicCatalogCache publicCatalogCache = mock(PublicCatalogCache.class);
    private final PublicCatalogCacheInvalidator invalidator = new PublicCatalogCacheInvalidator(publicCatalogCache);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void invalidateContentAfterCommit_트랜잭션이_커밋된_뒤에만_이전_버전_키를_삭제한다() {
        TransactionSynchronizationManager.initSynchronization();

        invalidator.invalidateContentAfterCommit(10L, 200L, 3);

        verifyNoInteractions(publicCatalogCache);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit);
        verify(publicCatalogCache).evictContent(10L, 200L, 3);
    }

    @Test
    void invalidateRegionAfterCommit_트랜잭션_밖에서는_즉시_삭제한다() {
        invalidator.invalidateRegionAfterCommit(10L);

        verify(publicCatalogCache).evictRegion(10L);
    }
}
