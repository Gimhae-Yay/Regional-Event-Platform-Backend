package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetOperatorStampbooksUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.OperatorStampbookListResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(OperatorStampbookListController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
class OperatorStampbookListControllerIntegrationTest {

    private static final Long AUTHENTICATED_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetOperatorStampbooksUseCase getOperatorStampbooksUseCase;

    @Test
    void 운영자_스탬프북_목록_조회는_명세_응답을_반환한다() throws Exception {
        when(getOperatorStampbooksUseCase.findAll(AUTHENTICATED_USER_ID)).thenReturn(List.of(
            new OperatorStampbookListResult(
                401L,
                "종료 스탬프북",
                10L,
                StampbookStatus.ENDED,
                2,
                301L,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-10T00:00:00Z")
            ),
            new OperatorStampbookListResult(
                400L,
                "초안 스탬프북",
                10L,
                StampbookStatus.DRAFT,
                1,
                300L,
                null,
                null
            )
        ));

        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 스탬프북 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbooks[0].stampbookId").value("401"))
            .andExpect(jsonPath("$.data.stampbooks[0].title").value("종료 스탬프북"))
            .andExpect(jsonPath("$.data.stampbooks[0].regionId").value("10"))
            .andExpect(jsonPath("$.data.stampbooks[0].status").value("ENDED"))
            .andExpect(jsonPath("$.data.stampbooks[0].targetCount").value(2))
            .andExpect(jsonPath("$.data.stampbooks[0].rewardCouponPolicyId").value("301"))
            .andExpect(jsonPath("$.data.stampbooks[0].publishedAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.stampbooks[0].endedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.data.stampbooks[1].publishedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.stampbooks[1].endedAt").value(nullValue()));

        verify(getOperatorStampbooksUseCase).findAll(AUTHENTICATED_USER_ID);
    }

    @Test
    void 운영자_스탬프북_목록_조회는_대상이_없으면_빈배열을_반환한다() throws Exception {
        when(getOperatorStampbooksUseCase.findAll(AUTHENTICATED_USER_ID)).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stampbooks").isArray())
            .andExpect(jsonPath("$.data.stampbooks").isEmpty());
    }

    @Test
    void 운영자_스탬프북_목록_조회는_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/operator/stampbooks"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getOperatorStampbooksUseCase, never()).findAll(any());
    }

    @Test
    void 운영자_권한이나_담당지역이_없으면_권한오류를_반환한다() throws Exception {
        when(getOperatorStampbooksUseCase.findAll(AUTHENTICATED_USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );

        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(
                jwtAccessTokenService,
                AUTHENTICATED_USER_ID
            )
        );
    }
}
