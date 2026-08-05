package io.regionevent.regioneventbackend.infra.redis;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import io.regionevent.regioneventbackend.domain.content.service.PublicContentCache;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentStaticInfo;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionCache;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionStaticInfo;

@Component
public class RedisPublicCatalogCache implements PublicContentCache, PublicRegionCache {

    private static final Logger log = LoggerFactory.getLogger(RedisPublicCatalogCache.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PublicCatalogCacheProperties properties;

    public RedisPublicCatalogCache(
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        PublicCatalogCacheProperties properties
    ) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Optional<PublicRegionStaticInfo> findRegion(Long regionId) {
        return find(regionKey(regionId), PublicRegionStaticInfo.class);
    }

    @Override
    public void saveRegion(PublicRegionStaticInfo region) {
        save(regionKey(region.regionId()), region);
    }

    @Override
    public void evictRegion(Long regionId) {
        evict(regionKey(regionId));
    }

    @Override
    public Optional<PublicContentStaticInfo> findContent(
        Long regionId,
        Long contentId,
        int versionNo
    ) {
        return find(contentKey(regionId, contentId, versionNo), PublicContentStaticInfo.class);
    }

    @Override
    public void saveContent(PublicContentStaticInfo content) {
        save(contentKey(content.regionId(), content.contentId(), content.versionNo()), content);
    }

    @Override
    public void evictContent(Long regionId, Long contentId, int versionNo) {
        evict(contentKey(regionId, contentId, versionNo));
    }

    private <T> Optional<T> find(String key, Class<T> valueType) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, valueType));
        } catch (RuntimeException exception) {
            log.warn("공개 카탈로그 캐시 읽기에 실패해 MySQL 원본으로 우회합니다. key={}", key, exception);
            return Optional.empty();
        }
    }

    private void save(String key, Object value) {
        try {
            stringRedisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(value),
                properties.ttl()
            );
        } catch (RuntimeException exception) {
            log.warn("공개 카탈로그 캐시 쓰기에 실패해 MySQL 원본 응답을 유지합니다. key={}", key, exception);
        }
    }

    private void evict(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("공개 카탈로그 캐시 무효화에 실패했습니다. key={}", key, exception);
        }
    }

    private String regionKey(Long regionId) {
        return "public-region:" + regionId;
    }

    private String contentKey(Long regionId, Long contentId, int versionNo) {
        return "public-content:" + regionId + ":" + contentId + ":v" + versionNo;
    }
}
