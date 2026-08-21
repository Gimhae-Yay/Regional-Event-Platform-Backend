package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.ApproveRegionAdminStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.ApproveRegionAdminStampbookUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(ApproveRegionAdminStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class ApproveRegionAdminStampbookControllerWebMvcTest {

    private static final long REGION_ADMIN_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private ApproveRegionAdminStampbookUseCase approveRegionAdminStampbookUseCase;

    @Test
    void approve_유효한요청이면승인응답을반환한다() throws Exception {
        when(approveRegionAdminStampbookUseCase.approve(eq(REGION_ADMIN_USER_ID), any(), any()))
            .thenReturn(new ApproveRegionAdminStampbookResult(
                101L,
                StampbookStatus.PUBLISHED,
                Instant.parse("2026-08-14T03:00:00Z")
            ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/101/approve"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-14T03:00:00Z"));

        verify(approveRegionAdminStampbookUseCase).approve(eq(REGION_ADMIN_USER_ID), any(), any());
    }

    @Test
    void approve_공백승인사유면입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/101/approve"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(approveRegionAdminStampbookUseCase);
    }

    @Test
    void approve_범위를벗어난식별자면입력오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/region-admin/stampbooks/9223372036854775808/approve"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(approveRegionAdminStampbookUseCase);
    }

    @Test
    void approve_인증정보가없으면미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/stampbooks/101/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return """
            {
              "reason": "대상 콘텐츠와 완료 보상 정책을 확인했습니다."
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, REGION_ADMIN_USER_ID)
        );
    }
}
