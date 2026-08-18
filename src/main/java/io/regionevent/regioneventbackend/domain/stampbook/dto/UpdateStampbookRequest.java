package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.UniqueElements;

public class UpdateStampbookRequest {

    @Size(min = 1, max = 100)
    private String title;

    @UniqueElements
    private List<
        @NotBlank
        @Pattern(regexp = "^[1-9][0-9]*$")
        String
    > contentIds;

    @Pattern(regexp = "^[1-9][0-9]*$")
    private String rewardCouponPolicyId;

    @NotBlank
    @Size(max = 500)
    private String reason;

    private boolean contentIdsProvided;
    private boolean rewardCouponPolicyIdProvided;
    private boolean titleProvided;

    public String title() {
        return title;
    }

    public List<String> contentIds() {
        return contentIds;
    }

    public String rewardCouponPolicyId() {
        return rewardCouponPolicyId;
    }

    public String reason() {
        return reason;
    }

    public void setContentIds(List<String> contentIds) {
        this.contentIds = contentIds == null ? null : List.copyOf(contentIds);
        contentIdsProvided = true;
    }

    public void setTitle(String title) {
        this.title = title == null ? null : title.strip();
        titleProvided = true;
    }

    public void setRewardCouponPolicyId(String rewardCouponPolicyId) {
        this.rewardCouponPolicyId = rewardCouponPolicyId;
        rewardCouponPolicyIdProvided = true;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }

    @AssertTrue
    public boolean hasValidUpdateFields() {
        return (titleProvided || contentIdsProvided || rewardCouponPolicyIdProvided)
            && (!titleProvided || title != null)
            && (!contentIdsProvided || contentIds != null && !contentIds.isEmpty())
            && (!rewardCouponPolicyIdProvided || rewardCouponPolicyId != null);
    }
}
