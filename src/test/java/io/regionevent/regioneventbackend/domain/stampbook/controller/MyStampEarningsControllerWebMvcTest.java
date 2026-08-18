package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampEarningsUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampEarningsResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyStampEarningsController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyStampEarningsControllerWebMvcTest {

    private static final Long USER_ID = 100L;
    private static final Long STAMPBOOK_ID = 101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyStampEarningsUseCase getMyStampEarningsUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void 내_스탬프_적립_이력_조회에_성공하면_방문_근거를_반환한다() throws Exception {
        when(getMyStampEarningsUseCase.find(USER_ID, STAMPBOOK_ID)).thenReturn(result());

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101/earnings")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 스탬프 적립 이력 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.earnings[0].stampEarnId").value("501"))
            .andExpect(jsonPath("$.data.earnings[0].visitId").value("701"))
            .andExpect(jsonPath("$.data.earnings[0].content.contentId").value("201"))
            .andExpect(jsonPath("$.data.earnings[0].content.title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.earnings[0].visitedAt").value("2026-08-06T00:50:00Z"))
            .andExpect(jsonPath("$.data.earnings[0].earnedAt").value("2026-08-06T01:00:00Z"));

        verify(getMyStampEarningsUseCase).find(USER_ID, STAMPBOOK_ID);
    }

    @Test
    void 공개_스탬프북에_적립이_없으면_빈_배열을_반환한다() throws Exception {
        when(getMyStampEarningsUseCase.find(USER_ID, STAMPBOOK_ID)).thenReturn(
            new MyStampEarningsResult(STAMPBOOK_ID, List.of())
        );

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101/earnings")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.earnings").isArray())
            .andExpect(jsonPath("$.data.earnings").isEmpty());
    }

    @Test
    void 내_스탬프_적립_이력_조회는_대상이_없으면_NOT_FOUND를_반환한다() throws Exception {
        when(getMyStampEarningsUseCase.find(USER_ID, STAMPBOOK_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101/earnings")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 내_스탬프_적립_이력_조회는_접근할_수_없는_대상을_FORBIDDEN으로_반환한다() throws Exception {
        when(getMyStampEarningsUseCase.find(USER_ID, STAMPBOOK_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101/earnings")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 내_스탬프_적립_이력_조회는_미인증과_잘못된_식별자를_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/stampbooks/101/earnings"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/0/earnings")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/not-a-number/earnings")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(getMyStampEarningsUseCase, never()).find(eq(USER_ID), any());
    }

    private MyStampEarningsResult result() {
        return new MyStampEarningsResult(
            STAMPBOOK_ID,
            List.of(new MyStampEarningsResult.Earning(
                501L,
                701L,
                201L,
                "김해 가야문화 체험",
                Instant.parse("2026-08-06T00:50:00Z"),
                Instant.parse("2026-08-06T01:00:00Z")
            ))
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
