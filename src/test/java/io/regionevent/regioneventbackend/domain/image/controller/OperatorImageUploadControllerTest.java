package io.regionevent.regioneventbackend.domain.image.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.regionevent.regioneventbackend.domain.image.service.CreatePresignedImageUploadUseCase;
import io.regionevent.regioneventbackend.domain.image.service.PresignedImageUploadCommand;
import io.regionevent.regioneventbackend.domain.image.service.PresignedImageUploadResult;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.AuthenticatedUserResolver;

class OperatorImageUploadControllerTest {

    private MockMvc mockMvc;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private CreatePresignedImageUploadUseCase createPresignedImageUploadUseCase;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        createPresignedImageUploadUseCase = mock(CreatePresignedImageUploadUseCase.class);
        authentication = mock(Authentication.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new OperatorImageUploadController(
                authenticatedUserResolver,
                createPresignedImageUploadUseCase
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createPresignedUploadUrl_whenRequestIsValid_returnsCreatedResponse() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "image/webp");
        headers.put("Content-Length", "1024");
        headers.put("x-amz-checksum-sha256", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        PresignedImageUploadResult result = new PresignedImageUploadResult(
            "301",
            "https://storage.example/contents/test.webp",
            Instant.parse("2026-07-30T00:10:00Z"),
            headers
        );
        when(authenticatedUserResolver.resolveUserId(authentication)).thenReturn(10L);
        when(createPresignedImageUploadUseCase.createUpload(
            ArgumentMatchers.eq(10L),
            ArgumentMatchers.any(PresignedImageUploadCommand.class)
        )).thenReturn(result);

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mediaType": "image/webp",
                      "byteSize": 1024,
                      "checksum": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                      "usage": "CONTENT_REPRESENTATIVE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("대표 이미지 업로드 URL 발급에 성공했습니다."))
            .andExpect(jsonPath("$.data.imageObjectId").value("301"))
            .andExpect(jsonPath("$.data.uploadUrl").value("https://storage.example/contents/test.webp"))
            .andExpect(jsonPath("$.data.uploadHeaders['Content-Type']").value("image/webp"))
            .andExpect(jsonPath("$.data.uploadHeaders['Content-Length']").value("1024"))
            .andExpect(jsonPath("$.data.uploadHeaders['x-amz-checksum-sha256']")
                .value("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
    }

    @Test
    void createPresignedUploadUrl_whenRequiredFieldIsMissing_returnsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
