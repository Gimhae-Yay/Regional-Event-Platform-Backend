package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class MySqlTestSupport {

    @DynamicPropertySource
    static void configureMySqlDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }
}
