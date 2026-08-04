package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class LockMonitoringLockTimeoutThreeMySqlTestSupport
    extends NonTransactionalMySqlProfileTestSupport {

    @DynamicPropertySource
    static void configureLockMonitoringMySqlDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.grantLockMonitoringPrivileges();
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
        registry.add("idempotency.lock-wait-timeout-seconds", () -> "3");
    }
}
