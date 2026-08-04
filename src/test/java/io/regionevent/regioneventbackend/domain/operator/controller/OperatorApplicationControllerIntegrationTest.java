package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperatorApplicationControllerIntegrationTest {

    private static final String OPERATOR_REQUEST_PATH = "/api/v1/operator/operator-requests";
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

    @Test
    void reapply_createsNewPendingApplicationWithoutReturningBusinessInformation() throws Exception {
        Region region = saveRegion(true);
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);
        createRejectedApplication(applicant, region);

        performReapplication(applicant, region.getRegionId(), "  New business information  ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 권한 신청에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").isNumber())
            .andExpect(jsonPath("$.data.requestedRegionId").value(region.getRegionId()))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.businessInformation").doesNotExist());

        assertThat(operatorApplicationRepository.findAll())
            .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.REJECTED)
            .hasSize(1);
        assertThat(operatorApplicationRepository.findAll())
            .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.PENDING)
            .singleElement()
            .satisfies(application -> {
                assertThat(application.getApplicant().getUserId()).isEqualTo(applicant.getUserId());
                assertThat(application.getRequestedRegion().getRegionId()).isEqualTo(region.getRegionId());
                assertThat(application.getBusinessInformation()).isEqualTo("New business information");
            });
    }

    @Test
    void reapply_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post(OPERATOR_REQUEST_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedRegionId": 1,
                      "businessInformation": "Business information"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void reapply_withMalformedJson_returnsInvalidJsonWithoutCreatingApplication() throws Exception {
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);

        mockMvc.perform(post(OPERATOR_REQUEST_PATH)
                .header("Authorization", bearerToken(applicant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedRegionId\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @Test
    void reapply_withBlankBusinessInformation_returnsInvalidInputWithoutCreatingApplication() throws Exception {
        Region region = saveRegion(true);
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);

        performReapplication(applicant, region.getRegionId(), " ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @Test
    void reapply_withNonPublicRegion_returnsNotFoundWithoutCreatingApplication() throws Exception {
        Region region = saveRegion(false);
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);
        createRejectedApplication(applicant, region);

        performReapplication(applicant, region.getRegionId(), "Business information")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(operatorApplicationRepository.findAll())
            .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.PENDING)
            .isEmpty();
    }

    @Test
    void reapply_withPendingApplication_returnsPendingConflict() throws Exception {
        Region region = saveRegion(true);
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);
        createRejectedApplication(applicant, region);
        operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "Pending business information",
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));

        performReapplication(applicant, region.getRegionId(), "Business information")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_PENDING"));

        assertThat(operatorApplicationRepository.findAll())
            .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.PENDING)
            .singleElement();
    }

    @Test
    void reapply_withoutRejectedApplication_returnsReapplicationNotAllowed() throws Exception {
        Region region = saveRegion(true);
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);

        performReapplication(applicant, region.getRegionId(), "Business information")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED"));

        assertThat(operatorApplicationRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OPERATOR", "REGION_ADMIN"})
    void reapply_withOperatorAuthority_returnsForbidden(UserRole role) throws Exception {
        Region region = saveRegion(true);
        AppUser applicant = saveUser(AppUserStatus.ACTIVE);
        createRejectedApplication(applicant, region);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(applicant, role, region));

        performReapplication(applicant, region.getRegionId(), "Business information")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(operatorApplicationRepository.findAll())
            .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.PENDING)
            .isEmpty();
    }

    @Test
    void reapply_whenMemberIsNotActive_returnsForbidden() throws Exception {
        Region region = saveRegion(true);
        AppUser applicant = saveUser(AppUserStatus.WITHDRAWING);
        createRejectedApplication(applicant, region);

        performReapplication(applicant, region.getRegionId(), "Business information")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(operatorApplicationRepository.findAll())
            .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.PENDING)
            .isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions performReapplication(
        AppUser applicant,
        Long requestedRegionId,
        String businessInformation
    ) throws Exception {
        return mockMvc.perform(post(OPERATOR_REQUEST_PATH)
            .header("Authorization", bearerToken(applicant))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "requestedRegionId": %d,
                  "businessInformation": "%s"
                }
                """.formatted(requestedRegionId, businessInformation)));
    }

    private AppUser saveUser(AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "Operator Applicant",
            "010-1234-5678",
            status
        ));
    }

    private Region saveRegion(boolean isPublic) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region("R" + suffix, "Test region", isPublic));
    }

    private void createRejectedApplication(AppUser applicant, Region region) {
        AppUser inspector = saveUser(AppUserStatus.ACTIVE);
        operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "Previous business information",
            OperatorApplicationStatus.REJECTED,
            inspector,
            "Rejected"
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }
}
