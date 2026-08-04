package io.regionevent.regioneventbackend.support.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public interface NonTransactionalMySqlTestIsolation {

    MySqlDatabaseCleaner DATABASE_CLEANER = MySqlDatabaseCleaner.forSharedContainer();

    @BeforeEach
    default void cleanDatabaseBeforeTest() {
        validateNoActiveTransaction();
        DATABASE_CLEANER.clean();
    }

    @AfterEach
    default void cleanDatabaseAfterTest() {
        validateNoActiveTransaction();
        DATABASE_CLEANER.clean();
    }

    private void validateNoActiveTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Non-transactional MySQL test support requires no active transaction");
        }
    }
}
