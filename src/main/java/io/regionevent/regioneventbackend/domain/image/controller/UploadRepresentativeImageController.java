package io.regionevent.regioneventbackend.domain.image.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.image.dto.UploadRepresentativeImageRequest;
import io.regionevent.regioneventbackend.domain.image.dto.UploadRepresentativeImageResponse;
import io.regionevent.regioneventbackend.domain.image.service.UploadRepresentativeImageUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/uploads")
public class UploadRepresentativeImageController {

    private static final String UPLOAD_URL_CREATED_MESSAGE = "대표 이미지 업로드 URL 발급에 성공했습니다.";

    private final UploadRepresentativeImageUseCase uploadRepresentativeImageUseCase;

    public UploadRepresentativeImageController(
        UploadRepresentativeImageUseCase uploadRepresentativeImageUseCase
    ) {
        this.uploadRepresentativeImageUseCase = uploadRepresentativeImageUseCase;
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<UploadRepresentativeImageResponse>> createPresignedUrl(
        Authentication authentication,
        @Valid @RequestBody UploadRepresentativeImageRequest request
    ) {
        UploadRepresentativeImageResponse response = uploadRepresentativeImageUseCase.createUpload(
            toAuthenticatedUserId(authentication),
            request
        );
        return ApiResponse
            .success(HttpStatus.CREATED, UPLOAD_URL_CREATED_MESSAGE, response)
            .toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
