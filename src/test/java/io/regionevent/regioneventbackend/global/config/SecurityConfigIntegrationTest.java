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
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import jakarta.servlet.http.Cookie;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenProperties;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.common.ApiResponseAccessDeniedHandler;

@SpringBootTest(properties = "security.cors.allowed-origins=https://local-stamp.org")
@AutoConfigureMockMvc
@Import(SecurityConfigIntegrationTest.SecurityTestController.class)
class SecurityConfigIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://local-stamp.org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private JwtAccessTokenProperties jwtAccessTokenProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @MethodSource("publicRequests")
    void publicPath_withoutAccessToken_isAllowed(HttpMethod method, String path) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = request(method, path);
        if (isAuthenticationPostRequest(method, path)) {
            requestBuilder.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
        }

        mockMvc.perform(requestBuilder)
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isNotIn(401, 403))
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void publicPath_withNonBearerAuthorizationHeader_isAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/contents")
                .header(HttpHeaders.AUTHORIZATION, "Basic malformed"))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isNotIn(401, 403));
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

    @Test
    void metricsEndpoint_withValidAccessToken_exposesAutoPublicationMetric() throws Exception {
        String accessToken = jwtAccessTokenService.issue(1L);

        mockMvc.perform(get("/actuator/metrics/content.publication.candidate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("content.publication.candidate"));
    }

    @Test
    void metricsEndpoint_withoutAccessToken_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/actuator/metrics/content.publication.candidate"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void healthEndpoint_withoutAccessToken_returnsHealthStatusWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isNotIn(401, 403))
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.components").doesNotExist());
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

    private static boolean isAuthenticationPostRequest(HttpMethod method, String path) {
        return method == HttpMethod.POST && path.startsWith("/api/v1/auth/");
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
    }
}
