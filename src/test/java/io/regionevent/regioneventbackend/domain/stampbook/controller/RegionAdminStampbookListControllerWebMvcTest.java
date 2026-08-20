package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import io.regionevent.regioneventbackend.domain.stampbook.service.GetPendingRegionAdminStampbooksUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.PendingRegionAdminStampbookResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RegionAdminStampbookListController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RegionAdminStampbookListControllerWebMvcTest {

    private static final Long REGION_ADMIN_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetPendingRegionAdminStampbooksUseCase getPendingRegionAdminStampbooksUseCase;

    @Test
    void 심사대기상태로_조회하면_명세응답을_반환한다() throws Exception {
        when(getPendingRegionAdminStampbooksUseCase.find(REGION_ADMIN_USER_ID)).thenReturn(List.of(
            new PendingRegionAdminStampbookResult(
                101L,
                10L,
                StampbookStatus.PENDING_REVIEW,
                2,
                301L,
                Instant.parse("2026-08-14T02:20:00Z")
            )
        ));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/stampbooks")
                .param("status", "PENDING_REVIEW")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 심사 대기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbooks[0].stampbookId").value("101"))
            .andExpect(jsonPath("$.data.stampbooks[0].regionId").value("10"))
            .andExpect(jsonPath("$.data.stampbooks[0].status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.stampbooks[0].targetCount").value(2))
            .andExpect(jsonPath("$.data.stampbooks[0].rewardCouponPolicyId").value("301"))
            .andExpect(jsonPath("$.data.stampbooks[0].requestedAt").value("2026-08-14T02:20:00Z"));

        verify(getPendingRegionAdminStampbooksUseCase).find(REGION_ADMIN_USER_ID);
    }

    @Test
    void 심사대기가아닌상태는_입력오류를_반환하고_조회하지않는다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/stampbooks")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/stampbooks").param("status", " ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/stampbooks").param("status", "DRAFT")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getPendingRegionAdminStampbooksUseCase);
    }

    @Test
    void 인증정보가없으면_미인증오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/stampbooks").param("status", "PENDING_REVIEW"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
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
