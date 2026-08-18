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

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampbookDetailUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetMyStampbooksUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookDetailResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookCompletionReward;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyStampbookDetailControllerIntegrationTest {

    private static final Long USER_ID = 100L;
    private static final Long STAMPBOOK_ID = 101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyStampbooksUseCase getMyStampbooksUseCase;

    @MockitoBean
    private GetMyStampbookDetailUseCase getMyStampbookDetailUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void 내_스탬프북_상세_조회에_성공하면_콘텐츠별_적립과_진행도를_반환한다() throws Exception {
        when(getMyStampbookDetailUseCase.find(USER_ID, STAMPBOOK_ID)).thenReturn(inProgressResult());

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 스탬프북 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbook.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.stampbook.title").value("김해 가야 문화 완주"))
            .andExpect(jsonPath("$.data.stampbook.regionId").value("10"))
            .andExpect(jsonPath("$.data.stampbook.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.stampbook.endedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.stampbook.targetContents[0].contentId").value("201"))
            .andExpect(jsonPath("$.data.stampbook.targetContents[0].title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.stampbook.targetContents[0].earned").value(true))
            .andExpect(jsonPath("$.data.stampbook.targetContents[1].contentId").value("202"))
            .andExpect(jsonPath("$.data.stampbook.targetContents[1].earned").value(false))
            .andExpect(jsonPath("$.data.stampbook.targetContents[1].earnedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.progress.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.progress.earnedCount").value(1))
            .andExpect(jsonPath("$.data.progress.targetCount").value(2))
            .andExpect(jsonPath("$.data.progress.completedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.progress.completionReward").value(nullValue()));

        verify(getMyStampbookDetailUseCase).find(USER_ID, STAMPBOOK_ID);
    }

    @Test
    void 공개_스탬프북에_진행_행이_없으면_NOT_STARTED를_반환한다() throws Exception {
        when(getMyStampbookDetailUseCase.find(USER_ID, STAMPBOOK_ID)).thenReturn(notStartedResult());

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progress.status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.data.progress.earnedCount").value(0))
            .andExpect(jsonPath("$.data.progress.completedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.progress.completionReward").value(nullValue()));
    }

    @Test
    void 내_스탬프북_상세_조회는_대상이_없으면_NOT_FOUND를_반환한다() throws Exception {
        when(getMyStampbookDetailUseCase.find(USER_ID, STAMPBOOK_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 내_스탬프북_상세_조회는_접근할_수_없는_대상을_FORBIDDEN으로_반환한다() throws Exception {
        when(getMyStampbookDetailUseCase.find(USER_ID, STAMPBOOK_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 내_스탬프북_상세_조회는_미인증과_잘못된_식별자를_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/stampbooks/101"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(getMyStampbookDetailUseCase, never()).find(eq(USER_ID), any());
    }

    @Test
    void 내_스탬프북_상세_조회는_완료_보상_쿠폰_발급_식별자를_반환한다() throws Exception {
        when(getMyStampbookDetailUseCase.find(USER_ID, STAMPBOOK_ID)).thenReturn(completedResult());

        mockMvc.perform(authenticated(get("/api/v1/me/stampbooks/101")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progress.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.progress.completionReward.couponPolicyId").value("301"))
            .andExpect(jsonPath("$.data.progress.completionReward.stampbookRewardGrantId").value("901"));
    }

    private MyStampbookDetailResult inProgressResult() {
        return new MyStampbookDetailResult(
            STAMPBOOK_ID,
            "김해 가야 문화 완주",
            10L,
            StampbookStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z"),
            null,
            List.of(
                new MyStampbookDetailResult.TargetContent(
                    201L,
                    "김해 가야문화 체험",
                    true,
                    Instant.parse("2026-08-06T01:00:00Z")
                ),
                new MyStampbookDetailResult.TargetContent(
                    202L,
                    "대성동고분박물관 해설",
                    false,
                    null
                )
            ),
            new MyStampbookDetailResult.Progress(
                MyStampbookProgressStatus.IN_PROGRESS,
                1L,
                2L,
                null,
                null
            )
        );
    }

    private MyStampbookDetailResult notStartedResult() {
        return new MyStampbookDetailResult(
            STAMPBOOK_ID,
            "김해 가야 문화 완주",
            10L,
            StampbookStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z"),
            null,
            List.of(new MyStampbookDetailResult.TargetContent(
                201L,
                "김해 가야문화 체험",
                false,
                null
            )),
            new MyStampbookDetailResult.Progress(
                MyStampbookProgressStatus.NOT_STARTED,
                0L,
                1L,
                null,
                null
            )
        );
    }

    private MyStampbookDetailResult completedResult() {
        return new MyStampbookDetailResult(
            STAMPBOOK_ID,
            "김해 역사 산책",
            10L,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-04T00:00:00Z"),
            List.of(new MyStampbookDetailResult.TargetContent(
                201L,
                "김해 가야문화 체험",
                true,
                Instant.parse("2026-08-04T00:00:00Z")
            )),
            new MyStampbookDetailResult.Progress(
                MyStampbookProgressStatus.COMPLETED,
                1L,
                1L,
                Instant.parse("2026-08-04T00:00:00Z"),
                new StampbookCompletionReward(301L, 901L)
            )
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
