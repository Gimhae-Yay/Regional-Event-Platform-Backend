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

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionService.CreateContentRevisionCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateContentRevisionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long REVISION_ID = 300L;
    private static final Long IMAGE_OBJECT_ID = 400L;
    private static final String REQUEST_ID = "00000000-0000-0000-0000-000000000406";
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final OffsetDateTime PUBLISH_AT = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RepresentativeImageConnectionService representativeImageConnectionService =
        mock(RepresentativeImageConnectionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final CreateContentRevisionUseCase useCase = new CreateContentRevisionUseCase(
        operatorAuthorizationService,
        representativeImageConnectionService,
        contentService,
        contentRevisionService,
        contentLogService,
        recordAuditEventUseCase,
        Clock.fixed(SUBMITTED_AT, ZoneOffset.UTC)
    );

    @Test
    void createRevision_공개_콘텐츠는_기존_대표_이미지로_수정본을_생성한다() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED);
        ImageObject representativeImageObject = mock(ImageObject.class);
        Instant assignedAt = Instant.parse("2026-08-01T00:00:00Z");
        ContentRevision createdRevision = createdRevision(fixture.content());
        when(fixture.content().getRepresentativeImageObject()).thenReturn(representativeImageObject);
        when(fixture.content().getRepresentativeImageAssignedAt()).thenReturn(assignedAt);
        stubTarget(fixture);
        when(contentRevisionService.createEditRequestedRevision(
            eq(fixture.content()),
            eq(fixture.operator().user()),
            any(CreateContentRevisionCommand.class),
            eq(representativeImageObject),
            eq(assignedAt),
            eq(SUBMITTED_AT)
        )).thenReturn(createdRevision);

        CreateContentRevisionResponse response = useCase.createRevision(
            USER_ID,
            CONTENT_ID,
            request(null, null),
            REQUEST_ID
        );

        assertThat(response).isEqualTo(new CreateContentRevisionResponse(
            REVISION_ID.toString(),
            CONTENT_ID.toString(),
            ContentRevisionStatus.EDIT_REQUESTED,
            3,
            SUBMITTED_AT
        ));
        ArgumentCaptor<CreateContentRevisionCommand> commandCaptor =
            ArgumentCaptor.forClass(CreateContentRevisionCommand.class);
        verify(contentRevisionService).createEditRequestedRevision(
            eq(fixture.content()),
            eq(fixture.operator().user()),
            commandCaptor.capture(),
            eq(representativeImageObject),
            eq(assignedAt),
            eq(SUBMITTED_AT)
        );
        assertThat(commandCaptor.getValue().publishAt()).isNull();
        verifyNoInteractions(representativeImageConnectionService, recordAuditEventUseCase);
    }

    @Test
    void createRevision_승인_대기_전_콘텐츠는_새_이미지를_연결하고_감사_이벤트를_기록한다() {
        Fixture fixture = fixture(ContentStatus.APPROVED);
        Content pendingContent = fixture.content();
        ImageObject candidateImageObject = mock(ImageObject.class);
        Instant assignedAt = Instant.parse("2026-08-04T00:00:00Z");
        ContentRevision createdRevision = createdRevision(pendingContent);
        when(candidateImageObject.getLinkedAt()).thenReturn(assignedAt);
        stubTarget(fixture);
        when(contentService.markPrePublicationRevisionPending(fixture.content())).thenReturn(pendingContent);
        when(representativeImageConnectionService.validateAndMarkConnected(
            IMAGE_OBJECT_ID,
            USER_ID,
            REGION_ID
        )).thenReturn(candidateImageObject);
        when(contentRevisionService.createEditRequestedRevision(
            eq(pendingContent),
            eq(fixture.operator().user()),
            any(CreateContentRevisionCommand.class),
            eq(candidateImageObject),
            eq(assignedAt),
            eq(SUBMITTED_AT)
        )).thenReturn(createdRevision);

        useCase.createRevision(USER_ID, CONTENT_ID, request(PUBLISH_AT, stringNode("400")), REQUEST_ID);

        verify(contentService).markPrePublicationRevisionPending(fixture.content());
        verify(contentLogService).recordPending(pendingContent, fixture.operator().user(), SUBMITTED_AT);
        ArgumentCaptor<AuditEventCommand> auditCommandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCommandCaptor.capture());
        AuditEventCommand auditCommand = auditCommandCaptor.getValue();
        assertThat(auditCommand.requestId()).isEqualTo(UUID.fromString(REQUEST_ID));
        assertThat(auditCommand.region()).isEqualTo(fixture.region());
        assertThat(auditCommand.targetType()).isEqualTo(AuditEventTargetType.CONTENT);
        assertThat(auditCommand.targetId()).isEqualTo(CONTENT_ID);
        assertThat(auditCommand.previousState()).isEqualTo(ContentStatus.APPROVED.name());
        assertThat(auditCommand.nextState()).isEqualTo(ContentStatus.PENDING.name());
        assertThat(auditCommand.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditCommand.occurredAt()).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void createRevision_대기_콘텐츠에_이력_근거가_없으면_상태_충돌을_반환한다() {
        Fixture fixture = fixture(ContentStatus.PENDING);
        stubTarget(fixture);
        when(contentLogService.hasApprovedToPendingRevisionHistory(fixture.content())).thenReturn(false);

        assertThatThrownBy(() -> useCase.createRevision(
            USER_ID,
            CONTENT_ID,
            request(PUBLISH_AT, null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
        verifyNoInteractions(contentRevisionService, representativeImageConnectionService, recordAuditEventUseCase);
    }

    @Test
    void createRevision_공개_콘텐츠에_공개_시각을_보내면_상태_충돌을_반환한다() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.createRevision(
            USER_ID,
            CONTENT_ID,
            request(PUBLISH_AT, null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
        verifyNoInteractions(contentRevisionService, representativeImageConnectionService, recordAuditEventUseCase);
    }

    @Test
    void createRevision_승인_콘텐츠의_공개_시각이_서울_오프셋이_아니면_상태_충돌을_반환한다() {
        Fixture fixture = fixture(ContentStatus.APPROVED);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.createRevision(
            USER_ID,
            CONTENT_ID,
            request(PUBLISH_AT.withOffsetSameInstant(ZoneOffset.UTC), null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
    }

    @Test
    void createRevision_기존_대표_이미지가_없으면_상태_충돌을_반환한다() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED);
        stubTarget(fixture);
        when(fixture.content().getRepresentativeImageObject()).thenReturn(null);

        assertThatThrownBy(() -> useCase.createRevision(
            USER_ID,
            CONTENT_ID,
            request(null, null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
        );
    }

    @Test
    void createRevision_대표_이미지_식별자_타입이_아니면_타입_오류를_반환한다() {
        Fixture fixture = fixture(ContentStatus.APPROVED);
        stubTarget(fixture);

        assertThatThrownBy(() -> useCase.createRevision(
            USER_ID,
            CONTENT_ID,
            request(PUBLISH_AT, JsonNodeFactory.instance.numberNode(400)),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TYPE)
        );
        verifyNoInteractions(representativeImageConnectionService, contentRevisionService, recordAuditEventUseCase);
    }

    private Fixture fixture(ContentStatus status) {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Content content = mock(Content.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        when(assignment.getAppUser()).thenReturn(user);
        when(assignment.getRole()).thenReturn(UserRole.OPERATOR);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(content.getStatus()).thenReturn(status);
        return new Fixture(content, region, new AuthorizedOperator(user, region, assignment));
    }

    private void stubTarget(Fixture fixture) {
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(fixture.operator());
        when(contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(fixture.content());
    }

    private ContentRevision createdRevision(Content content) {
        ContentRevision revision = mock(ContentRevision.class);
        when(revision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(revision.getContent()).thenReturn(content);
        when(revision.getStatus()).thenReturn(ContentRevisionStatus.EDIT_REQUESTED);
        when(revision.getBaseContentVersion()).thenReturn(3);
        when(revision.getSubmittedAt()).thenReturn(SUBMITTED_AT);
        return revision;
    }

    private static CreateContentRevisionRequest request(
        OffsetDateTime publishAt,
        JsonNode imageObjectId
    ) {
        return new CreateContentRevisionRequest(
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
        Region region,
        AuthorizedOperator operator
    ) {
    }
}
