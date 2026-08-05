package io.regionevent.regioneventbackend.domain.region.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(PublicRegionController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
@ExtendWith(OutputCaptureExtension.class)
class PublicRegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionService regionService;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void 인증_없이_공개_지역_목록_응답을_반환하고_성공_로그를_남긴다(CapturedOutput output) throws Exception {
        Region gimhae = new Region("GIMHAE", "김해시", true);
        Region donghae = new Region("DONGHAE", "동해시", true);
        ReflectionTestUtils.setField(gimhae, "regionId", 1L);
        ReflectionTestUtils.setField(donghae, "regionId", 2L);
        when(regionService.findPublicRegions()).thenReturn(List.of(gimhae, donghae));

        mockMvc.perform(get("/api/v1/regions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 지역 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.regions[0].regionId").value("1"))
            .andExpect(jsonPath("$.data.regions[0].regionCode").value("GIMHAE"))
            .andExpect(jsonPath("$.data.regions[0].name").value("김해시"))
            .andExpect(jsonPath("$.data.regions[1].regionId").value("2"))
            .andExpect(jsonPath("$.data.regions[1].regionCode").value("DONGHAE"))
            .andExpect(jsonPath("$.data.regions[1].name").value("동해시"));

        assertThat(output.getOut()).contains(
            "Public region list read. requestId=",
            "resultCount=2, resultCode=SUCCESS"
        );
    }

    @Test
    void 공개_지역이_없으면_빈_배열을_반환한다() throws Exception {
        when(regionService.findPublicRegions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/regions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.regions").isArray())
            .andExpect(jsonPath("$.data.regions").isEmpty());
    }

    @Test
    void 서버_오류_응답과_실패_로그에_비공개_지역_정보를_남기지_않는다(CapturedOutput output) throws Exception {
        String privateRegionName = "비공개-지역-이름";
        when(regionService.findPublicRegions()).thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(get("/api/v1/regions"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.statusCode").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data").isEmpty());

        assertThat(output.getOut()).contains(
            "Public region list read. requestId=",
            "resultCount=0, resultCode=INTERNAL_SERVER_ERROR"
        ).doesNotContain(privateRegionName);
    }
}
