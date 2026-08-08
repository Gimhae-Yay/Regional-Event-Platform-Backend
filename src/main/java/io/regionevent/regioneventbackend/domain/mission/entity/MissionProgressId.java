package io.regionevent.regioneventbackend.domain.mission.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MissionProgressId implements Serializable {

    @Column(name = "mission_participation_id")
    private Long missionParticipationId;

    @Column(name = "visit_id")
    private Long visitId;

    protected MissionProgressId() {
    }

    public MissionProgressId(
        Long missionParticipationId,
        Long visitId
    ) {
        this.missionParticipationId = missionParticipationId;
        this.visitId = visitId;
    }

    public Long getMissionParticipationId() {
        return missionParticipationId;
    }

    public Long getVisitId() {
        return visitId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MissionProgressId other)) {
            return false;
        }
        return Objects.equals(missionParticipationId, other.missionParticipationId)
            && Objects.equals(visitId, other.visitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionParticipationId, visitId);
    }
}
