package io.regionevent.regioneventbackend.global.config;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;

import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;
import io.regionevent.regioneventbackend.global.security.access.BearerAccessTokenAuthenticationFilter;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenProperties;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.common.ApiResponseAccessDeniedHandler;
import io.regionevent.regioneventbackend.global.security.common.ApiResponseAuthenticationEntryPoint;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenProperties;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.JwtRefreshTokenProperties;
import io.regionevent.regioneventbackend.global.security.refresh.JwtRefreshTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneProperties;
import io.regionevent.regioneventbackend.domain.payment.service.PortOneFakeProperties;

@Configuration
@EnableConfigurationProperties({
    JwtAccessTokenProperties.class,
    JwtRefreshTokenProperties.class,
    QrTokenProperties.class,
    PortOneProperties.class,
    PortOneFakeProperties.class
})
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(BCRYPT_STRENGTH));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    @Bean
    public JwtAccessTokenService jwtAccessTokenService(
        JwtAccessTokenProperties jwtAccessTokenProperties,
        Clock clock
    ) {
        return new JwtAccessTokenService(jwtAccessTokenProperties, clock);
    }

    @Bean
    public JwtRefreshTokenService jwtRefreshTokenService(
        JwtRefreshTokenProperties jwtRefreshTokenProperties,
        Clock clock
    ) {
        return new JwtRefreshTokenService(jwtRefreshTokenProperties, clock);
    }

    @Bean
    public QrTokenService qrTokenService(
        QrTokenProperties qrTokenProperties,
        Clock clock
    ) {
        return new QrTokenService(qrTokenProperties, clock);
    }

    @Bean
    public RefreshTokenService refreshTokenService(
        JwtRefreshTokenService jwtRefreshTokenService,
        Clock clock
    ) {
        return new RefreshTokenService(jwtRefreshTokenService, clock);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiResponseAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiResponseAccessDeniedHandler(objectMapper);
    }

    @Bean
    public BearerAccessTokenAuthenticationFilter bearerAccessTokenAuthenticationFilter(
        JwtAccessTokenService jwtAccessTokenService,
        AuthenticationEntryPoint authenticationEntryPoint
    ) {
        return new BearerAccessTokenAuthenticationFilter(jwtAccessTokenService, authenticationEntryPoint);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        BearerAccessTokenAuthenticationFilter bearerAccessTokenAuthenticationFilter,
        AuthenticationEntryPoint authenticationEntryPoint,
        AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/webhooks/portone",
                    "/internal/performance/fixtures/reset"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/regions",
                    "/api/v1/regions/*/home",
                    "/api/v1/regions/*/missions",
                    "/api/v1/missions/*",
                    "/api/v1/contents",
                    "/api/v1/contents/*",
                    "/api/v1/contents/*/reviews",
                    "/api/v1/contents/*/sessions",
                    "/api/v1/sessions/*"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/platform-admin/admin-accounts",
                    "/api/v1/platform-admin/admin-accounts/*/deactivate"
                ).hasAuthority(AccessTokenAuthority.SUPER_ADMIN.claimValue())
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/platform-admin/**"
                ).hasAnyAuthority(
                    AccessTokenAuthority.SUPER_ADMIN.claimValue(),
                    AccessTokenAuthority.PLATFORM_ADMIN.claimValue()
                )
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/platform-admin/**"
                ).hasAnyAuthority(
                    AccessTokenAuthority.SUPER_ADMIN.claimValue(),
                    AccessTokenAuthority.PLATFORM_ADMIN.claimValue()
                )
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/v1/platform-admin/**"
                ).hasAnyAuthority(
                    AccessTokenAuthority.SUPER_ADMIN.claimValue(),
                    AccessTokenAuthority.PLATFORM_ADMIN.claimValue()
                )
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/region-admin/**",
                    "/region-admin/qr-exceptions",
                    "/region-admin/qr-exceptions/*"
                ).hasAuthority(AccessTokenAuthority.REGION_ADMIN.claimValue())
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/region-admin/**"
                ).hasAuthority(AccessTokenAuthority.REGION_ADMIN.claimValue())
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/v1/region-admin/**"
                ).hasAuthority(AccessTokenAuthority.REGION_ADMIN.claimValue())
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/v1/region-admin/**"
                ).hasAuthority(AccessTokenAuthority.REGION_ADMIN.claimValue())
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/v1/region-admin/**"
                ).hasAuthority(AccessTokenAuthority.REGION_ADMIN.claimValue())
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/operator/operator-requests"
                ).authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/operator/**",
                    "/operator/check-ins",
                    "/operator/check-ins/manual",
                    "/operator/contents/*"
                ).hasAuthority(AccessTokenAuthority.OPERATOR.claimValue())
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/operator/**",
                    "/operator/check-ins",
                    "/operator/check-ins/manual"
                ).hasAuthority(AccessTokenAuthority.OPERATOR.claimValue())
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/v1/operator/**"
                ).hasAuthority(AccessTokenAuthority.OPERATOR.claimValue())
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/v1/operator/**"
                ).hasAuthority(AccessTokenAuthority.OPERATOR.claimValue())
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/v1/operator/**"
                ).hasAuthority(AccessTokenAuthority.OPERATOR.claimValue())
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/visits/*/reviews",
                    "/api/v1/missions/*/participations",
                    "/api/v1/me/mission-participations/*/rewards/claim"
                ).hasAuthority(AccessTokenAuthority.VISITOR.claimValue())
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/v1/reviews/*"
                ).hasAuthority(AccessTokenAuthority.VISITOR.claimValue())
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/v1/reviews/*"
                ).hasAuthority(AccessTokenAuthority.VISITOR.claimValue())
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/me/mission-participations",
                    "/api/v1/me/mission-participations/*"
                ).hasAuthority(AccessTokenAuthority.VISITOR.claimValue())
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterBefore(
                bearerAccessTokenAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .securityContext(Customizer.withDefaults());
        return http.build();
    }
}
