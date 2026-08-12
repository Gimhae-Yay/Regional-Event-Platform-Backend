package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EndMissionsScheduler {

    private static final Logger log = LoggerFactory.getLogger(EndMissionsScheduler.class);

    private final EndMissionsUseCase endMissionsUseCase;

    public EndMissionsScheduler(EndMissionsUseCase endMissionsUseCase) {
        this.endMissionsUseCase = endMissionsUseCase;
    }

    @Scheduled(
        initialDelayString = "${mission.auto-ending.initial-delay:PT1M}",
        fixedDelayString = "${mission.auto-ending.fixed-delay:PT1M}"
    )
    public void endMissions() {
        UUID requestId = UUID.randomUUID();
        EndingCounts counts = new EndingCounts();

        for (Long missionId : endMissionsUseCase.findAutoEndCandidateIds()) {
            counts.candidateMissionCount++;
            try {
                counts.record(endMissionsUseCase.endBySystem(missionId, requestId));
            } catch (RuntimeException exception) {
                counts.failedMissionCount++;
                log.error(
                    "미션 자동 종료에 실패했습니다. requestId={}, missionId={}",
                    requestId,
                    missionId,
                    exception
                );
            }
        }

        log.info(
            "미션 자동 종료 Scheduler 실행을 완료했습니다. requestId={}, candidateMissionCount={}, "
                + "endedMissionCount={}, skippedMissionCount={}, failedMissionCount={}",
            requestId,
            counts.candidateMissionCount,
            counts.endedMissionCount,
            counts.skippedMissionCount,
            counts.failedMissionCount
        );
    }

    private static class EndingCounts {

        private int candidateMissionCount;
        private int endedMissionCount;
        private int skippedMissionCount;
        private int failedMissionCount;

        private void record(EndMissionSystemResult result) {
            switch (result.status()) {
                case ENDED -> endedMissionCount++;
                case SKIPPED -> skippedMissionCount++;
            }
        }
    }
}
