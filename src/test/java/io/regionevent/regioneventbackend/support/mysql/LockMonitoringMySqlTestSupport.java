package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class LockMonitoringMySqlTestSupport extends NonTransactionalMySqlProfileTestSupport {

    @DynamicPropertySource
    static void configureLockMonitoringMySqlDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.grantLockMonitoringPrivileges();
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }
}
