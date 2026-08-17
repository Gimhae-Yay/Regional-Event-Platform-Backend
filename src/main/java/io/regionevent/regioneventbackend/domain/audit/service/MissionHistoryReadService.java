package io.regionevent.regioneventbackend.domain.audit.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.MissionHistoryAuditProjection;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionEarlyEndReasonCode;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class MissionHistoryReadService {

    private static final Logger log = LoggerFactory.getLogger(MissionHistoryReadService.class);

    private static final String USER = "USER";
    private static final String SYSTEM = "SYSTEM";
    private static final String WITHDRAWN_MEMBER = "WITHDRAWN_MEMBER";
    private static final String MAPPING_MISMATCH = "MISSION_HISTORY_MAPPING_MISMATCH";
    private static final Set<String> REJECTION_REASON_CODES = Set.of(
        "MISSION_INFORMATION_INCOMPLETE",
        "MISSION_CONDITION_INVALID",
        "MISSION_TARGET_CONTENT_INVALID",
        "MISSION_REWARD_POLICY_INVALID",
        "MISSION_SCHEDULE_INVALID"
    );
    private final AuditEventRepository auditEventRepository;

    public MissionHistoryReadService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public List<MissionHistoryReadResult> findAll(Long missionId) {
        return auditEventRepository.findMissionHistoryAuditProjections(missionId).stream()
            .map(projection -> toResult(missionId, projection))
            .toList();
    }

    private MissionHistoryReadResult toResult(
        Long missionId,
        MissionHistoryAuditProjection projection
    ) {
        String action = findAction(projection);
        if (action == null) {
            log.error(
                "Mission history mapping mismatch. requestId={}, missionId={}, auditEventId={}, consistencyErrorCode={}",
                projection.requestId(),
                missionId,
                projection.auditEventId(),
                MAPPING_MISMATCH
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        Actor actor = toActor(projection);
        return new MissionHistoryReadResult(
            projection.auditEventId(),
            action,
            projection.previousState(),
            projection.nextState(),
            projection.result(),
            projection.reasonCode(),
            actor.kind(),
            actor.userId(),
            projection.occurredAt()
        );
    }

    private String findAction(MissionHistoryAuditProjection projection) {
        if (matches(projection, null, "DRAFT", USER, "MISSION_CREATED")) {
            return "CREATED";
        }
        if (matches(projection, "DRAFT", "DRAFT", USER, "MISSION_UPDATED")) {
            return "UPDATED";
        }
        if (matches(projection, "DRAFT", "PENDING_REVIEW", USER, "MISSION_SUBMITTED")) {
            return "SUBMITTED";
        }
        if (matches(projection, "PENDING_REVIEW", "PUBLISHED", USER, "MISSION_APPROVED")) {
            return "APPROVED";
        }
        if (matchesStateAndActor(projection, "PENDING_REVIEW", "DRAFT", USER)
            && REJECTION_REASON_CODES.contains(projection.reasonCode())) {
            return "REJECTED";
        }
        if (matchesStateAndActor(projection, "PUBLISHED", "ENDED", USER)
            && MissionEarlyEndReasonCode.isSupported(projection.reasonCode())) {
            return "ENDED";
        }
        if (matches(projection, "PUBLISHED", "ENDED", SYSTEM, "MISSION_END_TIME_REACHED")) {
            return "AUTO_ENDED";
        }
        return null;
    }

    private boolean matches(
        MissionHistoryAuditProjection projection,
        String previousState,
        String nextState,
        String actorKind,
        String reasonCode
    ) {
        return matchesStateAndActor(projection, previousState, nextState, actorKind)
            && sameValue(reasonCode, projection.reasonCode());
    }

    private boolean matchesStateAndActor(
        MissionHistoryAuditProjection projection,
        String previousState,
        String nextState,
        String actorKind
    ) {
        return sameValue(previousState, projection.previousState())
            && sameValue(nextState, projection.nextState())
            && sameValue(actorKind, projection.actorKind());
    }

    private boolean sameValue(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        return expected.equals(actual);
    }

    private Actor toActor(MissionHistoryAuditProjection projection) {
        if (SYSTEM.equals(projection.actorKind())) {
            return new Actor(SYSTEM, null);
        }
        if (projection.actorUserId() == null) {
            return new Actor(WITHDRAWN_MEMBER, null);
        }
        return new Actor(USER, projection.actorUserId());
    }

    private record Actor(
        String kind,
        Long userId
    ) {
    }
}
