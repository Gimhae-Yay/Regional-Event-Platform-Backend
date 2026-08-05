package io.regionevent.regioneventbackend.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentStaticInfo;

class RedisPublicCatalogCacheTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mockValueOperations();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisPublicCatalogCache cache;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new RedisPublicCatalogCache(
            stringRedisTemplate,
            objectMapper,
            new PublicCatalogCacheProperties(TTL)
        );
    }

    @Test
    void saveContent_버전별_키와_기본_TTL로_정적_표시_정보만_직렬화한다() throws Exception {
        PublicContentStaticInfo content = content();

        cache.saveContent(content);

        org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
            eq("public-content:10:200:v3"),
            valueCaptor.capture(),
            eq(TTL)
        );
        JsonNode serialized = objectMapper.readTree(valueCaptor.getValue());
        assertThat(serialized.has("representativeImageObject")).isFalse();
        assertThat(serialized.has("representativeImageUrl")).isFalse();
        assertThat(serialized.has("reservationAvailable")).isFalse();
        assertThat(serialized.has("displaySession")).isFalse();
        assertThat(serialized.has("contactText")).isFalse();
    }

    @Test
    void findContent_직렬화된_현재_버전_정적_정보를_역직렬화한다() throws Exception {
        PublicContentStaticInfo content = content();
        when(valueOperations.get("public-content:10:200:v3"))
            .thenReturn(objectMapper.writeValueAsString(content));

        Optional<PublicContentStaticInfo> result = cache.findContent(10L, 200L, 3);

        assertThat(result).contains(content);
    }

    @Test
    void redis_읽기_쓰기_삭제_실패는_예외를_전파하지_않는다() {
        when(valueOperations.get("public-content:10:200:v3"))
            .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Redis unavailable"))
            .when(valueOperations)
            .set(
                eq("public-content:10:200:v3"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(TTL)
            );
        when(stringRedisTemplate.delete("public-content:10:200:v3"))
            .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThat(cache.findContent(10L, 200L, 3)).isEmpty();
        assertThatCode(() -> cache.saveContent(content())).doesNotThrowAnyException();
        assertThatCode(() -> cache.evictContent(10L, 200L, 3)).doesNotThrowAnyException();
    }

    private static PublicContentStaticInfo content() {
        return new PublicContentStaticInfo(
            10L,
            200L,
            3,
            ContentType.EVENT_EXPERIENCE,
            "지역 축제",
            "축제 설명",
            "김해시",
            "10:00~18:00",
            "우천 시 취소",
            "전 연령",
            "없음",
            "당일 취소 불가"
        );
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
