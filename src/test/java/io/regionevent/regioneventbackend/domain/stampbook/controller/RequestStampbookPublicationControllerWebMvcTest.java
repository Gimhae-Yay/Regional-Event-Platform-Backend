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
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RequestStampbookPublicationController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RequestStampbookPublicationControllerWebMvcTest {

    private static final long OPERATOR_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RequestStampbookPublicationUseCase requestStampbookPublicationUseCase;

    @Test
    void requestPublication_유효한요청_심사요청응답을반환한다() throws Exception {
        when(requestStampbookPublicationUseCase.request(eq(OPERATOR_USER_ID), any(), any())).thenReturn(
            new RequestStampbookPublicationResult(
                101L,
                StampbookStatus.PENDING_REVIEW,
                Instant.parse("2026-08-09T06:00:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks/101/publish"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 공개 심사 요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-09T06:00:00Z"));

        verify(requestStampbookPublicationUseCase).request(eq(OPERATOR_USER_ID), any(), any());
    }

    @Test
    void requestPublication_공백사유면_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks/101/publish"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(requestStampbookPublicationUseCase);
    }

    @Test
    void requestPublication_범위를벗어난식별자면_형식오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks/9223372036854775808/publish"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(requestStampbookPublicationUseCase);
    }

    @Test
    void requestPublication_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/stampbooks/101/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return """
            {
              "reason": "지역 관리자 공개 심사를 요청합니다."
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, OPERATOR_USER_ID));
    }
}
