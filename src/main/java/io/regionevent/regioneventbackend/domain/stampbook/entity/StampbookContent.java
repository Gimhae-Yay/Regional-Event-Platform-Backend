package io.regionevent.regioneventbackend.domain.stampbook.entity;

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
@Table(name = "stampbook_content")
public class StampbookContent {

    @EmbeddedId
    private StampbookContentId id;

    @MapsId("stampbookId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stampbook_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stampbook_content_stampbook")
    )
    private Stampbook stampbook;

    @MapsId("contentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stampbook_content_content")
    )
    private Content content;

    protected StampbookContent() {
    }

    public StampbookContent(
        Stampbook stampbook,
        Content content
    ) {
        this.id = new StampbookContentId(null, null);
        this.stampbook = requireNotNull(stampbook, "stampbook");
        this.content = requireNotNull(content, "content");
    }

    public StampbookContentId getId() {
        return id;
    }

    public Stampbook getStampbook() {
        return stampbook;
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
