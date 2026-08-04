package io.regionevent.regioneventbackend.support.redis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class RedisTestSupport {

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        SharedRedisTestContainer.registerRedisProperties(registry);
    }
}
