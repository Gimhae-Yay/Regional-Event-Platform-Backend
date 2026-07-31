package io.regionevent.regioneventbackend.domain.content.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.ContentHistoryResponse;
import io.regionevent.regioneventbackend.domain.content.service.ContentHistoryResult;
import io.regionevent.regioneventbackend.domain.content.service.GetContentHistoryUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/contents")
public class ContentHistoryController {

    private static final String SUCCESS_MESSAGE = "콘텐츠 이력 조회에 성공했습니다.";

    private final GetContentHistoryUseCase getContentHistoryUseCase;

    public ContentHistoryController(GetContentHistoryUseCase getContentHistoryUseCase) {
        this.getContentHistoryUseCase = getContentHistoryUseCase;
    }

    @GetMapping("/{contentId}/history")
    public ResponseEntity<ApiResponse<ContentHistoryResponse>> getContentHistory(
        Authentication authentication,
        @PathVariable @Positive Long contentId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        ContentHistoryResult result = getContentHistoryUseCase.get(userId, contentId);
        ContentHistoryResponse response = ContentHistoryResponse.from(result);
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
