package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetPublicContentResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/contents")
public class PublicContentDetailController {

    private static final String SUCCESS_MESSAGE = "공개 콘텐츠 상세 조회에 성공했습니다.";
    private static final Pattern CONTENT_ID_PATTERN = Pattern.compile("^[1-9][0-9]{0,9}$");

    private final GetPublicContentUseCase getPublicContentUseCase;

    public PublicContentDetailController(GetPublicContentUseCase getPublicContentUseCase) {
        this.getPublicContentUseCase = getPublicContentUseCase;
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<GetPublicContentResponse>> getPublicContent(
        @PathVariable String contentId
    ) {
        PublicContentDetailResult result = getPublicContentUseCase.get(toContentId(contentId));
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPublicContentResponse.from(result)
        ).toResponseEntity();
    }

    private Long toContentId(String value) {
        if (!CONTENT_ID_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return Long.valueOf(value);
    }
}
