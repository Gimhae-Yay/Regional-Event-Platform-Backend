package io.regionevent.regioneventbackend.domain.content.entity;

import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(name = "content")
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long contentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_region")
    )
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "operator_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_operator")
    )
    private AppUser operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 30)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContentStatus status;

    @Version
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_text", nullable = false, length = 255)
    private String locationText;

    @Column(name = "operating_hours_text", nullable = false, columnDefinition = "TEXT")
    private String operatingHoursText;

    @Column(name = "contact_text", nullable = false, length = 255)
    private String contactText;

    @Column(name = "precautions", nullable = false, columnDefinition = "TEXT")
    private String precautions;

    @Column(name = "age_requirement", nullable = false, length = 255)
    private String ageRequirement;

    @Column(name = "materials", nullable = false, columnDefinition = "TEXT")
    private String materials;

    @Column(name = "cancellation_policy_text", nullable = false, columnDefinition = "TEXT")
    private String cancellationPolicyText;

    @Column(name = "publish_at", nullable = false)
    private Instant publishAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "representative_image_object_id",
        foreignKey = @ForeignKey(name = "fk_content_representative_image_object")
    )
    private ImageObject representativeImageObject;

    @Column(name = "representative_image_assigned_at")
    private Instant representativeImageAssignedAt;

    protected Content() {
    }

    public Content(
        Region region,
        AppUser operator,
        ContentType contentType,
        ContentStatus status,
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        Instant publishAt
    ) {
        this.region = requireNotNull(region, "region");
        this.operator = requireNotNull(operator, "operator");
        this.contentType = requireNotNull(contentType, "contentType");
        this.status = requireNotNull(status, "status");
        this.title = requireNotBlank(title, "title");
        this.description = requireNotBlank(description, "description");
        this.locationText = requireNotBlank(locationText, "locationText");
        this.operatingHoursText = requireNotBlank(operatingHoursText, "operatingHoursText");
        this.contactText = requireNotBlank(contactText, "contactText");
        this.precautions = requireNotBlank(precautions, "precautions");
        this.ageRequirement = requireNotBlank(ageRequirement, "ageRequirement");
        this.materials = requireNotBlank(materials, "materials");
        this.cancellationPolicyText = requireNotBlank(cancellationPolicyText, "cancellationPolicyText");
        this.publishAt = requireNotNull(publishAt, "publishAt");
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

    public void softDelete() {
        if (status != ContentStatus.PENDING && status != ContentStatus.APPROVED) {
            throw new IllegalStateException("only pending or approved content can be soft deleted");
        }
        if (deletedAt != null) {
            throw new IllegalStateException("content is already soft deleted");
        }
        deletedAt = Instant.now();
    }

    public void approve() {
        if (deletedAt != null) {
            throw new IllegalStateException("soft deleted content cannot be approved");
        }
        if (status != ContentStatus.PENDING) {
            throw new IllegalStateException("content status must be PENDING but was " + status);
        }
        status = ContentStatus.APPROVED;
    }

    public void assignRepresentativeImage(ImageObject imageObject, Instant assignedAt) {
        representativeImageObject = requireNotNull(imageObject, "imageObject");
        representativeImageAssignedAt = requireNotNull(assignedAt, "assignedAt");
    }

    public void replaceEditableFields(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        Instant publishAt
    ) {
        this.title = requireNotBlank(title, "title");
        this.description = requireNotBlank(description, "description");
        this.locationText = requireNotBlank(locationText, "locationText");
        this.operatingHoursText = requireNotBlank(operatingHoursText, "operatingHoursText");
        this.contactText = requireNotBlank(contactText, "contactText");
        this.precautions = requireNotBlank(precautions, "precautions");
        this.ageRequirement = requireNotBlank(ageRequirement, "ageRequirement");
        this.materials = requireNotBlank(materials, "materials");
        this.cancellationPolicyText = requireNotBlank(cancellationPolicyText, "cancellationPolicyText");
        this.publishAt = requireNotNull(publishAt, "publishAt");
    }

    public boolean isOwnedBy(Long userId) {
        return operator != null && operator.getUserId().equals(userId);
    }

    public boolean isScopedTo(Long regionId) {
        return region != null && region.getRegionId().equals(regionId);
    }

    public boolean hasRepresentativeImage(Long imageObjectId) {
        return representativeImageObject != null
            && representativeImageObject.getImageObjectId().equals(imageObjectId);
    }

    public Long getContentId() {
        return contentId;
    }

    public Region getRegion() {
        return region;
    }

    public AppUser getOperator() {
        return operator;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocationText() {
        return locationText;
    }

    public String getOperatingHoursText() {
        return operatingHoursText;
    }

    public String getContactText() {
        return contactText;
    }

    public String getPrecautions() {
        return precautions;
    }

    public String getAgeRequirement() {
        return ageRequirement;
    }

    public String getMaterials() {
        return materials;
    }

    public String getCancellationPolicyText() {
        return cancellationPolicyText;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public ImageObject getRepresentativeImageObject() {
        return representativeImageObject;
    }

    public Instant getRepresentativeImageAssignedAt() {
        return representativeImageAssignedAt;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
