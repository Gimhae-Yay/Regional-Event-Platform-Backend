package io.regionevent.regioneventbackend.domain.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.PendingSessionRevisionsResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionRevisionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PendingSessionRevisionListResult;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/session-revisions")
public class SessionRevisionReviewController {

    private static final String SUCCESS_MESSAGE = "심사 대기 회차 수정 요청 목록 조회에 성공했습니다.";

    private final GetPendingSessionRevisionsUseCase getPendingSessionRevisionsUseCase;

    public SessionRevisionReviewController(GetPendingSessionRevisionsUseCase getPendingSessionRevisionsUseCase) {
        this.getPendingSessionRevisionsUseCase = getPendingSessionRevisionsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PendingSessionRevisionsResponse>> getPendingRevisions(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status
    ) {
        PendingSessionRevisionListResult result = getPendingSessionRevisionsUseCase.get(userId, status);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            PendingSessionRevisionsResponse.from(result)
        ).toResponseEntity();
    }
}
