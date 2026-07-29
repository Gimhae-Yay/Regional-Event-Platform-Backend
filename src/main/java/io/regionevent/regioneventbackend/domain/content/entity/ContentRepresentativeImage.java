package io.regionevent.regioneventbackend.domain.content.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

@Entity
@Table(
    name = "content_representative_image",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_content_representative_image_object",
        columnNames = "image_object_id"
    )
)
public class ContentRepresentativeImage {

    @Id
    @Column(name = "content_id")
    private Long contentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_representative_image_content")
    )
    private Content content;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "image_object_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_content_representative_image_object")
    )
    private ImageObject imageObject;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected ContentRepresentativeImage() {
    }

    public ContentRepresentativeImage(
        Content content,
        ImageObject imageObject,
        Instant assignedAt
    ) {
        this.content = requireNotNull(content, "content");
        this.imageObject = requireNotNull(imageObject, "imageObject");
        this.assignedAt = requireNotNull(assignedAt, "assignedAt");
    }

    public Long getContentId() {
        return contentId;
    }

    public Content getContent() {
        return content;
    }

    public ImageObject getImageObject() {
        return imageObject;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
