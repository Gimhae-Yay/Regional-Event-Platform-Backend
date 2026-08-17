package io.regionevent.regioneventbackend.domain.mission.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "mission_participation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mission_participation_mission_user",
        columnNames = {"mission_id", "user_id"}
    ),
    check = {
        @CheckConstraint(
            name = "ck_mission_participation_status",
            constraint = "status REGEXP '^(IN_PROGRESS|COMPLETED|ENDED_INCOMPLETE)$'"
        ),
        @CheckConstraint(
            name = "ck_mission_participation_status_completed_at",
            constraint = """
                CASE
                    WHEN status = 'IN_PROGRESS' AND completed_at IS NULL THEN 1
                    WHEN status = 'COMPLETED' AND completed_at IS NOT NULL THEN 1
                    WHEN status = 'ENDED_INCOMPLETE' AND completed_at IS NULL THEN 1
                    ELSE 0
                END = 1
                """
        )
    }
)
public class MissionParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_participation_id")
    private Long missionParticipationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "mission_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_participation_mission")
    )
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_mission_participation_user")
    )
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MissionParticipationStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MissionParticipation() {
    }

    public MissionParticipation(
        Mission mission,
        AppUser user,
        Instant joinedAt
    ) {
        this.mission = requireNotNull(mission, "mission");
        this.user = requireNotNull(user, "user");
        this.joinedAt = requireNotNull(joinedAt, "joinedAt");
        this.status = MissionParticipationStatus.IN_PROGRESS;
    }

    public Long getMissionParticipationId() {
        return missionParticipationId;
    }

    public Mission getMission() {
        return mission;
    }

    public AppUser getUser() {
        return user;
    }

    public MissionParticipationStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void complete(Instant completedAt) {
        validateStatus(MissionParticipationStatus.IN_PROGRESS);
        this.completedAt = requireNotNull(completedAt, "completedAt");
        status = MissionParticipationStatus.COMPLETED;
    }

    public void endIncomplete() {
        validateStatus(MissionParticipationStatus.IN_PROGRESS);
        status = MissionParticipationStatus.ENDED_INCOMPLETE;
    }

    private void validateStatus(MissionParticipationStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("mission participation status cannot be changed");
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
