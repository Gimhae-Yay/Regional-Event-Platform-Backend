package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.payment.dto.RetryRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.service.RetryRefundUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RetryRefundController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RetryRefundControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RetryRefundUseCase retryRefundUseCase;

    @Test
    void 유효한_요청은_환불_재시도_결과를_반환한다() throws Exception {
        org.mockito.Mockito.when(retryRefundUseCase.retry(eq(USER_ID), eq("20"), any())).thenReturn(
            new RetryRefundResponse(
                "20",
                2,
                "SUCCEEDED",
                Instant.parse("2026-08-12T00:00:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/refunds/20/retry")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("환불 재시도에 성공했습니다."))
            .andExpect(jsonPath("$.data.refundId").value("20"))
            .andExpect(jsonPath("$.data.attemptNo").value(2))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        verify(retryRefundUseCase).retry(eq(USER_ID), eq("20"), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
