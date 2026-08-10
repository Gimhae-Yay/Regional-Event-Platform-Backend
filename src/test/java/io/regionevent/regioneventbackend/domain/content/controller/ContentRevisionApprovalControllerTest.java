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

import io.regionevent.regioneventbackend.domain.content.dto.ApproveContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentRevisionUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

class ContentRevisionApprovalControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long REVISION_ID = 501L;
    private static final Long CONTENT_ID = 101L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000215");
    private static final Instant PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-02T01:00:00Z");

    private final ApproveContentRevisionUseCase approveContentRevisionUseCase =
        mock(ApproveContentRevisionUseCase.class);
    private final ContentRevisionApprovalController controller =
        new ContentRevisionApprovalController(approveContentRevisionUseCase);

    @Test
    void approveContentRevision_returnsApiContractResponse() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);
        when(approveContentRevisionUseCase.approve(USER_ID, REVISION_ID, REQUEST_ID))
            .thenReturn(new ApproveContentRevisionResult(
                REVISION_ID,
                CONTENT_ID,
                ContentRevisionStatus.EDIT_APPROVED,
                ContentStatus.APPROVED,
                20_000,
                PUBLISH_AT,
                REVIEWED_AT
            ));

        ResponseEntity<ApiResponse<ApproveContentRevisionResponse>> response =
            controller.approveContentRevision(
                authentication,
                REVISION_ID.toString(),
                REQUEST_ID.toString()
            );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SUCCESS");
        assertThat(response.getBody().message()).isEqualTo("콘텐츠 수정본 승인에 성공했습니다.");
        assertThat(response.getBody().data()).isEqualTo(new ApproveContentRevisionResponse(
            "501",
            "101",
            "EDIT_APPROVED",
            "APPROVED",
            20_000,
            PUBLISH_AT,
            REVIEWED_AT
        ));
    }

    @Test
    void approveContentRevision_whenRevisionIdIsInvalid_throwsInvalidInput() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);

        for (String invalidRevisionId : new String[]{"0", "01", "+1", "not-a-number", "9223372036854775808"}) {
            assertThatThrownBy(() -> controller.approveContentRevision(
                authentication,
                invalidRevisionId,
                REQUEST_ID.toString()
            )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        }
    }

    @Test
    void approveContentRevision_withoutAuthenticatedPrincipal_throwsUnauthenticated() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        assertThatThrownBy(() -> controller.approveContentRevision(
            authentication,
            REVISION_ID.toString(),
            REQUEST_ID.toString()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED)
        );
    }
}
