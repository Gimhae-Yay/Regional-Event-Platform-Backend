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
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(RejectRegionAdminStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RejectRegionAdminStampbookControllerWebMvcTest {

    private static final long REGION_ADMIN_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RejectRegionAdminStampbookUseCase rejectRegionAdminStampbookUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void reject_유효한요청_반려응답을반환한다() throws Exception {
        when(rejectRegionAdminStampbookUseCase.reject(eq(REGION_ADMIN_USER_ID), any(), any())).thenReturn(
            new RejectRegionAdminStampbookResult(
                101L,
                StampbookStatus.DRAFT,
                Instant.parse("2026-08-14T03:05:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/101/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.rejectedAt").value("2026-08-14T03:05:00Z"));

        verify(rejectRegionAdminStampbookUseCase).reject(eq(REGION_ADMIN_USER_ID), any(), any());
    }

    @Test
    void reject_반려사유가공백이면_입력오류를반환하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/101/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(rejectRegionAdminStampbookUseCase);
    }

    @Test
    void reject_식별자가양의정수가아니면_형식오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/0/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(rejectRegionAdminStampbookUseCase);
    }

    @Test
    void reject_식별자가Long범위를벗어나면_입력오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/9223372036854775808/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(rejectRegionAdminStampbookUseCase);
    }

    @Test
    void reject_미인증요청이면_미인증오류를반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/stampbooks/101/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(rejectRegionAdminStampbookUseCase);
    }

    @Test
    void reject_유스케이스업무오류를명세대로응답한다() throws Exception {
        assertBusinessError(ErrorCode.FORBIDDEN);
        assertBusinessError(ErrorCode.NOT_FOUND);
        assertBusinessError(ErrorCode.STAMPBOOK_STATE_CONFLICT);
    }

    private void assertBusinessError(ErrorCode errorCode) throws Exception {
        when(rejectRegionAdminStampbookUseCase.reject(eq(REGION_ADMIN_USER_ID), any(), any()))
            .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/stampbooks/101/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().is(errorCode.httpStatus().value()))
            .andExpect(jsonPath("$.code").value(errorCode.code()));
    }

    private String validRequest() {
        return """
            {
              "reason": "완료 보상 쿠폰 정책을 공개 상태로 전환한 뒤 다시 요청해 주세요."
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + jwtAccessTokenService.issue(REGION_ADMIN_USER_ID)
        );
    }
}
