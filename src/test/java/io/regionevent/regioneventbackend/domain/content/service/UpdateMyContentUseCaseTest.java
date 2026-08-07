package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentService.UpdateContentCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectCleanupService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService.DeletePendingImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class UpdateMyContentUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long PREVIOUS_IMAGE_OBJECT_ID = 300L;
    private static final Long REPLACEMENT_IMAGE_OBJECT_ID = 400L;
    private static final Instant UPDATED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final OffsetDateTime PUBLISH_AT = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RepresentativeImageConnectionService representativeImageConnectionService =
        mock(RepresentativeImageConnectionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ImageObjectService imageObjectService = mock(ImageObjectService.class);
    private final ImageObjectCleanupService imageObjectCleanupService = mock(ImageObjectCleanupService.class);
    private final UpdateMyContentUseCase useCase = new UpdateMyContentUseCase(
        operatorAuthorizationService,
        representativeImageConnectionService,
        contentService,
        imageObjectService,
        imageObjectCleanupService,
        Clock.fixed(UPDATED_AT, ZoneOffset.UTC)
    );

    @Test
    void updateContent_새_대표_이미지면_콘텐츠를_갱신하고_커밋_후_이전_이미지를_정리한다() {
        Fixture fixture = fixture();
        ImageObject replacementImageObject = mock(ImageObject.class);
        DeletePendingImageObject deletePendingImageObject = new DeletePendingImageObject(
            PREVIOUS_IMAGE_OBJECT_ID,
            "contents/previous.webp"
        );
        Content updatedContent = updatedContent();
        stubTarget(fixture);
        when(representativeImageConnectionService.validateAndMarkConnected(
            REPLACEMENT_IMAGE_OBJECT_ID,
            USER_ID,
            REGION_ID
        )).thenReturn(replacementImageObject);
        when(contentService.updateRejectedContent(
            eq(fixture.content()),
            any(UpdateContentCommand.class),
            eq(replacementImageObject),
            eq(UPDATED_AT)
        )).thenReturn(updatedContent);
        when(imageObjectService.markDeletePendingIfUnreferenced(
            fixture.previousImageObject(),
            replacementImageObject
        )).thenReturn(Optional.of(deletePendingImageObject));

        TransactionSynchronizationManager.initSynchronization();
        try {
            UpdateMyContentResponse response = useCase.updateContent(
                USER_ID,
                CONTENT_ID,
                request(PUBLISH_AT, stringNode("400"))
            );

            assertThat(response).isEqualTo(new UpdateMyContentResponse(
                CONTENT_ID.toString(),
                ContentStatus.REJECTED
            ));
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            verify(imageObjectCleanupService).deletePendingObject(
                PREVIOUS_IMAGE_OBJECT_ID,
                "contents/previous.webp"
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateContent_같은_대표_이미지면_이미지를_다시_연결하거나_정리하지_않는다() {
        Fixture fixture = fixture();
        Content updatedContent = updatedContent();
        when(fixture.content().hasRepresentativeImage(PREVIOUS_IMAGE_OBJECT_ID)).thenReturn(true);
        stubTarget(fixture);
        when(contentService.updateRejectedContent(
            eq(fixture.content()),
            any(UpdateContentCommand.class),
            eq(null),
            eq(UPDATED_AT)
        )).thenReturn(updatedContent);

        useCase.updateContent(USER_ID, CONTENT_ID, request(PUBLISH_AT, stringNode("300")));

        verifyNoInteractions(
            representativeImageConnectionService,
            imageObjectService,
            imageObjectCleanupService
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void updateContent_요청_규칙이_잘못되면_입력_오류를_반환한다(UpdateMyContentRequest request) {
        assertThatThrownBy(() -> useCase.updateContent(USER_ID, CONTENT_ID, request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(
            operatorAuthorizationService,
            representativeImageConnectionService,
            contentService,
            imageObjectService,
            imageObjectCleanupService
        );
    }

    @ParameterizedTest
    @MethodSource("invalidImageObjectIds")
    void updateContent_대표_이미지_식별자가_양의_정수가_아니면_입력_오류를_반환한다(
        JsonNode imageObjectId
    ) {
        Fixture fixture = fixture();
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateContent(USER_ID, CONTENT_ID, request(PUBLISH_AT, imageObjectId)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    @Test
    void updateContent_대표_이미지_식별자_타입이_아니면_타입_오류를_반환한다() {
        Fixture fixture = fixture();
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateContent(
            USER_ID,
            CONTENT_ID,
            request(PUBLISH_AT, JsonNodeFactory.instance.numberNode(REPLACEMENT_IMAGE_OBJECT_ID))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TYPE)
        );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of((UpdateMyContentRequest) null),
            Arguments.of(request(PUBLISH_AT.withOffsetSameInstant(ZoneOffset.UTC), null))
        );
    }

    private static Stream<Arguments> invalidImageObjectIds() {
        return Stream.of(
            Arguments.of(stringNode("0")),
            Arguments.of(stringNode("999999999999999999999"))
        );
    }

    private Fixture fixture() {
        AppUser operator = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Content content = mock(Content.class);
        ImageObject previousImageObject = mock(ImageObject.class);
        when(operator.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getId()).thenReturn(new UserRoleAssignmentId(USER_ID, UserRole.OPERATOR));
        when(content.getRepresentativeImageObject()).thenReturn(previousImageObject);
        return new Fixture(content, previousImageObject, new AuthorizedOperator(operator, region, assignment));
    }

    private void stubTarget(Fixture fixture) {
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(fixture.operator());
        when(contentService.findRejectedOwnedContentForUpdate(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(fixture.content());
    }

    private Content updatedContent() {
        Content content = mock(Content.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getStatus()).thenReturn(ContentStatus.REJECTED);
        return content;
    }

    private static UpdateMyContentRequest request(
        OffsetDateTime publishAt,
        JsonNode imageObjectId
    ) {
        return new UpdateMyContentRequest(
            "수정 제목",
            "수정 설명",
            "김해",
            "10:00-18:00",
            "055-0000-0000",
            "주의사항",
            "전체",
            "준비물",
            "취소 정책",
            publishAt,
            imageObjectId
        );
    }

    private static JsonNode stringNode(String value) {
        return JsonNodeFactory.instance.stringNode(value);
    }

    private record Fixture(
        Content content,
        ImageObject previousImageObject,
        AuthorizedOperator operator
    ) {
    }
}
