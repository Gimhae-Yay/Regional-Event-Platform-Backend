package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupControllerIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Test
    void signupVisitor_createsActiveUserAndVisitorRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": " Visitor@Example.com ",
                      "password": "LocalStamp!2026",
                      "name": " 홍길동 ",
                      "phone": "010-1234-5678",
                      "requestedRole": "VISITOR"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회원가입에 성공했습니다."))
            .andExpect(jsonPath("$.data.requestedRole").value("VISITOR"))
            .andExpect(jsonPath("$.data.assignedRole").value("VISITOR"))
            .andExpect(jsonPath("$.data.operatorApplicationStatus").isEmpty())
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.password").doesNotExist());

        var user = appUserRepository.findAll().getFirst();
        assertThat(user.getLoginIdentifier()).isEqualTo("visitor@example.com");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getStatus()).isEqualTo(AppUserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("LocalStamp!2026", user.getPasswordHash())).isTrue();
        assertThat(userRoleAssignmentRepository.findAll())
            .singleElement()
            .satisfies(assignment -> {
                assertThat(assignment.getRole()).isEqualTo(UserRole.VISITOR);
                assertThat(assignment.getRegion()).isNull();
            });
        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @Test
    void signupOperator_createsPendingApplicationWithoutRoleAssignment() throws Exception {
        Region publicRegion = regionRepository.saveAndFlush(new Region("GIMHAE", "김해", true));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "operator@example.com",
                      "password": "LocalStamp!2026",
                      "name": "홍길동",
                      "phone": "01012345678",
                      "requestedRole": "OPERATOR",
                      "requestedRegionId": "%d",
                      "businessInformation": " 지역행사 주식회사 "
                    }
                    """.formatted(publicRegion.getRegionId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.requestedRole").value("OPERATOR"))
            .andExpect(jsonPath("$.data.assignedRole").isEmpty())
            .andExpect(jsonPath("$.data.operatorApplicationStatus").value("PENDING"));

        assertThat(userRoleAssignmentRepository.count()).isZero();
        assertThat(operatorApplicationRepository.findAll())
            .singleElement()
            .satisfies(application -> {
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.PENDING);
                assertThat(application.getRequestedRegion().getRegionId()).isEqualTo(publicRegion.getRegionId());
                assertThat(application.getBusinessInformation()).isEqualTo("지역행사 주식회사");
                assertThat(application.getInspectedUser()).isNull();
            });
    }

    @Test
    void signupVisitor_withOperatorOnlyFields_returnsInvalidInputWithoutCreatingUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "visitor@example.com",
                      "password": "LocalStamp!2026",
                      "name": "홍길동",
                      "phone": "01012345678",
                      "requestedRole": "VISITOR",
                      "requestedRegionId": "1"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(appUserRepository.count()).isZero();
        assertThat(userRoleAssignmentRepository.count()).isZero();
        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @Test
    void signupOperator_withNonPublicRegion_returnsNotFoundWithoutCreatingUser() throws Exception {
        Region privateRegion = regionRepository.saveAndFlush(new Region("PRIVATE", "비공개 지역", false));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "operator@example.com",
                      "password": "LocalStamp!2026",
                      "name": "홍길동",
                      "phone": "01012345678",
                      "requestedRole": "OPERATOR",
                      "requestedRegionId": "%d",
                      "businessInformation": "지역행사 주식회사"
                    }
                    """.formatted(privateRegion.getRegionId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(appUserRepository.count()).isZero();
        assertThat(userRoleAssignmentRepository.count()).isZero();
        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @Test
    void signupOperator_withoutRequestedRegion_returnsInvalidInputWithoutCreatingUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "operator@example.com",
                      "password": "LocalStamp!2026",
                      "name": "홍길동",
                      "phone": "01012345678",
                      "requestedRole": "OPERATOR",
                      "businessInformation": "지역행사 주식회사"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(appUserRepository.count()).isZero();
        assertThat(userRoleAssignmentRepository.count()).isZero();
        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @Test
    void signup_withNormalizedDuplicateEmail_returnsConflictWithoutAdditionalRecords() throws Exception {
        String firstRequest = """
            {
              "email": "visitor@example.com",
              "password": "LocalStamp!2026",
              "name": "홍길동",
              "phone": "01012345678",
              "requestedRole": "VISITOR"
            }
            """;
        String duplicateRequest = """
            {
              "email": " VISITOR@example.com ",
              "password": "LocalStamp!2026",
              "name": "홍길동",
              "phone": "01012345678",
              "requestedRole": "VISITOR"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequest))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequest))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_IDENTIFIER"));

        assertThat(appUserRepository.count()).isEqualTo(1);
        assertThat(userRoleAssignmentRepository.count()).isEqualTo(1);
    }
}
