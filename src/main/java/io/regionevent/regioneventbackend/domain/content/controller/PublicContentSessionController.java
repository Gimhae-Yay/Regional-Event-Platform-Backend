package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.List;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetPublicContentSessionsResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentSessionsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/contents")
public class PublicContentSessionController {

    private static final String GET_PUBLIC_CONTENT_SESSIONS_SUCCESS_MESSAGE = "콘텐츠 회차 목록 조회에 성공했습니다.";

    private final GetPublicContentSessionsUseCase getPublicContentSessionsUseCase;

    public PublicContentSessionController(GetPublicContentSessionsUseCase getPublicContentSessionsUseCase) {
        this.getPublicContentSessionsUseCase = getPublicContentSessionsUseCase;
    }

    @GetMapping("/{contentId}/sessions")
    public ResponseEntity<ApiResponse<GetPublicContentSessionsResponse>> getPublicContentSessions(
        @PathVariable @Positive Long contentId
    ) {
        List<ContentSession> contentSessions = getPublicContentSessionsUseCase.get(contentId);
        GetPublicContentSessionsResponse response = GetPublicContentSessionsResponse.from(
            contentId,
            contentSessions
        );
        return ApiResponse.success(
            HttpStatus.OK,
            GET_PUBLIC_CONTENT_SESSIONS_SUCCESS_MESSAGE,
            response
        ).toResponseEntity();
    }
}
