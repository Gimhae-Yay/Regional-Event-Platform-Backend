package io.regionevent.regioneventbackend.domain.region.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "region",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_region_region_code",
        columnNames = "region_code"
    )
)
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "region_code", nullable = false, length = 50)
    private String regionCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Region() {
    }

    public Region(
        String regionCode,
        String name,
        boolean isPublic
    ) {
        this.regionCode = regionCode;
        this.name = name;
        this.isPublic = isPublic;
    }

    public static Region createPrivate(
        String regionCode,
        String name
    ) {
        return new Region(regionCode, name, false);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getRegionId() {
        return regionId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getName() {
        return name;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
