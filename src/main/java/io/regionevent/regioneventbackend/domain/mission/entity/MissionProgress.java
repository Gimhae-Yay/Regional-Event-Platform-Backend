package io.regionevent.regioneventbackend.domain.mission.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@Entity
@Table(name = "mission_progress")
public class MissionProgress {

    @EmbeddedId
    private MissionProgressId id;

    @MapsId("missionParticipationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "mission_participation_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_progress_participation")
    )
    private MissionParticipation missionParticipation;

    @MapsId("visitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "visit_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_progress_visit")
    )
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_progress_content")
    )
    private Content content;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected MissionProgress() {
    }

    public MissionProgress(
        MissionParticipation missionParticipation,
        Visit visit,
        Content content,
        Instant recordedAt
    ) {
        this.missionParticipation = requireNotNull(missionParticipation, "missionParticipation");
        this.visit = requireNotNull(visit, "visit");
        this.content = requireNotNull(content, "content");
        this.recordedAt = requireNotNull(recordedAt, "recordedAt");
        validateVisitContent(visit, content);
        this.id = new MissionProgressId(null, null);
    }

    public MissionProgressId getId() {
        return id;
    }

    public MissionParticipation getMissionParticipation() {
        return missionParticipation;
    }

    public Visit getVisit() {
        return visit;
    }

    public Content getContent() {
        return content;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    private static void validateVisitContent(
        Visit visit,
        Content content
    ) {
        Content visitContent = visit.getContent();
        if (visitContent == content) {
            return;
        }
        Long visitContentId = visitContent.getContentId();
        Long contentId = content.getContentId();
        if (visitContentId == null || !visitContentId.equals(contentId)) {
            throw new IllegalArgumentException("content must match visit content");
        }
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
