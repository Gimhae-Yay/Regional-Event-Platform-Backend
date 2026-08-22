package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CorsPropertiesConfiguration.class);

    @Test
    void allowedOrigins_구성이없으면_빈목록으로시작한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CorsProperties.class).getAllowedOrigins()).isEmpty();
        });
    }

    @Test
    void allowedOrigins_HTTPS_Origin목록을바인딩한다() {
        contextRunner
            .withPropertyValues(
                "security.cors.allowed-origins=https://local-stamp.org,https://admin.local-stamp.org:8443"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CorsProperties.class).getAllowedOrigins()).containsExactly(
                    "https://local-stamp.org",
                    "https://admin.local-stamp.org:8443"
                );
            });
    }

    @Test
    void allowedOrigins_경로가포함되면_시작하지않는다() {
        contextRunner
            .withPropertyValues("security.cors.allowed-origins=https://local-stamp.org/app")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void allowedOrigins_쿼리가포함되면_시작하지않는다() {
        contextRunner
            .withPropertyValues("security.cors.allowed-origins=https://local-stamp.org?redirect=/app")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void allowedOrigins_fragment가포함되면_시작하지않는다() {
        contextRunner
            .withPropertyValues("security.cors.allowed-origins=https://local-stamp.org#login")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void allowedOrigins_와일드카드가포함되면_시작하지않는다() {
        contextRunner
            .withPropertyValues("security.cors.allowed-origins=https://*.local-stamp.org")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void allowedOrigins_HTTPS가아니면_시작하지않는다() {
        contextRunner
            .withPropertyValues("security.cors.allowed-origins=http://local-stamp.org")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void allowedOrigins_빈문자열은_빈목록으로처리한다() {
        CorsProperties corsProperties = new CorsProperties();

        corsProperties.setAllowedOrigins(List.of(""));

        assertThat(corsProperties.getAllowedOrigins()).isEmpty();
    }

    @Configuration
    @EnableConfigurationProperties(CorsProperties.class)
    static class CorsPropertiesConfiguration {
    }
}
