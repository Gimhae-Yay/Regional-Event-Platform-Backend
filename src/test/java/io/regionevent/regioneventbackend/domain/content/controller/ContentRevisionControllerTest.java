package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import io.regionevent.regioneventbackend.domain.content.dto.RejectContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.RejectContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

class ContentRevisionControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long REVISION_ID = 501L;
    private static final Long CONTENT_ID = 101L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-02T01:00:00Z");

    private final RejectContentRevisionUseCase rejectContentRevisionUseCase =
        mock(RejectContentRevisionUseCase.class);
    private final ContentRevisionController contentRevisionController =
        new ContentRevisionController(rejectContentRevisionUseCase);

    @Test
    void rejectContentRevision_returnsApiContractResponse() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);
        when(rejectContentRevisionUseCase.reject(
            USER_ID,
            REVISION_ID,
            "반려 사유",
            REQUEST_ID
        )).thenReturn(new RejectContentRevisionResult(
            REVISION_ID,
            CONTENT_ID,
            ContentRevisionStatus.EDIT_REJECTED,
            ContentStatus.PENDING,
            "반려 사유",
            REVIEWED_AT
        ));

        ResponseEntity<ApiResponse<RejectContentRevisionResponse>> response =
            contentRevisionController.rejectContentRevision(
                authentication,
                REVISION_ID.toString(),
                new RejectContentRevisionRequest("반려 사유"),
                REQUEST_ID.toString()
            );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SUCCESS");
        assertThat(response.getBody().message()).isEqualTo("콘텐츠 수정본 반려에 성공했습니다.");
        assertThat(response.getBody().data()).isEqualTo(new RejectContentRevisionResponse(
            "501",
            "101",
            "EDIT_REJECTED",
            "PENDING",
            "반려 사유",
            REVIEWED_AT
        ));
    }

    @Test
    void rejectContentRevision_whenRevisionIdIsNotPositiveDecimal_throwsInvalidInput() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);

        assertThatThrownBy(() -> contentRevisionController.rejectContentRevision(
            authentication,
            "01",
            new RejectContentRevisionRequest("반려 사유"),
            REQUEST_ID.toString()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );
    }
}
