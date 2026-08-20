package io.regionevent.regioneventbackend.domain.payment.controller;

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

import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(PlatformAdminRefundController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class PlatformAdminRefundControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreateRefundUseCase createRefundUseCase;

    @Test
    void 유효한_요청은_환불_생성_결과를_반환한다() throws Exception {
        when(createRefundUseCase.create(eq(USER_ID), eq("10"), any(), any())).thenReturn(
            new CreateRefundResponse(
                "20",
                "10",
                17_000L,
                "KRW",
                "PROCESSING",
                Instant.parse("2026-08-11T00:00:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/payments/10/refund"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"CS-7781","reason":"방문자 취소 요청"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.refundId").value("20"))
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        verify(createRefundUseCase).create(eq(USER_ID), eq("10"), any(), any());
    }

    @Test
    void 공백_증빙은_환불_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/platform-admin/payments/10/refund"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"   ","reason":"방문자 취소 요청"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createRefundUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
