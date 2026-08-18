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
import io.regionevent.regioneventbackend.domain.stampbook.service.EndStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.EndStampbookUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(EndStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class EndStampbookControllerWebMvcTest {

    private static final long OPERATOR_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private EndStampbookUseCase endStampbookUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void end_유효한요청이면종료응답을반환한다() throws Exception {
        when(endStampbookUseCase.end(eq(OPERATOR_USER_ID), any(), any())).thenReturn(
            new EndStampbookResult(
                101L,
                StampbookStatus.ENDED,
                Instant.parse("2026-08-09T06:00:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks/101/end"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 종료에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.status").value("ENDED"))
            .andExpect(jsonPath("$.data.endedAt").value("2026-08-09T06:00:00Z"));

        verify(endStampbookUseCase).end(eq(OPERATOR_USER_ID), any(), any());
    }

    @Test
    void end_공백사유면입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks/101/end"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(endStampbookUseCase);
    }

    @Test
    void end_범위를벗어난식별자면형식오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks/9223372036854775808/end"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(endStampbookUseCase);
    }

    @Test
    void end_인증정보없음이면미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/stampbooks/101/end")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return """
            {
              "reason": "행사 운영을 종료합니다."
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, OPERATOR_USER_ID));
    }
}
