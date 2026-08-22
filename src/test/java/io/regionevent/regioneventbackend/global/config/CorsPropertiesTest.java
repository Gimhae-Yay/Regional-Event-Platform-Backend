package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsPropertiesTest {

    private static final String API_PUBLIC_ORIGIN = "https://api.example.test";
    private static final String SITE_REGISTRABLE_DOMAIN = "example.test";
    private static final String FRONTEND_ORIGIN = "https://frontend.example.test";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CorsPropertiesConfiguration.class);

    @Test
    void allowedOrigins_설정이비어있으면ApiPublicOrigin없이Cors를허용하지않는다() {
        contextRunner.withPropertyValues("security.cors.allowed-origins=")
            .run(context -> {
                CorsProperties corsProperties = context.getBean(CorsProperties.class);
                CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(corsProperties);
                CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

                assertThat(context.getStartupFailure()).isNull();
                assertThat(corsProperties.getAllowedOrigins()).isEmpty();
                assertThat(configuration.checkOrigin(FRONTEND_ORIGIN)).isNull();
            });
    }

    @Test
    void allowedOrigins_ApiPublicOrigin과같은Site면허용한다() {
        withCorsConfiguration("https://frontend.example.test:8443")
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context.getBean(CorsProperties.class).isAllowedOrigin("https://frontend.example.test:8443"))
                    .isTrue();
            });
    }

    @Test
    void allowedOrigins_기본HttpsPort와Host대소문자는브라우저Origin형식으로정규화한다() {
        withCorsConfiguration("https://FRONTEND.example.test:443")
            .run(context -> {
                CorsProperties corsProperties = context.getBean(CorsProperties.class);
                CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(corsProperties);
                CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

                assertThat(context.getStartupFailure()).isNull();
                assertThat(corsProperties.getAllowedOrigins()).containsExactly(FRONTEND_ORIGIN);
                assertThat(corsProperties.isAllowedOrigin(FRONTEND_ORIGIN)).isTrue();
                assertThat(configuration.checkOrigin(FRONTEND_ORIGIN)).isEqualTo(FRONTEND_ORIGIN);
            });
    }

    @Test
    void allowedOrigins_ApiPublicOrigin이없으면애플리케이션시작에실패한다() {
        contextRunner.withPropertyValues(
                "security.cors.allowed-origins=" + FRONTEND_ORIGIN,
                "security.cors.site-registrable-domain=" + SITE_REGISTRABLE_DOMAIN
            )
            .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void allowedOrigins_다른Site면애플리케이션시작에실패한다() {
        withCorsConfiguration("https://frontend.other.test")
            .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @ParameterizedTest
    @MethodSource("invalidAllowedOrigins")
    void allowedOrigins_잘못된형식이면애플리케이션시작에실패한다(String origin) {
        withCorsConfiguration(origin)
            .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @ParameterizedTest
    @MethodSource("invalidApiPublicOrigins")
    void apiPublicOrigin_잘못된형식이면애플리케이션시작에실패한다(String origin) {
        contextRunner.withPropertyValues(
                "security.cors.allowed-origins=" + FRONTEND_ORIGIN,
                "security.cors.api-public-origin=" + origin,
                "security.cors.site-registrable-domain=" + SITE_REGISTRABLE_DOMAIN
            )
            .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    private ApplicationContextRunner withCorsConfiguration(String allowedOrigin) {
        return contextRunner.withPropertyValues(
            "security.cors.allowed-origins=" + allowedOrigin,
            "security.cors.api-public-origin=" + API_PUBLIC_ORIGIN,
            "security.cors.site-registrable-domain=" + SITE_REGISTRABLE_DOMAIN
        );
    }

    private static Stream<String> invalidAllowedOrigins() {
        return Stream.of(
            "http://frontend.example.test",
            "https://frontend.example.test/",
            "https://frontend.example.test/path",
            "https://frontend.example.test?query=value",
            "https://frontend.example.test#fragment",
            "https://*.example.test",
            "https://127.0.0.1"
        );
    }

    private static Stream<String> invalidApiPublicOrigins() {
        return Stream.of(
            "",
            "http://api.example.test",
            "https://api.example.test/path",
            "https://api.other.test",
            "https://127.0.0.1"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class CorsPropertiesConfiguration {
    }
}
