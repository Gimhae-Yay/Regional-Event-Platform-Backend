package io.regionevent.regioneventbackend.domain.visit.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import io.regionevent.regioneventbackend.domain.visit.dto.GetQrExceptionsResponse;
import io.regionevent.regioneventbackend.domain.visit.dto.GetQrExceptionsResponse.QrExceptionResponse;
import io.regionevent.regioneventbackend.domain.visit.service.GetQrExceptionsUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;

class QrExceptionControllerTest {

    private static final Long USER_ID = 1L;

    private final GetQrExceptionsUseCase getQrExceptionsUseCase = mock(GetQrExceptionsUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new QrExceptionController(getQrExceptionsUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
            .build();
    }

    @Test
    void getQrExceptions_whenRequestIsValid_returnsSuccessResponse() throws Exception {
        when(getQrExceptionsUseCase.get(USER_ID, "cursor-token", 2))
            .thenReturn(new GetQrExceptionsResponse(
                List.of(new QrExceptionResponse(
                    "10",
                    "QR_CHECK_IN_FAILURE",
                    "FAILURE",
                    "QR_CHECK_IN_SIGNATURE_INVALID",
                    false,
                    null,
                    null,
                    null,
                    null
                )),
                "next-cursor",
                true
            ));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("cursor", "cursor-token")
                .queryParam("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.exceptions[0].exceptionId").value("10"))
            .andExpect(jsonPath("$.data.exceptions[0].exceptionType").value("QR_CHECK_IN_FAILURE"))
            .andExpect(jsonPath("$.data.exceptions[0].reasonCode").value("QR_CHECK_IN_SIGNATURE_INVALID"))
            .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
            .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(getQrExceptionsUseCase).get(USER_ID, "cursor-token", 2);
    }

    @Test
    void getQrExceptions_whenUnversionedPathIsUsed_returnsSuccessResponse() throws Exception {
        when(getQrExceptionsUseCase.get(USER_ID, null, 20))
            .thenReturn(new GetQrExceptionsResponse(List.of(), null, false));

        mockMvc.perform(get("/region-admin/qr-exceptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptions.length()").value(0))
            .andExpect(jsonPath("$.data.nextCursor").isEmpty())
            .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(getQrExceptionsUseCase).get(USER_ID, null, 20);
    }

    @Test
    void getQrExceptions_whenSizeIsOutOfRange_returnsInvalidInputResponse() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getQrExceptions_whenSizeTypeIsInvalid_returnsInvalidTypeResponse() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("size", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void getQrExceptions_whenCursorIsInvalid_returnsInvalidInputResponse() throws Exception {
        when(getQrExceptionsUseCase.get(eq(USER_ID), eq(" "), eq(20)))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("cursor", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private static class TestAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
        ) {
            return USER_ID;
        }
    }
}
