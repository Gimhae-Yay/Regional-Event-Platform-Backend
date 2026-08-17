package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.MissionHistoryAuditProjection;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionEarlyEndReasonCode;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class MissionHistoryReadServiceTest {

    private static final Long MISSION_ID = 701L;
    private static final Instant RECORDED_AT = Instant.parse("2026-08-07T04:20:00Z");

    private final AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
    private final MissionHistoryReadService missionHistoryReadService = new MissionHistoryReadService(
        auditEventRepository
    );

    @Test
    void findAll_everyAction_mapsActionsAndActorKinds() {
        when(auditEventRepository.findMissionHistoryAuditProjections(MISSION_ID)).thenReturn(List.of(
            projection(1L, null, "DRAFT", "MISSION_CREATED", "USER", 31L),
            projection(2L, "DRAFT", "DRAFT", "MISSION_UPDATED", "USER", null),
            projection(3L, "DRAFT", "PENDING_REVIEW", "MISSION_SUBMITTED", "USER", 31L),
            projection(4L, "PENDING_REVIEW", "PUBLISHED", "MISSION_APPROVED", "USER", 41L),
            projection(5L, "PENDING_REVIEW", "DRAFT", "MISSION_REWARD_POLICY_INVALID", "USER", 41L),
            projection(6L, "PUBLISHED", "ENDED", "MISSION_OPERATION_SCHEDULE_CHANGED", "USER", 31L),
            projection(7L, "PUBLISHED", "ENDED", "MISSION_END_TIME_REACHED", "SYSTEM", null)
        ));

        List<MissionHistoryReadResult> histories = missionHistoryReadService.findAll(MISSION_ID);

        assertThat(histories)
            .extracting(MissionHistoryReadResult::action)
            .containsExactly(
                "CREATED",
                "UPDATED",
                "SUBMITTED",
                "APPROVED",
                "REJECTED",
                "ENDED",
                "AUTO_ENDED"
            );
        assertThat(histories)
            .extracting(MissionHistoryReadResult::actorKind)
            .containsExactly("USER", "WITHDRAWN_MEMBER", "USER", "USER", "USER", "USER", "SYSTEM");
        assertThat(histories)
            .extracting(MissionHistoryReadResult::actorUserId)
            .containsExactly(31L, null, 31L, 41L, 41L, 31L, null);
    }

    @Test
    void findAll_mappingMismatch_throwsInternalServerErrorWithoutPartialResult() {
        when(auditEventRepository.findMissionHistoryAuditProjections(MISSION_ID)).thenReturn(List.of(
            projection(1L, null, "DRAFT", "MISSION_CREATED", "USER", 31L),
            projection(2L, "DRAFT", "PUBLISHED", "MISSION_SUBMITTED", "USER", 31L)
        ));

        assertThatThrownBy(() -> missionHistoryReadService.findAll(MISSION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    @ParameterizedTest
    @EnumSource(MissionEarlyEndReasonCode.class)
    void findAll_everyEarlyEndReason_mapsEndedAction(MissionEarlyEndReasonCode reasonCode) {
        when(auditEventRepository.findMissionHistoryAuditProjections(MISSION_ID)).thenReturn(List.of(
            projection(1L, "PUBLISHED", "ENDED", reasonCode.name(), "USER", 31L)
        ));

        List<MissionHistoryReadResult> histories = missionHistoryReadService.findAll(MISSION_ID);

        assertThat(histories)
            .extracting(MissionHistoryReadResult::action)
            .containsExactly("ENDED");
    }

    private MissionHistoryAuditProjection projection(
        Long auditEventId,
        String previousStatus,
        String nextStatus,
        String reasonCode,
        String actorKind,
        Long actorUserId
    ) {
        return new MissionHistoryAuditProjection(
            auditEventId,
            "00000000-0000-0000-0000-000000000638",
            previousStatus,
            nextStatus,
            AuditEventResult.SUCCESS,
            reasonCode,
            actorKind,
            actorUserId,
            RECORDED_AT.plusSeconds(auditEventId)
        );
    }
}
