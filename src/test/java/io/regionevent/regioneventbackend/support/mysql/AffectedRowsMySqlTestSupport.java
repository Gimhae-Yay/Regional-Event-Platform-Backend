package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AffectedRowsMySqlTestSupport extends NonTransactionalMySqlProfileTestSupport {

    @DynamicPropertySource
    static void configureAffectedRowsMySqlDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerAffectedRowsDataSourceProperties(registry);
    }
}
