package io.regionevent.regioneventbackend.domain.stampbook.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StampbookContentId implements Serializable {

    @Column(name = "stampbook_id")
    private Long stampbookId;

    @Column(name = "content_id")
    private Long contentId;

    protected StampbookContentId() {
    }

    public StampbookContentId(
        Long stampbookId,
        Long contentId
    ) {
        this.stampbookId = stampbookId;
        this.contentId = contentId;
    }

    public Long getStampbookId() {
        return stampbookId;
    }

    public Long getContentId() {
        return contentId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StampbookContentId other)) {
            return false;
        }
        return isEqual(stampbookId, other.stampbookId)
            && isEqual(contentId, other.contentId);
    }

    @Override
    public int hashCode() {
        int result = stampbookId == null ? 0 : stampbookId.hashCode();
        return 31 * result + (contentId == null ? 0 : contentId.hashCode());
    }

    private static boolean isEqual(
        Object first,
        Object second
    ) {
        return first == null ? second == null : first.equals(second);
    }
}
