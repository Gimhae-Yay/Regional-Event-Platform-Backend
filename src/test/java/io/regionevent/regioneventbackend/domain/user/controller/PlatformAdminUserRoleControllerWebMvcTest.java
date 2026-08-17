package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.service.ChangeRegionAdminRoleUseCase;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminRoleChange;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminRoleChangeResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest(PlatformAdminUserRoleController.class)
class PlatformAdminUserRoleControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    private static final Long TARGET_USER_ID = 200L;
    private static final Long REGION_ID = 12L;

    @MockitoBean
    private ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase;

    @Test
    void 지역관리자_임명_요청을_성공_응답으로_반환한다() throws Exception {
        when(changeRegionAdminRoleUseCase.change(
            eq(AUTHENTICATED_USER_ID),
            eq(TARGET_USER_ID),
            eq(RegionAdminRoleChange.REGION_ADMIN),
            eq(REGION_ID),
            eq("REGION_ADMIN_APPOINTMENT"),
            eq("OPS-2026-0809-001"),
            any(UUID.class)
        )).thenReturn(activeResult());

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "role": "REGION_ADMIN",
                  "regionId": "12",
                  "reasonCode": "REGION_ADMIN_APPOINTMENT",
                  "evidenceReference": "OPS-2026-0809-001"
                }
                """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("지역관리자 역할 변경에 성공했습니다."))
            .andExpect(jsonPath("$.data.userId").value(TARGET_USER_ID.toString()))
            .andExpect(jsonPath("$.data.roleAssignmentId").value("305"))
            .andExpect(jsonPath("$.data.role").value("REGION_ADMIN"))
            .andExpect(jsonPath("$.data.regionId").value(REGION_ID.toString()))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(changeRegionAdminRoleUseCase).change(
            eq(AUTHENTICATED_USER_ID),
            eq(TARGET_USER_ID),
            eq(RegionAdminRoleChange.REGION_ADMIN),
            eq(REGION_ID),
            eq("REGION_ADMIN_APPOINTMENT"),
            eq("OPS-2026-0809-001"),
            any(UUID.class)
        );
    }

    @Test
    void 회수_응답은_활성_역할을_null로_반환한다() throws Exception {
        when(changeRegionAdminRoleUseCase.change(
            eq(AUTHENTICATED_USER_ID),
            eq(TARGET_USER_ID),
            eq(RegionAdminRoleChange.NONE),
            eq(null),
            eq("REGION_ADMIN_REVOCATION"),
            eq("OPS-2026-0809-002"),
            any(UUID.class)
        )).thenReturn(new RegionAdminRoleChangeResult(
            TARGET_USER_ID,
            305L,
            null,
            REGION_ID,
            UserRoleAssignmentStatus.REVOKED,
            Instant.parse("2026-08-09T00:00:00Z"),
            Instant.parse("2026-08-09T01:00:00Z")
        ));

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "role": "NONE",
                  "reasonCode": "REGION_ADMIN_REVOCATION",
                  "evidenceReference": "OPS-2026-0809-002"
                }
                """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").doesNotExist())
            .andExpect(jsonPath("$.data.status").value("REVOKED"))
            .andExpect(jsonPath("$.data.revokedAt").value("2026-08-09T01:00:00Z"));
    }

    @Test
    void 식별자와_조건부_요청_검증_오류는_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/0/role")
            .contentType("application/json")
            .content(validAppointmentRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/not-a-number/role")
            .contentType("application/json")
            .content(validAppointmentRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "role": "NONE",
                  "regionId": "12",
                  "reasonCode": "REGION_ADMIN_REVOCATION",
                  "evidenceReference": "OPS-2026-0809-002"
                }
                """)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "role": "REGION_ADMIN",
                  "regionId": "not-a-number",
                  "reasonCode": "REGION_ADMIN_APPOINTMENT",
                  "evidenceReference": "OPS-2026-0809-001"
                }
                """)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(changeRegionAdminRoleUseCase);
    }

    @Test
    void 민감해보이는_증빙_참조도_ADR에_따라_형식과_길이가_맞으면_전달한다() throws Exception {
        when(changeRegionAdminRoleUseCase.change(
            eq(AUTHENTICATED_USER_ID),
            eq(TARGET_USER_ID),
            eq(RegionAdminRoleChange.REGION_ADMIN),
            eq(REGION_ID),
            eq("REGION_ADMIN_APPOINTMENT"),
            eq("token=abc"),
            any(UUID.class)
        )).thenReturn(activeResult());

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "role": "REGION_ADMIN",
                  "regionId": "12",
                  "reasonCode": "REGION_ADMIN_APPOINTMENT",
                  "evidenceReference": "token=abc"
                }
                """)))
            .andExpect(status().isOk());

        verify(changeRegionAdminRoleUseCase).change(
            eq(AUTHENTICATED_USER_ID),
            eq(TARGET_USER_ID),
            eq(RegionAdminRoleChange.REGION_ADMIN),
            eq(REGION_ID),
            eq("REGION_ADMIN_APPOINTMENT"),
            eq("token=abc"),
            any(UUID.class)
        );
    }

    @Test
    void 권한_대상_없음_역할_충돌은_계약된_오류를_반환한다() throws Exception {
        assertBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        assertBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        assertBusinessError(ErrorCode.ROLE_ASSIGNMENT_CONFLICT, 409, "ROLE_ASSIGNMENT_CONFLICT");
    }

    @Test
    void 인증되지_않은_요청은_미인증_오류를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content(validAppointmentRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private RegionAdminRoleChangeResult activeResult() {
        return new RegionAdminRoleChangeResult(
            TARGET_USER_ID,
            305L,
            UserRole.REGION_ADMIN,
            REGION_ID,
            UserRoleAssignmentStatus.ACTIVE,
            Instant.parse("2026-08-09T00:00:00Z"),
            null
        );
    }

    private void assertBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(changeRegionAdminRoleUseCase.change(
            eq(AUTHENTICATED_USER_ID),
            eq(TARGET_USER_ID),
            eq(RegionAdminRoleChange.REGION_ADMIN),
            eq(REGION_ID),
            eq("REGION_ADMIN_APPOINTMENT"),
            eq("OPS-2026-0809-001"),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", TARGET_USER_ID)
            .contentType("application/json")
            .content(validAppointmentRequest())))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private String validAppointmentRequest() {
        return """
            {
              "role": "REGION_ADMIN",
              "regionId": "12",
              "reasonCode": "REGION_ADMIN_APPOINTMENT",
              "evidenceReference": "OPS-2026-0809-001"
            }
            """;
    }
}
