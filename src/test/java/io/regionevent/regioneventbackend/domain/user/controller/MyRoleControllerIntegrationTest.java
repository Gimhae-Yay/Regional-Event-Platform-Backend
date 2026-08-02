package io.regionevent.regioneventbackend.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenProperties;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyRoleControllerIntegrationTest {

    private static final String MY_ROLE_PATH = "/api/v1/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private OperatorApplicationRepository operatorApplicationRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private JwtAccessTokenProperties jwtAccessTokenProperties;

    @Test
    void getMyRoles_withVisitorAssignment_returnsNullRegion() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));

        mockMvc.perform(get(MY_ROLE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 역할과 담당 지역 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.roleAssignments[0].role").value("VISITOR"))
            .andExpect(jsonPath("$.data.roleAssignments[0].regionId").isEmpty())
            .andExpect(jsonPath("$.data.roleAssignments[0].regionName").isEmpty());
    }

    @Test
    void getMyRoles_withOperatorAssignment_returnsAssignedRegion() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE);
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.OPERATOR, region));

        mockMvc.perform(get(MY_ROLE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleAssignments[0].role").value("OPERATOR"))
            .andExpect(jsonPath("$.data.roleAssignments[0].regionId").value(region.getRegionId().toString()))
            .andExpect(jsonPath("$.data.roleAssignments[0].regionName").value("김해시"));
    }

    @Test
    void getMyRoles_withPendingOperatorApplication_returnsEmptyAssignments() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE);
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            user,
            region,
            "김해 지역 행사 운영자 신청",
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));

        mockMvc.perform(get(MY_ROLE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleAssignments").isArray())
            .andExpect(jsonPath("$.data.roleAssignments").isEmpty());
    }

    @Test
    void getMyRoles_withWithdrawingUser_returnsForbidden() throws Exception {
        AppUser user = saveUser(AppUserStatus.WITHDRAWING);

        mockMvc.perform(get(MY_ROLE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getMyRoles_withoutAccessToken_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get(MY_ROLE_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getMyRoles_withMalformedAccessToken_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get(MY_ROLE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getMyRoles_withExpiredAccessToken_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get(MY_ROLE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private AppUser saveUser(AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            "user@example.com",
            "password-hash",
            "홍길동",
            "01012345678",
            status
        ));
    }

    private String bearerTokenFor(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private String expiredAccessToken() {
        Instant issuedAt = Instant.now().minusSeconds(901);
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
}
