package io.regionevent.regioneventbackend.support.mysql;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SharedMySqlContainerUsage {

    private static final Map<String, String> CONTAINER_IDS_BY_CONFIGURATION = new ConcurrentHashMap<>();

    private SharedMySqlContainerUsage() {
    }

    static void record(String configuration, String containerId) {
        CONTAINER_IDS_BY_CONFIGURATION.put(configuration, containerId);
        boolean usesOneContainer = CONTAINER_IDS_BY_CONFIGURATION.values().stream()
            .distinct()
            .count() == 1;
        if (!usesOneContainer) {
            throw new AssertionError("Spring test configurations used different MySQL containers");
        }
    }
}
