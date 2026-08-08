package io.regionevent.regioneventbackend.domain.mission.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.content.entity.Content;

@Entity
@Table(name = "mission_target_content")
public class MissionTargetContent {

    @EmbeddedId
    private MissionTargetContentId id;

    @MapsId("missionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "mission_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_target_content_mission")
    )
    private Mission mission;

    @MapsId("contentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_target_content_content")
    )
    private Content content;

    protected MissionTargetContent() {
    }

    public MissionTargetContent(
        Mission mission,
        Content content
    ) {
        this.mission = requireNotNull(mission, "mission");
        this.content = requireNotNull(content, "content");
        Mission.validateTargetContent(mission, content);
        this.id = new MissionTargetContentId(null, null);
    }

    public MissionTargetContentId getId() {
        return id;
    }

    public Mission getMission() {
        return mission;
    }

    public Content getContent() {
        return content;
    }

    private static <T> T requireNotNull(
        T value,
        String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
