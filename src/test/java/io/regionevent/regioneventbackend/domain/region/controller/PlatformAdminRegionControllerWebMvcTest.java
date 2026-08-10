package io.regionevent.regioneventbackend.domain.region.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.region.service.CreateRegionResult;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase.CreateRegionCommand;
import io.regionevent.regioneventbackend.domain.region.service.GetPlatformAdminRegionsUseCase;
import io.regionevent.regioneventbackend.domain.region.service.PlatformAdminRegionListInfo;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusResult;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusUseCase;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusUseCase.UpdateRegionStatusCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(PlatformAdminRegionController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class PlatformAdminRegionControllerWebMvcTest {

    private static final Long AUTHENTICATED_USER_ID = 101L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-09T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreateRegionUseCase createRegionUseCase;

    @MockitoBean
    private GetPlatformAdminRegionsUseCase getPlatformAdminRegionsUseCase;

    @MockitoBean
    private UpdateRegionStatusUseCase updateRegionStatusUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getRegions_유효한요청_전체지역목록을반환한다() throws Exception {
        when(getPlatformAdminRegionsUseCase.get(AUTHENTICATED_USER_ID, null)).thenReturn(List.of(
            new PlatformAdminRegionListInfo(
                11L,
                "GIMHAE",
                "김해시",
                true,
                2L,
                CREATED_AT,
                UPDATED_AT
            )
        ));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체 지역 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.regions[0].regionId").value("11"))
            .andExpect(jsonPath("$.data.regions[0].regionCode").value("GIMHAE"))
            .andExpect(jsonPath("$.data.regions[0].name").value("김해시"))
            .andExpect(jsonPath("$.data.regions[0].isPublic").value(true))
            .andExpect(jsonPath("$.data.regions[0].regionAdminCount").value(2))
            .andExpect(jsonPath("$.data.regions[0].createdAt").value("2026-08-09T00:00:00Z"))
            .andExpect(jsonPath("$.data.regions[0].updatedAt").value("2026-08-09T01:00:00Z"));

        verify(getPlatformAdminRegionsUseCase).get(AUTHENTICATED_USER_ID, null);
    }

    @Test
    void getRegions_공개여부필터_유스케이스에전달한다() throws Exception {
        when(getPlatformAdminRegionsUseCase.get(AUTHENTICATED_USER_ID, false)).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions")
                .queryParam("isPublic", "false")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.regions").isArray())
            .andExpect(jsonPath("$.data.regions").isEmpty());

        verify(getPlatformAdminRegionsUseCase).get(AUTHENTICATED_USER_ID, false);
    }

    @Test
    void getRegions_잘못된공개여부_실패로그를남기고_유스케이스를호출하지않는다(
        CapturedOutput output
    ) throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions")
                .queryParam("isPublic", "yes")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getPlatformAdminRegionsUseCase);
        assertThat(output.getOut()).contains(
            "Platform admin region list queried. requestId=",
            "resultCount=0, resultCode=INVALID_TYPE"
        );
    }

    @Test
    void getRegions_권한없음_계약된오류를반환한다() throws Exception {
        when(getPlatformAdminRegionsUseCase.get(AUTHENTICATED_USER_ID, null))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getRegions_인증정보없음_미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/regions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void createRegion_유효한요청_생성응답을반환한다() throws Exception {
        when(createRegionUseCase.create(eq(AUTHENTICATED_USER_ID), any(), any())).thenReturn(result());

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/regions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("지역 생성에 성공했습니다."))
            .andExpect(jsonPath("$.data.regionId").value("11"))
            .andExpect(jsonPath("$.data.regionCode").value("JEONJU"))
            .andExpect(jsonPath("$.data.name").value("전주시"))
            .andExpect(jsonPath("$.data.isPublic").value(false))
            .andExpect(jsonPath("$.data.createdAt").value("2026-08-09T00:00:00Z"))
            .andExpect(jsonPath("$.data.updatedAt").value("2026-08-09T00:00:00Z"));

        verify(createRegionUseCase).create(
            eq(AUTHENTICATED_USER_ID),
            eq(new CreateRegionCommand(
                "JEONJU",
                "전주시",
                "PILOT_REGION_ADDITION",
                "OPS-2026-0805-REGION-03"
            )),
            any()
        );
    }

    @Test
    void createRegion_잘못된입력_유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/platform-admin/regions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "regionCode": "jeon ju",
                      "name": "전주시",
                      "reasonCode": "UNSUPPORTED_REASON",
                      "evidenceReference": " "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createRegionUseCase);
    }

    @Test
    void createRegion_중복코드_계약된충돌오류를반환한다() throws Exception {
        when(createRegionUseCase.create(eq(AUTHENTICATED_USER_ID), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.REGION_CODE_ALREADY_EXISTS));

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/regions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REGION_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createRegion_인증정보없음_미인증오류를반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/platform-admin/regions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void updateRegionStatus_유효한요청_상태변경응답을반환한다() throws Exception {
        when(updateRegionStatusUseCase.update(eq(AUTHENTICATED_USER_ID), eq(11L), any(), any()))
            .thenReturn(updateResult());

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/regions/11/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validStatusRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("지역 공개 여부 요청을 처리했습니다."))
            .andExpect(jsonPath("$.data.regionId").value("11"))
            .andExpect(jsonPath("$.data.regionCode").value("JEONJU"))
            .andExpect(jsonPath("$.data.name").value("전주시"))
            .andExpect(jsonPath("$.data.isPublic").value(true))
            .andExpect(jsonPath("$.data.updatedAt").value("2026-08-09T01:00:00Z"));

        verify(updateRegionStatusUseCase).update(
            eq(AUTHENTICATED_USER_ID),
            eq(11L),
            eq(new UpdateRegionStatusCommand(
                true,
                "REGION_LAUNCH",
                "OPS-2026-0805-REGION-03"
            )),
            any()
        );
    }

    @Test
    void updateRegionStatus_잘못된요청_유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/regions/11/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "isPublic": true,
                      "reasonCode": "REGION_LAUNCH",
                      "evidenceReference": " "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(updateRegionStatusUseCase);
    }

    @Test
    void updateRegionStatus_잘못된지역식별자_계약된타입오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/regions/invalid/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validStatusRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(updateRegionStatusUseCase);
    }

    @Test
    void updateRegionStatus_콘텐츠존재충돌_계약된충돌오류를반환한다() throws Exception {
        when(updateRegionStatusUseCase.update(eq(AUTHENTICATED_USER_ID), eq(11L), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.REGION_AVAILABILITY_CONFLICT));

        mockMvc.perform(authenticated(patch("/api/v1/platform-admin/regions/11/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(privateStatusRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REGION_AVAILABILITY_CONFLICT"));
    }

    @Test
    void updateRegionStatus_인증정보없음_미인증오류를반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/platform-admin/regions/11/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validStatusRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private CreateRegionResult result() {
        return new CreateRegionResult(
            11L,
            "JEONJU",
            "전주시",
            false,
            CREATED_AT,
            CREATED_AT
        );
    }

    private String validRequest() {
        return """
            {
              "regionCode": "  jeonju  ",
              "name": "  전주시  ",
              "reasonCode": "PILOT_REGION_ADDITION",
              "evidenceReference": "OPS-2026-0805-REGION-03"
            }
        """;
    }

    private UpdateRegionStatusResult updateResult() {
        return new UpdateRegionStatusResult(
            11L,
            "JEONJU",
            "전주시",
            true,
            UPDATED_AT
        );
    }

    private String validStatusRequest() {
        return """
            {
              "isPublic": true,
              "reasonCode": "  REGION_LAUNCH  ",
              "evidenceReference": "  OPS-2026-0805-REGION-03  "
            }
            """;
    }

    private String privateStatusRequest() {
        return """
            {
              "isPublic": false,
              "reasonCode": "REGION_PREPARATION",
              "evidenceReference": "OPS-2026-0805-REGION-03"
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + jwtAccessTokenService.issue(AUTHENTICATED_USER_ID)
        );
    }
}
