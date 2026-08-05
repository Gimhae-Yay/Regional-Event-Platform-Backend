package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionService.UpdateContentRevisionCommand;
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

class UpdateContentRevisionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long REVISION_ID = 300L;
    private static final Long PREVIOUS_IMAGE_OBJECT_ID = 400L;
    private static final Long REPLACEMENT_IMAGE_OBJECT_ID = 500L;
    private static final OffsetDateTime PUBLISH_AT = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RepresentativeImageConnectionService representativeImageConnectionService =
        mock(RepresentativeImageConnectionService.class);
    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final ImageObjectService imageObjectService = mock(ImageObjectService.class);
    private final ImageObjectCleanupService imageObjectCleanupService = mock(ImageObjectCleanupService.class);
    private final UpdateContentRevisionUseCase useCase = new UpdateContentRevisionUseCase(
        operatorAuthorizationService,
        representativeImageConnectionService,
        contentRevisionService,
        imageObjectService,
        imageObjectCleanupService
    );

    @Test
    void updateRevision_새_대표_이미지면_수정본을_갱신하고_커밋_후_이전_이미지를_정리한다() {
        Fixture fixture = fixture(true);
        ImageObject replacementImageObject = mock(ImageObject.class);
        DeletePendingImageObject deletePendingImageObject = new DeletePendingImageObject(
            PREVIOUS_IMAGE_OBJECT_ID,
            "contents/previous.webp"
        );
        ContentRevision updatedRevision = updatedRevision(fixture.content());
        when(replacementImageObject.getLinkedAt()).thenReturn(Instant.parse("2026-08-05T00:00:00Z"));
        stubTarget(fixture);
        when(representativeImageConnectionService.validateAndMarkConnected(
            REPLACEMENT_IMAGE_OBJECT_ID,
            USER_ID,
            REGION_ID
        )).thenReturn(replacementImageObject);
        when(contentRevisionService.updateRejectedRevision(
            eq(fixture.revision()),
            any(UpdateContentRevisionCommand.class)
        )).thenReturn(updatedRevision);
        when(imageObjectService.markDeletePendingIfUnreferenced(
            fixture.previousImageObject(),
            replacementImageObject
        )).thenReturn(Optional.of(deletePendingImageObject));

        TransactionSynchronizationManager.initSynchronization();
        try {
            UpdateContentRevisionResponse response = useCase.updateRevision(
                USER_ID,
                REVISION_ID,
                request(PUBLISH_AT, stringNode("500"))
            );

            assertThat(response).isEqualTo(new UpdateContentRevisionResponse(
                REVISION_ID.toString(),
                CONTENT_ID.toString(),
                ContentRevisionStatus.EDIT_REQUESTED
            ));
            ArgumentCaptor<UpdateContentRevisionCommand> commandCaptor =
                ArgumentCaptor.forClass(UpdateContentRevisionCommand.class);
            verify(contentRevisionService).updateRejectedRevision(eq(fixture.revision()), commandCaptor.capture());
            assertThat(commandCaptor.getValue().publishAt()).isEqualTo(PUBLISH_AT.toInstant());
            assertThat(commandCaptor.getValue().candidateImageObject()).isEqualTo(replacementImageObject);
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
    void updateRevision_기존_이미지와_공개_시각_후보가_없으면_이미지_정리를_등록하지_않는다() {
        Fixture fixture = fixture(false);
        ContentRevision updatedRevision = updatedRevision(fixture.content());
        stubTarget(fixture);
        when(contentRevisionService.updateRejectedRevision(
            eq(fixture.revision()),
            any(UpdateContentRevisionCommand.class)
        )).thenReturn(updatedRevision);

        useCase.updateRevision(USER_ID, REVISION_ID, request(null, null));

        verifyNoInteractions(
            representativeImageConnectionService,
            imageObjectService,
            imageObjectCleanupService
        );
    }

    @Test
    void updateRevision_삭제된_원본이면_찾을수없음을_반환한다() {
        Fixture fixture = fixture(false);
        when(fixture.content().getDeletedAt()).thenReturn(Instant.parse("2026-08-04T00:00:00Z"));
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateRevision(USER_ID, REVISION_ID, request(null, null)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    @Test
    void updateRevision_운영자나_지역이_다르면_권한_오류를_반환한다() {
        Fixture fixture = fixture(false);
        AppUser anotherOperator = mock(AppUser.class);
        when(anotherOperator.getUserId()).thenReturn(USER_ID + 1);
        when(fixture.content().getOperator()).thenReturn(anotherOperator);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateRevision(USER_ID, REVISION_ID, request(null, null)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void updateRevision_요청_규칙이_잘못되면_입력_오류를_반환한다(
        UpdateContentRevisionRequest request
    ) {
        assertThatThrownBy(() -> useCase.updateRevision(USER_ID, REVISION_ID, request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(
            operatorAuthorizationService,
            representativeImageConnectionService,
            contentRevisionService,
            imageObjectService,
            imageObjectCleanupService
        );
    }

    @Test
    void updateRevision_공개_시각_후보가_있는데_서울_시간이_아니면_입력_오류를_반환한다() {
        Fixture fixture = fixture(true);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateRevision(
            USER_ID,
            REVISION_ID,
            request(PUBLISH_AT.withOffsetSameInstant(ZoneOffset.UTC), null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    @ParameterizedTest
    @MethodSource("invalidImageObjectIds")
    void updateRevision_대표_이미지_식별자가_양의_정수가_아니면_입력_오류를_반환한다(
        JsonNode imageObjectId
    ) {
        Fixture fixture = fixture(false);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateRevision(USER_ID, REVISION_ID, request(null, imageObjectId)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    @Test
    void updateRevision_대표_이미지_식별자_타입이_아니면_타입_오류를_반환한다() {
        Fixture fixture = fixture(false);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.updateRevision(
            USER_ID,
            REVISION_ID,
            request(null, JsonNodeFactory.instance.numberNode(REPLACEMENT_IMAGE_OBJECT_ID))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TYPE)
        );
        verifyNoInteractions(representativeImageConnectionService, imageObjectService, imageObjectCleanupService);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of((UpdateContentRevisionRequest) null)
        );
    }

    private static Stream<Arguments> invalidImageObjectIds() {
        return Stream.of(
            Arguments.of(stringNode("0")),
            Arguments.of(stringNode("999999999999999999999"))
        );
    }

    private Fixture fixture(boolean hasCandidatePublishAt) {
        AppUser operator = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Content content = mock(Content.class);
        ContentRevision revision = mock(ContentRevision.class);
        ImageObject previousImageObject = mock(ImageObject.class);
        when(operator.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getId()).thenReturn(new UserRoleAssignmentId(USER_ID, UserRole.OPERATOR));
        when(content.getOperator()).thenReturn(operator);
        when(content.getRegion()).thenReturn(region);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(revision.getContent()).thenReturn(content);
        when(revision.getCandidateImageObject()).thenReturn(previousImageObject);
        when(revision.hasCandidatePublishAt()).thenReturn(hasCandidatePublishAt);
        return new Fixture(revision, content, previousImageObject, new AuthorizedOperator(operator, region, assignment));
    }

    private void stubTarget(Fixture fixture) {
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(fixture.operator());
        when(contentRevisionService.findRejectedRevisionForUpdate(REVISION_ID)).thenReturn(fixture.revision());
    }

    private ContentRevision updatedRevision(Content content) {
        ContentRevision revision = mock(ContentRevision.class);
        when(revision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(revision.getContent()).thenReturn(content);
        when(revision.getStatus()).thenReturn(ContentRevisionStatus.EDIT_REQUESTED);
        return revision;
    }

    private static UpdateContentRevisionRequest request(
        OffsetDateTime publishAt,
        JsonNode imageObjectId
    ) {
        return new UpdateContentRevisionRequest(
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
        ContentRevision revision,
        Content content,
        ImageObject previousImageObject,
        AuthorizedOperator operator
    ) {
    }
}
