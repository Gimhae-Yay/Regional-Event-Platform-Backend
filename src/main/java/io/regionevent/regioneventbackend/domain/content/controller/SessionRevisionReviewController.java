package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.PendingSessionRevisionsResponse;
import io.regionevent.regioneventbackend.domain.content.dto.SessionRevisionReviewDetailResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionRevisionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetSessionRevisionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PendingSessionRevisionListResult;
import io.regionevent.regioneventbackend.domain.content.service.SessionRevisionReviewDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/session-revisions")
public class SessionRevisionReviewController {

    private static final String DETAIL_SUCCESS_MESSAGE = "심사 대기 회차 수정 요청 상세 조회에 성공했습니다.";
    private static final String LIST_SUCCESS_MESSAGE = "심사 대기 회차 수정 요청 목록 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetPendingSessionRevisionsUseCase getPendingSessionRevisionsUseCase;
    private final GetSessionRevisionReviewDetailUseCase getSessionRevisionReviewDetailUseCase;

    public SessionRevisionReviewController(
        GetPendingSessionRevisionsUseCase getPendingSessionRevisionsUseCase,
        GetSessionRevisionReviewDetailUseCase getSessionRevisionReviewDetailUseCase
    ) {
        this.getPendingSessionRevisionsUseCase = getPendingSessionRevisionsUseCase;
        this.getSessionRevisionReviewDetailUseCase = getSessionRevisionReviewDetailUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PendingSessionRevisionsResponse>> getPendingRevisions(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status
    ) {
        PendingSessionRevisionListResult result = getPendingSessionRevisionsUseCase.get(userId, status);
        return ApiResponse.success(
            HttpStatus.OK,
            LIST_SUCCESS_MESSAGE,
            PendingSessionRevisionsResponse.from(result)
        ).toResponseEntity();
    }

    @GetMapping("/{revisionId}")
    public ResponseEntity<ApiResponse<SessionRevisionReviewDetailResponse>> getReviewDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String revisionId
    ) {
        SessionRevisionReviewDetailResult result = getSessionRevisionReviewDetailUseCase.get(
            userId,
            toRevisionId(revisionId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            DETAIL_SUCCESS_MESSAGE,
            SessionRevisionReviewDetailResponse.from(result)
        ).toResponseEntity();
    }

    private Long toRevisionId(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
