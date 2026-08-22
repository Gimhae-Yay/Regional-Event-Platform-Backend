package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CorsPropertiesConfiguration.class);

    @Test
    void allowedOrigins_설정이비어있으면교차출처허용목록도비어있다() {
        contextRunner.withPropertyValues("security.cors.allowed-origins=")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context.getBean(CorsProperties.class).getAllowedOrigins()).isEmpty();
            });
    }

    @Test
    void allowedOrigins_유효한HttpsOrigin은그대로허용한다() {
        contextRunner.withPropertyValues("security.cors.allowed-origins=https://frontend.local:8443")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context.getBean(CorsProperties.class).isAllowedOrigin("https://frontend.local:8443"))
                    .isTrue();
            });
    }

    @ParameterizedTest
    @MethodSource("invalidAllowedOrigins")
    void allowedOrigins_잘못된형식이면애플리케이션시작에실패한다(String origin) {
        contextRunner.withPropertyValues("security.cors.allowed-origins=" + origin)
            .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    private static Stream<String> invalidAllowedOrigins() {
        return Stream.of(
            "http://frontend.local",
            "https://frontend.local/",
            "https://frontend.local/path",
            "https://frontend.local?query=value",
            "https://frontend.local#fragment",
            "https://*.local"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class CorsPropertiesConfiguration {
    }
}
