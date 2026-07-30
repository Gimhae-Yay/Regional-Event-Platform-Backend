package io.regionevent.regioneventbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import jakarta.servlet.http.Cookie;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfigIntegrationTest.SecurityTestController.class)
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private JwtAccessTokenProperties jwtAccessTokenProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicAuthenticationPath_withoutAccessToken_isAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
            .andExpect(status().isNoContent())
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void protectedPath_withoutAccessToken_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/test/protected"))
            .andExpect(status().isUnauthorized())
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
    void accessDeniedHandler_returnsForbiddenResponse() throws Exception {
        ApiResponseAccessDeniedHandler accessDeniedHandler = new ApiResponseAccessDeniedHandler(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
            new MockHttpServletRequest(),
            response,
            new AccessDeniedException("forbidden")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
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

    @RestController
    static class SecurityTestController {

        @PostMapping("/api/v1/auth/login")
        ResponseEntity<Void> login() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/test/protected")
        java.util.Map<String, Object> protectedResource(Authentication authentication) {
            return java.util.Map.of("userId", authentication.getPrincipal());
        }
    }
}
