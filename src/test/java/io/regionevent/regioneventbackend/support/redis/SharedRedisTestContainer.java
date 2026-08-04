package io.regionevent.regioneventbackend.support.redis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

public final class SharedRedisTestContainer {

    private static final String IMAGE_NAME = "redis:7.4-alpine";

    private SharedRedisTestContainer() {
    }

    public static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> container().getHost());
        registry.add("spring.data.redis.port", () -> container().getFirstMappedPort());
    }

    private static GenericContainer<?> container() {
        return ContainerHolder.INSTANCE;
    }

    private static GenericContainer<?> startContainer() {
        GenericContainer<?> redis = new GenericContainer<>(IMAGE_NAME)
            .withCommand("redis-server", "--maxmemory", "64mb", "--maxmemory-policy", "noeviction")
            .withExposedPorts(6379);
        redis.start();
        return redis;
    }

    private static final class ContainerHolder {

        private static final GenericContainer<?> INSTANCE = startContainer();

        private ContainerHolder() {
        }
    }
}
