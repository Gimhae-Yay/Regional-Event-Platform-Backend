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
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest.SessionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentResponse;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.ContentService.CreateContentCommand;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService.CreateContentSessionCommand;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageConnectionService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateContentUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long IMAGE_OBJECT_ID = 300L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final OffsetDateTime PUBLISH_AT = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-08-11T10:00:00+09:00");
    private static final OffsetDateTime ENDS_AT = OffsetDateTime.parse("2026-08-11T12:00:00+09:00");
    private static final OffsetDateTime CHECKIN_OPEN_AT = OffsetDateTime.parse("2026-08-11T09:30:00+09:00");
    private static final OffsetDateTime CHECKIN_CLOSE_AT = OffsetDateTime.parse("2026-08-11T11:30:00+09:00");

    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RepresentativeImageConnectionService representativeImageConnectionService =
        mock(RepresentativeImageConnectionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final CreateContentUseCase useCase = new CreateContentUseCase(
        operatorAuthorizationService,
        representativeImageConnectionService,
        contentService,
        contentSessionService,
        contentLogService,
        Clock.fixed(SUBMITTED_AT, ZoneOffset.UTC)
    );

    @Test
    void createContent_유효한_요청이면_콘텐츠와_회차와_로그를_생성한다() {
        AuthorizedOperator operator = authorizedOperator();
        ImageObject representativeImageObject = mock(ImageObject.class);
        Content content = mock(Content.class);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(representativeImageConnectionService.validateAndMarkConnected(
            IMAGE_OBJECT_ID,
            USER_ID,
            REGION_ID
        )).thenReturn(representativeImageObject);
        when(contentService.createPendingContent(
            eq(operator.region()),
            eq(operator.user()),
            eq(representativeImageObject),
            any(CreateContentCommand.class),
            eq(SUBMITTED_AT)
        )).thenReturn(content);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getStatus()).thenReturn(ContentStatus.PENDING);

        CreateContentResponse response = useCase.createContent(USER_ID, validRequest(stringNode("300")));

        assertThat(response).isEqualTo(new CreateContentResponse(
            CONTENT_ID.toString(),
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            SUBMITTED_AT
        ));
        ArgumentCaptor<CreateContentCommand> contentCommandCaptor =
            ArgumentCaptor.forClass(CreateContentCommand.class);
        verify(contentService).createPendingContent(
            eq(operator.region()),
            eq(operator.user()),
            eq(representativeImageObject),
            contentCommandCaptor.capture(),
            eq(SUBMITTED_AT)
        );
        assertThat(contentCommandCaptor.getValue().publishAt()).isEqualTo(PUBLISH_AT.toInstant());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateContentSessionCommand>> sessionCommandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(contentSessionService).createPendingSessions(
            eq(content),
            eq(operator.region()),
            sessionCommandsCaptor.capture()
        );
        assertThat(sessionCommandsCaptor.getValue()).containsExactly(new CreateContentSessionCommand(
            STARTS_AT.toInstant(),
            ENDS_AT.toInstant(),
            CHECKIN_OPEN_AT.toInstant(),
            CHECKIN_CLOSE_AT.toInstant(),
            20
        ));
        verify(contentLogService).recordPending(content, operator.user(), SUBMITTED_AT);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void createContent_요청_또는_회차_규칙이_잘못되면_입력_오류를_반환한다(
        CreateContentRequest request
    ) {
        assertThatThrownBy(() -> useCase.createContent(USER_ID, request))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
        verifyNoInteractions(
            operatorAuthorizationService,
            representativeImageConnectionService,
            contentService,
            contentSessionService,
            contentLogService
        );
    }

    @ParameterizedTest
    @MethodSource("invalidImageObjectIds")
    void createContent_대표_이미지_식별자_형식이_잘못되면_해당_오류를_반환한다(
        JsonNode imageObjectId,
        ErrorCode expectedErrorCode
    ) {
        AuthorizedOperator operator = authorizedOperator();
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);

        assertThatThrownBy(() -> useCase.createContent(USER_ID, validRequest(imageObjectId)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
            );
        verifyNoInteractions(representativeImageConnectionService, contentService, contentSessionService, contentLogService);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of((CreateContentRequest) null),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), null)),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of())),
            Arguments.of(request(PUBLISH_AT.withOffsetSameInstant(ZoneOffset.UTC), stringNode("300"), List.of(validSession()))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), Collections.singletonList(null))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of(new SessionRequest(
                STARTS_AT.withOffsetSameInstant(ZoneOffset.UTC),
                ENDS_AT,
                CHECKIN_OPEN_AT,
                CHECKIN_CLOSE_AT,
                20
            )))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of(new SessionRequest(
                ENDS_AT,
                STARTS_AT,
                CHECKIN_OPEN_AT,
                CHECKIN_CLOSE_AT,
                20
            )))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of(new SessionRequest(
                STARTS_AT,
                ENDS_AT,
                CHECKIN_CLOSE_AT,
                CHECKIN_OPEN_AT,
                20
            )))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of(new SessionRequest(
                STARTS_AT,
                CHECKIN_CLOSE_AT,
                CHECKIN_OPEN_AT,
                CHECKIN_CLOSE_AT,
                20
            )))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of(new SessionRequest(
                STARTS_AT,
                ENDS_AT,
                CHECKIN_OPEN_AT,
                CHECKIN_CLOSE_AT,
                null
            )))),
            Arguments.of(request(PUBLISH_AT, stringNode("300"), List.of(new SessionRequest(
                STARTS_AT,
                ENDS_AT,
                CHECKIN_OPEN_AT,
                CHECKIN_CLOSE_AT,
                0
            ))))
        );
    }

    private static Stream<Arguments> invalidImageObjectIds() {
        return Stream.of(
            Arguments.of(null, ErrorCode.INVALID_TYPE),
            Arguments.of(JsonNodeFactory.instance.numberNode(300), ErrorCode.INVALID_TYPE),
            Arguments.of(stringNode("0"), ErrorCode.INVALID_INPUT),
            Arguments.of(stringNode("00300"), ErrorCode.INVALID_INPUT),
            Arguments.of(stringNode("999999999999999999999"), ErrorCode.INVALID_INPUT)
        );
    }

    private AuthorizedOperator authorizedOperator() {
        AppUser operator = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(operator.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        return new AuthorizedOperator(operator, region, assignment);
    }

    private static CreateContentRequest validRequest(JsonNode imageObjectId) {
        return request(PUBLISH_AT, imageObjectId, List.of(validSession()));
    }

    private static CreateContentRequest request(
        OffsetDateTime publishAt,
        JsonNode imageObjectId,
        List<SessionRequest> sessions
    ) {
        return new CreateContentRequest(
            "콘텐츠 제목",
            "콘텐츠 설명",
            "김해",
            "10:00-18:00",
            "055-0000-0000",
            "주의사항",
            "전체",
            "준비물",
            "취소 정책",
            publishAt,
            imageObjectId,
            sessions
        );
    }

    private static SessionRequest validSession() {
        return new SessionRequest(STARTS_AT, ENDS_AT, CHECKIN_OPEN_AT, CHECKIN_CLOSE_AT, 20);
    }

    private static JsonNode stringNode(String value) {
        return JsonNodeFactory.instance.stringNode(value);
    }
}
