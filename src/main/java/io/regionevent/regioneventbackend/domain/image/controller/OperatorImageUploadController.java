package io.regionevent.regioneventbackend.domain.image.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.image.dto.PresignedImageUploadRequest;
import io.regionevent.regioneventbackend.domain.image.dto.PresignedImageUploadResponse;
import io.regionevent.regioneventbackend.domain.image.service.CreatePresignedImageUploadUseCase;
import io.regionevent.regioneventbackend.domain.image.service.PresignedImageUploadCommand;
import io.regionevent.regioneventbackend.domain.image.service.PresignedImageUploadResult;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.AuthenticatedUserResolver;

@RestController
@RequestMapping("/api/v1/operator/uploads")
public class OperatorImageUploadController {

    private static final String SUCCESS_MESSAGE = "대표 이미지 업로드 URL 발급에 성공했습니다.";

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final CreatePresignedImageUploadUseCase createPresignedImageUploadUseCase;

    public OperatorImageUploadController(
        AuthenticatedUserResolver authenticatedUserResolver,
        CreatePresignedImageUploadUseCase createPresignedImageUploadUseCase
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.createPresignedImageUploadUseCase = createPresignedImageUploadUseCase;
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedImageUploadResponse>> createPresignedUploadUrl(
        Authentication authentication,
        @Valid @RequestBody PresignedImageUploadRequest request
    ) {
        Long userId = authenticatedUserResolver.resolveUserId(authentication);
        PresignedImageUploadResult result = createPresignedImageUploadUseCase.createUpload(userId, toCommand(request));
        PresignedImageUploadResponse response = toResponse(result);
        return ApiResponse.success(HttpStatus.CREATED, SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }

    private static PresignedImageUploadCommand toCommand(PresignedImageUploadRequest request) {
        return new PresignedImageUploadCommand(
            request.mediaType(),
            request.byteSize(),
            request.checksum(),
            request.usage()
        );
    }

    private static PresignedImageUploadResponse toResponse(PresignedImageUploadResult result) {
        return new PresignedImageUploadResponse(
            result.imageObjectId(),
            result.uploadUrl(),
            result.expiresAt(),
            result.uploadHeaders()
        );
    }
}
