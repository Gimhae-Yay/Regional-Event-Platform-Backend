package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
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

    private static final String VISITOR_SIGNUP_REQUEST = """
        {
          "email": "visitor@example.com",
          "password": "LocalStamp!2026",
          "name": "홍길동",
          "phone": "01012345678",
          "requestedRole": "VISITOR"
        }
        """;

    private static final int CONCURRENT_SIGNUP_REQUEST_COUNT = 2;
    private static final long CONCURRENT_SIGNUP_TIMEOUT_SECONDS = 5;

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

    @Test
    void signup_withNonAsciiPassword_returnsInvalidInputWithoutCreatingUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "visitor@example.com",
                      "password": "\\uAC00A123456",
                      "name": "홍길동",
                      "phone": "01012345678",
                      "requestedRole": "VISITOR"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(appUserRepository.count()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void signup_withSameEmailConcurrently_createsOneUserAndReturnsConflictForOtherRequest() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_SIGNUP_REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_SIGNUP_REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<MvcResult> firstRequest = submitSignupRequest(executorService, ready, start);
            Future<MvcResult> secondRequest = submitSignupRequest(executorService, ready, start);

            assertThat(ready.await(CONCURRENT_SIGNUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> completedResults = List.of(
                firstRequest.get(CONCURRENT_SIGNUP_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                secondRequest.get(CONCURRENT_SIGNUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertThat(completedResults)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(201, 409);
            assertThat(completedResults)
                .filteredOn(result -> result.getResponse().getStatus() == 409)
                .singleElement()
                .satisfies(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("DUPLICATE_LOGIN_IDENTIFIER"));
            assertThat(appUserRepository.count()).isEqualTo(1);
            assertThat(userRoleAssignmentRepository.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executorService.shutdownNow();
            userRoleAssignmentRepository.deleteAllInBatch();
            appUserRepository.deleteAllInBatch();
        }
    }

    private Future<MvcResult> submitSignupRequest(
        ExecutorService executorService,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        return executorService.submit(() -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VISITOR_SIGNUP_REQUEST))
                .andReturn();
        });
    }

}
