package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class IdempotencyMySqlTestSupport extends NonTransactionalMySqlProfileTestSupport {

    @DynamicPropertySource
    static void configureIdempotencyMySqlDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerAffectedRowsDataSourceProperties(registry);
        registry.add("idempotency.retention", () -> "PT24H");
        registry.add("idempotency.cleanup-fixed-delay", () -> "PT1H");
        registry.add("idempotency.cleanup-initial-delay", () -> "PT1H");
        registry.add("idempotency.lock-wait-timeout-seconds", () -> "1");
    }
}
