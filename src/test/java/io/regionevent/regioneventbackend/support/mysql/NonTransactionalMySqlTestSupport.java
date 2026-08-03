package io.regionevent.regioneventbackend.support.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

@Execution(ExecutionMode.SAME_THREAD)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class NonTransactionalMySqlTestSupport {

    private static final MySqlDatabaseCleaner DATABASE_CLEANER = MySqlDatabaseCleaner.forSharedContainer();

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        DATABASE_CLEANER.clean();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        DATABASE_CLEANER.clean();
    }
}
