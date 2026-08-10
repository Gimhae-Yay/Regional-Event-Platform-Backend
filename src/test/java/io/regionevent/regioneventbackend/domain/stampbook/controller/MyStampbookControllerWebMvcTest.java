package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.hamcrest.Matchers.nullValue;
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

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampbooksUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookListResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookProgressStatus;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyStampbookControllerWebMvcTest {

    private static final Long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyStampbooksUseCase getMyStampbooksUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getMyStampbooks_목록을_정상_응답으로_반환한다() throws Exception {
        when(getMyStampbooksUseCase.findAll(USER_ID)).thenReturn(List.of(result()));

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 스탬프북 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbooks[0].stampbookId").value("101"))
            .andExpect(jsonPath("$.data.stampbooks[0].regionId").value("10"))
            .andExpect(jsonPath("$.data.stampbooks[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.stampbooks[0].progress.status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.data.stampbooks[0].progress.earnedCount").value(0))
            .andExpect(jsonPath("$.data.stampbooks[0].progress.targetCount").value(3))
            .andExpect(jsonPath("$.data.stampbooks[0].progress.completedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.stampbooks[0].progress.lastEarnedAt").value(nullValue()));

        verify(getMyStampbooksUseCase).findAll(USER_ID);
    }

    @Test
    void getMyStampbooks_스탬프북이_없으면_빈목록을_반환한다() throws Exception {
        when(getMyStampbooksUseCase.findAll(USER_ID)).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stampbooks").isArray())
            .andExpect(jsonPath("$.data.stampbooks").isEmpty());
    }

    @Test
    void getMyStampbooks_인증정보가_없으면_미인증오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/stampbooks"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private MyStampbookListResult result() {
        return new MyStampbookListResult(
            101L,
            10L,
            StampbookStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z"),
            new MyStampbookListResult.Progress(
                MyStampbookProgressStatus.NOT_STARTED,
                0L,
                3L,
                null,
                null
            )
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(USER_ID));
    }
}
