package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetPublicContentSessionsResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentSessionsUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/contents")
public class PublicContentSessionController {

    private static final String GET_PUBLIC_CONTENT_SESSIONS_SUCCESS_MESSAGE = "콘텐츠 회차 목록 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetPublicContentSessionsUseCase getPublicContentSessionsUseCase;

    public PublicContentSessionController(GetPublicContentSessionsUseCase getPublicContentSessionsUseCase) {
        this.getPublicContentSessionsUseCase = getPublicContentSessionsUseCase;
    }

    @GetMapping("/{contentId}/sessions")
    public ResponseEntity<ApiResponse<GetPublicContentSessionsResponse>> getPublicContentSessions(
        @PathVariable String contentId
    ) {
        Long parsedContentId = toContentId(contentId);
        List<ContentSession> contentSessions = getPublicContentSessionsUseCase.get(parsedContentId);
        GetPublicContentSessionsResponse response = GetPublicContentSessionsResponse.from(
            parsedContentId,
            contentSessions
        );
        return ApiResponse.success(
            HttpStatus.OK,
            GET_PUBLIC_CONTENT_SESSIONS_SUCCESS_MESSAGE,
            response
        ).toResponseEntity();
    }

    private Long toContentId(String value) {
        Long contentId;
        try {
            contentId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return contentId;
    }
}
