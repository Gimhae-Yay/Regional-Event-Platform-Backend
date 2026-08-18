package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookUseCase.CreateStampbookCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(CreateStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class CreateStampbookControllerWebMvcTest {

    private static final long OPERATOR_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreateStampbookUseCase createStampbookUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void createStampbook_유효한요청_생성응답을반환한다() throws Exception {
        when(createStampbookUseCase.create(eq(OPERATOR_USER_ID), any(), any())).thenReturn(
            new CreateStampbookResult(
                101L,
                StampbookStatus.DRAFT,
                2,
                Instant.parse("2026-08-09T05:30:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 생성에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.targetCount").value(2))
            .andExpect(jsonPath("$.data.createdAt").value("2026-08-09T05:30:00Z"));

        ArgumentCaptor<CreateStampbookCommand> commandCaptor = ArgumentCaptor.forClass(
            CreateStampbookCommand.class
        );
        verify(createStampbookUseCase).create(eq(OPERATOR_USER_ID), commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue().title()).isEqualTo("김해 가야 문화 완주");
    }

    @Test
    void createStampbook_중복콘텐츠요청_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "김해 가야 문화 완주",
                      "regionId": "1",
                      "contentIds": ["201", "201"],
                      "rewardCouponPolicyId": "301",
                      "reason": "스탬프북을 생성합니다."
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createStampbookUseCase);
    }

    @Test
    void createStampbook_64비트범위를벗어난식별자_입력오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "김해 가야 문화 완주",
                      "regionId": "9223372036854775808",
                      "contentIds": ["201"],
                      "rewardCouponPolicyId": "301",
                      "reason": "스탬프북을 생성합니다."
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createStampbookUseCase);
    }

    @Test
    void createStampbook_제목이누락되거나공백이거나101자면_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "regionId": "1",
                      "contentIds": ["201"],
                      "rewardCouponPolicyId": "301",
                      "reason": "스탬프북을 생성합니다."
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        for (String title : List.of("   ", "가".repeat(101))) {
            mockMvc.perform(authenticated(post("/api/v1/operator/stampbooks"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "%s",
                          "regionId": "1",
                          "contentIds": ["201"],
                          "rewardCouponPolicyId": "301",
                          "reason": "스탬프북을 생성합니다."
                        }
                        """.formatted(title)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        verifyNoInteractions(createStampbookUseCase);
    }

    @Test
    void createStampbook_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/stampbooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return """
            {
              "title": "  김해 가야 문화 완주  ",
              "regionId": "1",
              "contentIds": ["201", "202"],
              "rewardCouponPolicyId": "301",
              "reason": "스탬프북을 생성합니다."
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(OPERATOR_USER_ID));
    }
}
