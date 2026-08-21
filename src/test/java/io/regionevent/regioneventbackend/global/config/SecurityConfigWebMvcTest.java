package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import javax.crypto.SecretKey;
import javax.sql.DataSource;

import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManagerFactory;

import com.zaxxer.hikari.HikariDataSource;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import tools.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenProperties;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.common.ApiResponseAccessDeniedHandler;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;

@WebMvcTest(SecurityConfigWebMvcTest.SecurityTestController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class,
    SecurityConfigWebMvcTest.SecurityTestController.class
})
class SecurityConfigWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private JwtAccessTokenProperties jwtAccessTokenProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void securityMvcSlice_데이터베이스인프라를초기화하지않는다() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Flyway.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(EntityManagerFactory.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(HikariDataSource.class)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("publicRequests")
    void publicPath_withoutAccessToken_isAllowed(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isNotIn(401, 403))
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void publicPath_withNonBearerAuthorizationHeader_isAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/contents")
                .header(HttpHeaders.AUTHORIZATION, "Basic malformed"))
            .andExpect(status().isNoContent());
    }

    @Test
    void pathAdjacentToPublicPath_withoutAccessToken_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/regions/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedPath_withoutAccessToken_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/test/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8)))
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void protectedPath_withValidAccessToken_setsUserIdAsPrincipal() throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L);

        mockMvc.perform(get("/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(1))
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @ParameterizedTest
    @MethodSource("roleProtectedRequests")
    void roleProtectedPath_withRequiredAuthority_isAllowed(
        HttpMethod method,
        String path,
        AccessTokenAuthority authority
    ) throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L, List.of(authority));

        mockMvc.perform(request(method, path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void superAdminOnlyPath_withPlatformAdminAuthority_returnsForbiddenResponse() throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L, List.of(AccessTokenAuthority.PLATFORM_ADMIN));

        mockMvc.perform(request(HttpMethod.POST, "/api/v1/platform-admin/admin-accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void superAdminOnlyGetPath_withPlatformAdminAuthority_returnsForbiddenResponse() throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L, List.of(AccessTokenAuthority.PLATFORM_ADMIN));

        mockMvc.perform(request(HttpMethod.GET, "/api/v1/platform-admin/admin-accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void roleProtectedPath_withInsufficientAuthority_returnsForbiddenBeforeController() throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L, List.of(AccessTokenAuthority.VISITOR));

        mockMvc.perform(get("/api/v1/operator/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @MethodSource("legacyRoleProtectedRequests")
    void legacyRoleProtectedPath_withInsufficientAuthority_returnsForbiddenBeforeController(
        HttpMethod method,
        String path
    ) throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L, List.of(AccessTokenAuthority.VISITOR));

        mockMvc.perform(request(method, path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void operatorRequest_withEmptyAuthorities_isAuthenticatedOnly() throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L);

        mockMvc.perform(request(HttpMethod.POST, "/api/v1/operator/operator-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @MethodSource("roleProtectedRequests")
    void roleProtectedPath_withEmptyAuthorities_returnsForbiddenBeforeController(
        HttpMethod method,
        String path,
        AccessTokenAuthority ignoredAuthority
    ) throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L);

        mockMvc.perform(request(method, path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void compatibilityTokenWithoutAuthorities_isAuthenticatedOnlyAndForbiddenOnRoleProtectedPath() throws Exception {
        String accessToken = issueAccessTokenWithoutAuthorities();

        mockMvc.perform(get("/api/v1/me/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/operator/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void publicGetAndProtectedWriteWithSamePath_haveDifferentAuthenticationRequirements() throws Exception {
        mockMvc.perform(get("/api/v1/contents"))
            .andExpect(status().isNoContent());

        mockMvc.perform(request(HttpMethod.POST, "/api/v1/contents"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedPath_withRefreshTokenCookieOnly_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/test/protected")
                .cookie(new Cookie("refreshToken", "refresh-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedPath_withMalformedBearerToken_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedPath_withAccessTokenWithoutAudience_returnsUnauthenticatedResponse() throws Exception {
        String accessTokenWithoutAudience = issueAccessTokenWithoutAudience();

        mockMvc.perform(get("/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenWithoutAudience))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedPath_withTokenSignedByExpiredPreviousKey_returnsUnauthenticatedResponse() throws Exception {
        String expiredPreviousKeyToken = issueAccessTokenWithExpiredPreviousKey();

        mockMvc.perform(get("/test/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredPreviousKeyToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void accessDeniedHandler_returnsForbiddenResponse() throws Exception {
        ApiResponseAccessDeniedHandler accessDeniedHandler = new ApiResponseAccessDeniedHandler(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
            new MockHttpServletRequest(),
            response,
            new AccessDeniedException("forbidden")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    private static Stream<Arguments> publicRequests() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, "/api/v1/auth/signup"),
            Arguments.of(HttpMethod.POST, "/api/v1/auth/login"),
            Arguments.of(HttpMethod.POST, "/api/v1/auth/refresh"),
            Arguments.of(HttpMethod.POST, "/api/v1/auth/logout"),
            Arguments.of(HttpMethod.GET, "/api/v1/regions"),
            Arguments.of(HttpMethod.GET, "/api/v1/regions/1/home"),
            Arguments.of(HttpMethod.GET, "/api/v1/regions/1/missions"),
            Arguments.of(HttpMethod.GET, "/api/v1/missions/1"),
            Arguments.of(HttpMethod.GET, "/api/v1/contents"),
            Arguments.of(HttpMethod.GET, "/api/v1/contents/1"),
            Arguments.of(HttpMethod.GET, "/api/v1/contents/1/reviews"),
            Arguments.of(HttpMethod.GET, "/api/v1/contents/1/sessions"),
            Arguments.of(HttpMethod.GET, "/api/v1/sessions/1")
        );
    }

    private static Stream<Arguments> roleProtectedRequests() {
        return Stream.of(
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/platform-admin/me",
                AccessTokenAuthority.SUPER_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/platform-admin/me",
                AccessTokenAuthority.PLATFORM_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/platform-admin/admin-accounts",
                AccessTokenAuthority.SUPER_ADMIN
            ),
            Arguments.of(
                HttpMethod.POST,
                "/api/v1/platform-admin/admin-accounts",
                AccessTokenAuthority.SUPER_ADMIN
            ),
            Arguments.of(
                HttpMethod.POST,
                "/api/v1/platform-admin/admin-accounts/1/deactivate",
                AccessTokenAuthority.SUPER_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/platform-admin/protected",
                AccessTokenAuthority.SUPER_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/platform-admin/protected",
                AccessTokenAuthority.PLATFORM_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/region-admin/protected",
                AccessTokenAuthority.REGION_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/operator/protected",
                AccessTokenAuthority.OPERATOR
            ),
            Arguments.of(
                HttpMethod.POST,
                "/api/v1/visits/1/reviews",
                AccessTokenAuthority.VISITOR
            ),
            Arguments.of(
                HttpMethod.POST,
                "/api/v1/missions/1/participations",
                AccessTokenAuthority.VISITOR
            ),
            Arguments.of(
                HttpMethod.PATCH,
                "/api/v1/reviews/1",
                AccessTokenAuthority.VISITOR
            ),
            Arguments.of(
                HttpMethod.DELETE,
                "/api/v1/reviews/1",
                AccessTokenAuthority.VISITOR
            ),
            Arguments.of(
                HttpMethod.GET,
                "/api/v1/me/mission-participations",
                AccessTokenAuthority.VISITOR
            ),
            Arguments.of(
                HttpMethod.GET,
                "/operator/contents/1",
                AccessTokenAuthority.OPERATOR
            ),
            Arguments.of(
                HttpMethod.POST,
                "/operator/check-ins",
                AccessTokenAuthority.OPERATOR
            ),
            Arguments.of(
                HttpMethod.POST,
                "/operator/check-ins/manual",
                AccessTokenAuthority.OPERATOR
            ),
            Arguments.of(
                HttpMethod.GET,
                "/region-admin/qr-exceptions",
                AccessTokenAuthority.REGION_ADMIN
            ),
            Arguments.of(
                HttpMethod.GET,
                "/region-admin/qr-exceptions/1",
                AccessTokenAuthority.REGION_ADMIN
            )
        );
    }

    private static Stream<Arguments> legacyRoleProtectedRequests() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, "/operator/check-ins"),
            Arguments.of(HttpMethod.POST, "/operator/check-ins/manual"),
            Arguments.of(HttpMethod.GET, "/operator/contents/1"),
            Arguments.of(HttpMethod.GET, "/region-admin/qr-exceptions"),
            Arguments.of(HttpMethod.GET, "/region-admin/qr-exceptions/1")
        );
    }

    private String issueAccessTokenWithoutAudience() {
        Instant issuedAt = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtAccessTokenProperties.getActiveKey()));

        return Jwts.builder()
            .header()
                .type("JWT")
                .keyId(jwtAccessTokenProperties.getActiveKeyId())
                .and()
            .issuer(jwtAccessTokenProperties.getIssuer())
            .subject("1")
            .claim("token_type", "ACCESS")
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(900)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private String issueAccessTokenWithoutAuthorities() {
        Instant issuedAt = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtAccessTokenProperties.getActiveKey()));

        return Jwts.builder()
            .header()
                .type("JWT")
                .keyId(jwtAccessTokenProperties.getActiveKeyId())
                .and()
            .issuer(jwtAccessTokenProperties.getIssuer())
            .audience()
                .add(jwtAccessTokenProperties.getAudience())
                .and()
            .subject("1")
            .claim("token_type", "ACCESS")
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(900)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private String issueAccessTokenWithExpiredPreviousKey() {
        Instant issuedAt = Instant.now();
        JwtAccessTokenProperties.VerificationKey previousKey = jwtAccessTokenProperties.getPreviousKeys().getFirst();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(previousKey.getKey()));

        return Jwts.builder()
            .header()
                .type("JWT")
                .keyId(previousKey.getKeyId())
                .and()
            .issuer(jwtAccessTokenProperties.getIssuer())
            .audience()
                .add(jwtAccessTokenProperties.getAudience())
                .and()
            .subject("1")
            .claim("token_type", "ACCESS")
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(900)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    @RestController
    static class SecurityTestController {

        @PostMapping({
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh"
        })
        ResponseEntity<Void> publicAuthenticationResource() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping({
            "/api/v1/regions",
            "/api/v1/regions/{regionId}/home",
            "/api/v1/regions/{regionId}/missions",
            "/api/v1/contents",
            "/api/v1/contents/{contentId}",
            "/api/v1/contents/{contentId}/reviews",
            "/api/v1/contents/{contentId}/sessions",
            "/api/v1/sessions/{sessionId}"
        })
        ResponseEntity<Void> publicCatalogResource() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/test/protected")
        java.util.Map<String, Object> protectedResource(Authentication authentication) {
            return java.util.Map.of("userId", authentication.getPrincipal());
        }

        @PostMapping({
            "/api/v1/platform-admin/admin-accounts",
            "/api/v1/platform-admin/admin-accounts/{userId}/deactivate",
            "/api/v1/operator/operator-requests",
            "/api/v1/visits/{visitId}/reviews",
            "/api/v1/missions/{missionId}/participations",
            "/api/v1/contents",
            "/operator/check-ins",
            "/operator/check-ins/manual"
        })
        ResponseEntity<Void> postResource() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping({
            "/api/v1/platform-admin/me",
            "/api/v1/platform-admin/admin-accounts",
            "/api/v1/platform-admin/protected",
            "/api/v1/region-admin/protected",
            "/api/v1/operator/protected",
            "/api/v1/me/protected",
            "/api/v1/me/mission-participations",
            "/operator/contents/{contentId}",
            "/region-admin/qr-exceptions",
            "/region-admin/qr-exceptions/{exceptionId}"
        })
        ResponseEntity<Void> roleProtectedResource() {
            return ResponseEntity.noContent().build();
        }

        @PatchMapping("/api/v1/reviews/{reviewId}")
        ResponseEntity<Void> updateReviewResource() {
            return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/api/v1/reviews/{reviewId}")
        ResponseEntity<Void> deleteReviewResource() {
            return ResponseEntity.noContent().build();
        }
    }
}
