package io.regionevent.regioneventbackend.support.redis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlProfileTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

public abstract class MySqlRedisTestSupport extends NonTransactionalMySqlProfileTestSupport {

    @DynamicPropertySource
    static void configureMySqlAndRedis(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
        SharedRedisTestContainer.registerRedisProperties(registry);
    }
}
