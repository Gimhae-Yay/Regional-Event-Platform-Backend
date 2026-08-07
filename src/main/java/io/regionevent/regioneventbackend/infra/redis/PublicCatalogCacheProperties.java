package io.regionevent.regioneventbackend.infra.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "public-catalog-cache")
public record PublicCatalogCacheProperties(
    @DefaultValue("PT10M") Duration ttl
) {

    public PublicCatalogCacheProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }
}
