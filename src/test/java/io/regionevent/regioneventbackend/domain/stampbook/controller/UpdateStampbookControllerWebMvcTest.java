package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookUseCase;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookUseCase.UpdateStampbookCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(UpdateStampbookController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class UpdateStampbookControllerWebMvcTest {

    private static final long OPERATOR_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private UpdateStampbookUseCase updateStampbookUseCase;

    @Test
    void updateStampbook_유효한요청_수정응답을반환한다() throws Exception {
        when(updateStampbookUseCase.update(eq(OPERATOR_USER_ID), any(), any())).thenReturn(
            new UpdateStampbookResult(
                101L,
                StampbookStatus.DRAFT,
                2,
                Instant.parse("2026-08-09T05:30:00Z")
            )
        );

        mockMvc.perform(authenticated(patch("/api/v1/operator/stampbooks/101"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("스탬프북 수정에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("101"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.targetCount").value(2))
            .andExpect(jsonPath("$.data.updatedAt").value("2026-08-09T05:30:00Z"));

        ArgumentCaptor<UpdateStampbookCommand> commandCaptor = ArgumentCaptor.forClass(
            UpdateStampbookCommand.class
        );
        verify(updateStampbookUseCase).update(eq(OPERATOR_USER_ID), commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue().title()).isEqualTo("김해 가야 문화 완주");
    }

    @Test
    void updateStampbook_수정필드가없으면_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/operator/stampbooks/101"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "스탬프북을 수정합니다."
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(updateStampbookUseCase);
    }

    @Test
    void updateStampbook_제목만제공하면_수정응답을반환한다() throws Exception {
        when(updateStampbookUseCase.update(eq(OPERATOR_USER_ID), any(), any())).thenReturn(
            new UpdateStampbookResult(
                101L,
                StampbookStatus.DRAFT,
                2,
                Instant.parse("2026-08-09T05:30:00Z")
            )
        );

        mockMvc.perform(authenticated(patch("/api/v1/operator/stampbooks/101"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "  김해 가야 문화 완주 코스  ",
                      "reason": "제목을 수정합니다."
                    }
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<UpdateStampbookCommand> commandCaptor = ArgumentCaptor.forClass(
            UpdateStampbookCommand.class
        );
        verify(updateStampbookUseCase).update(eq(OPERATOR_USER_ID), commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue().title()).isEqualTo("김해 가야 문화 완주 코스");
        assertThat(commandCaptor.getValue().contentIds()).isNull();
        assertThat(commandCaptor.getValue().rewardCouponPolicyId()).isNull();
    }

    @Test
    void updateStampbook_공백이거나101자제목이면_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        for (String title : List.of("   ", "가".repeat(101))) {
            mockMvc.perform(authenticated(patch("/api/v1/operator/stampbooks/101"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "%s",
                          "reason": "제목을 수정합니다."
                        }
                        """.formatted(title)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        verifyNoInteractions(updateStampbookUseCase);
    }

    @Test
    void updateStampbook_범위를벗어난식별자면_형식오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/operator/stampbooks/9223372036854775808"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(updateStampbookUseCase);
    }

    @Test
    void updateStampbook_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(patch("/api/v1/operator/stampbooks/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return """
            {
              "title": "  김해 가야 문화 완주  ",
              "contentIds": ["201", "202"],
              "rewardCouponPolicyId": "301",
              "reason": "스탬프북을 수정합니다."
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, OPERATOR_USER_ID));
    }
}
