package io.regionevent.regioneventbackend.domain.visit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.visit.dto.CheckInResponse;
import io.regionevent.regioneventbackend.domain.visit.service.CheckInResult;
import io.regionevent.regioneventbackend.domain.visit.service.CheckInUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(CheckInController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class CheckInControllerWebMvcTest {

    private static final long OPERATOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CheckInUseCase checkInUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void checkInByQr_유효한요청_버전경로와비버전경로에서성공응답을반환한다() throws Exception {
        when(checkInUseCase.checkInByQr(eq(OPERATOR_ID), any(), eq("key"), any())).thenReturn(successResult("QR"));

        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"qr-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("QR 체크인에 성공했습니다."))
            .andExpect(jsonPath("$.data.checkInMethod").value("QR"));

        mockMvc.perform(authenticated(post("/operator/check-ins").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"qr-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(checkInUseCase, org.mockito.Mockito.times(2)).checkInByQr(eq(OPERATOR_ID), any(), eq("key"), any());
    }

    @Test
    void checkInByQr_토큰또는멱등키가유효하지않음_계약된오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        when(checkInUseCase.checkInByQr(eq(OPERATOR_ID), any(), eq(null), any()))
            .thenReturn(CheckInResult.failure(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"qr-token\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void checkInManually_유효한요청_버전경로와비버전경로에서성공응답을반환한다() throws Exception {
        when(checkInUseCase.checkInManually(eq(OPERATOR_ID), any(), eq("key"), any())).thenReturn(successResult("MANUAL"));

        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins/manual").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationNo\":\"R-2026\",\"reason\":\"예약번호 확인\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약번호 보조 체크인에 성공했습니다."))
            .andExpect(jsonPath("$.data.checkInMethod").value("MANUAL"));

        mockMvc.perform(authenticated(post("/operator/check-ins/manual").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationNo\":\"R-2026\",\"reason\":\"예약번호 확인\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(checkInUseCase, org.mockito.Mockito.times(2)).checkInManually(eq(OPERATOR_ID), any(), eq("key"), any());
    }

    @Test
    void checkInManually_본문형식오류또는권한없음_계약된오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins/manual").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationNo\":\"\",\"reason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins/manual").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationNo\":\"R-2026\",\"reason\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        when(checkInUseCase.checkInManually(eq(OPERATOR_ID), any(), eq("key"), any()))
            .thenReturn(CheckInResult.failure(ErrorCode.FORBIDDEN));
        mockMvc.perform(authenticated(post("/api/v1/operator/check-ins/manual").header("Idempotency-Key", "key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationNo\":\"R-2026\",\"reason\":\"예약번호 확인\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void checkIn_인증정보없음_미인증오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/check-ins")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"qr-token\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(checkInUseCase);
    }

    private CheckInResult successResult(String checkInMethod) {
        return CheckInResult.success(new CheckInResponse(
            "1",
            "2",
            "3",
            "CHECKED_IN",
            checkInMethod,
            Instant.parse("2026-08-05T00:00:00Z")
        ));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, OPERATOR_ID));
    }
}
