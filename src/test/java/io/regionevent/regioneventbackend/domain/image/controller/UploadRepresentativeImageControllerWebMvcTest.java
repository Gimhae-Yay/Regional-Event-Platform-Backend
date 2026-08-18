package io.regionevent.regioneventbackend.domain.image.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.image.dto.UploadRepresentativeImageResponse;
import io.regionevent.regioneventbackend.domain.image.service.UploadRepresentativeImageUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(UploadRepresentativeImageController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class UploadRepresentativeImageControllerWebMvcTest {

    private static final long OPERATOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private UploadRepresentativeImageUseCase uploadRepresentativeImageUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void createPresignedUrl_유효한요청_업로드정보를응답한다() throws Exception {
        when(uploadRepresentativeImageUseCase.createUpload(eq(OPERATOR_ID), any())).thenReturn(
            new UploadRepresentativeImageResponse(
                "1",
                "https://upload.example.com/image",
                Instant.parse("2026-08-05T00:10:00Z"),
                Map.of("Content-Type", "image/png")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/operator/uploads/presigned-url"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("대표 이미지 업로드 URL 발급에 성공했습니다."))
            .andExpect(jsonPath("$.data.imageObjectId").value("1"))
            .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example.com/image"))
            .andExpect(jsonPath("$.data.uploadHeaders.Content-Type").value("image/png"));

        verify(uploadRepresentativeImageUseCase).createUpload(eq(OPERATOR_ID), any());
    }

    @Test
    void createPresignedUrl_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(uploadRepresentativeImageUseCase);
    }

    @Test
    void createPresignedUrl_운영자권한없음_권한오류를응답한다() throws Exception {
        when(uploadRepresentativeImageUseCase.createUpload(eq(OPERATOR_ID), any()))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(post("/api/v1/operator/uploads/presigned-url"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createPresignedUrl_요청검증실패_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/uploads/presigned-url"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mediaType": "",
                      "byteSize": 0,
                      "checksum": "",
                      "usage": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(uploadRepresentativeImageUseCase);
    }

    @Test
    void createPresignedUrl_타입오류또는저장소실패_계약된오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/uploads/presigned-url"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mediaType": "image/png",
                      "byteSize": "large",
                      "checksum": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                      "usage": "CONTENT_REPRESENTATIVE"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        when(uploadRepresentativeImageUseCase.createUpload(eq(OPERATOR_ID), any()))
            .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        mockMvc.perform(authenticated(post("/api/v1/operator/uploads/presigned-url"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, OPERATOR_ID));
    }

    private String validRequest() {
        return """
            {
              "mediaType": "image/png",
              "byteSize": 1024,
              "checksum": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
              "usage": "CONTENT_REPRESENTATIVE"
            }
            """;
    }
}
