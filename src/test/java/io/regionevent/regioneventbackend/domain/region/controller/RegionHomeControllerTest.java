package io.regionevent.regioneventbackend.domain.region.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.service.GetRegionHomeUseCase;
import io.regionevent.regioneventbackend.domain.region.service.RegionHomeResult;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionStaticInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(RegionHomeController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
@ExtendWith(OutputCaptureExtension.class)
class RegionHomeControllerTest {

    private static final long REGION_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetRegionHomeUseCase getRegionHomeUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void 인증_없이_지역_홈_응답을_반환하고_결과를_로그로_남긴다(CapturedOutput output) throws Exception {
        when(getRegionHomeUseCase.get(REGION_ID)).thenReturn(regionHomeResult());

        mockMvc.perform(get("/api/v1/regions/{regionId}/home", REGION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("지역 홈 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.region.regionId").value("10"))
            .andExpect(jsonPath("$.data.region.regionCode").value("GIMHAE"))
            .andExpect(jsonPath("$.data.ongoingContents[0].contentId").value("200"))
            .andExpect(jsonPath("$.data.ongoingContents[0].displaySession.sessionId").value("1001"))
            .andExpect(jsonPath("$.data.ongoingContents[0].displaySession.startsAt")
                .value("2026-08-05T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.ongoingContents[0].displaySession.endsAt")
                .value("2026-08-05T11:00:00+09:00"))
            .andExpect(jsonPath("$.data.upcomingContents").isEmpty());

        verify(getRegionHomeUseCase).get(REGION_ID);
        assertThat(output.getOut()).contains(
            "Region home read. requestId=",
            "regionId=10, ongoingCount=1, upcomingCount=0, resultCode=SUCCESS"
        );
    }

    @Test
    void 지역_식별자_형식과_양수_규칙을_구분한다() throws Exception {
        mockMvc.perform(get("/api/v1/regions/0/home"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/regions/not-a-number/home"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 비공개_지역은_찾을수없음으로_응답한다() throws Exception {
        when(getRegionHomeUseCase.get(REGION_ID)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/regions/{regionId}/home", REGION_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static RegionHomeResult regionHomeResult() {
        return new RegionHomeResult(
            new PublicRegionStaticInfo(REGION_ID, "GIMHAE", "김해시"),
            List.of(new RegionHomeResult.Content(
                200L,
                ContentType.EVENT_EXPERIENCE,
                "김해 문화 체험",
                "김해문화의전당",
                "https://example.com/image",
                Instant.parse("2026-08-05T01:00:00Z"),
                true,
                new RegionHomeResult.DisplaySession(
                    1001L,
                    Instant.parse("2026-08-05T00:00:00Z"),
                    Instant.parse("2026-08-05T02:00:00Z"),
                    4
                )
            )),
            List.of()
        );
    }
}
