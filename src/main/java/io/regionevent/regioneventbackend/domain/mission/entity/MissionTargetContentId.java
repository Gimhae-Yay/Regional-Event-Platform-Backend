package io.regionevent.regioneventbackend.domain.mission.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MissionTargetContentId implements Serializable {

    @Column(name = "mission_id")
    private Long missionId;

    @Column(name = "content_id")
    private Long contentId;

    protected MissionTargetContentId() {
    }

    public MissionTargetContentId(
        Long missionId,
        Long contentId
    ) {
        this.missionId = missionId;
        this.contentId = contentId;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getContentId() {
        return contentId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MissionTargetContentId other)) {
            return false;
        }
        return Objects.equals(missionId, other.missionId)
            && Objects.equals(contentId, other.contentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, contentId);
    }
}
