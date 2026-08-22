package io.regionevent.regioneventbackend.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginControllerIntegrationTest {

    private static final String EMAIL = "visitor@example.com";
    private static final String PASSWORD = "LocalStamp!2026";
    private static final String ALLOWED_ORIGIN = "https://frontend.example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void login_withActiveUser_returnsTokensAndRoles() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE, PASSWORD);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));

        mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": " Visitor@Example.com ",
                      "password": "LocalStamp!2026"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=1209600")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
            .andExpect(jsonPath("$.data.userId").value(user.getUserId().toString()))
            .andExpect(jsonPath("$.data.roles[0]").value("VISITOR"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

    }

    @Test
    void login_withActiveUserWithoutRole_returnsEmptyRoles() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(user.getUserId().toString()))
            .andExpect(jsonPath("$.data.roles").isEmpty());
    }

    @Test
    void login_withInvalidInput_returnsInvalidInputWithoutTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "password": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    }

    @Test
    void login_withUnknownEmail_returnsInvalidCredentialsWithoutTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest(EMAIL, PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    }

    @Test
    void login_withWrongPassword_returnsInvalidCredentialsWithoutTokens() throws Exception {
        saveUser(AppUserStatus.ACTIVE, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest(EMAIL, "WrongPassword!2026")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    }

    @Test
    void login_withWithdrawingUser_returnsInvalidCredentialsWithoutTokens() throws Exception {
        saveUser(AppUserStatus.WITHDRAWING, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest(EMAIL, PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    }

    private AppUser saveUser(AppUserStatus status, String password) {
        return appUserRepository.saveAndFlush(new AppUser(
            EMAIL,
            passwordEncoder.encode(password),
            "홍길동",
            "01012345678",
            status
        ));
    }

    private String loginRequest(String email, String password) {
        return """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);
    }

}
