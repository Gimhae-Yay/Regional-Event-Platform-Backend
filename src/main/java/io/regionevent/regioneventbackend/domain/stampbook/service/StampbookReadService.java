package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookDetailProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampEarningProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.OperatorStampbookListProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.PendingRegionAdminStampbookProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class StampbookReadService {

    private final StampbookRepository stampbookRepository;

    public StampbookReadService(StampbookRepository stampbookRepository) {
        this.stampbookRepository = stampbookRepository;
    }

    public List<MyStampbookListResult> findMyStampbooks(Long userId) {
        validateUserId(userId);

        return stampbookRepository.findMyStampbookListProjections(
                userId,
                StampbookStatus.PUBLISHED,
                StampbookStatus.ENDED
            )
            .stream()
            .map(projection -> toResult(projection, userId))
            .toList();
    }

    public List<PendingRegionAdminStampbookResult> findPendingRegionAdminStampbooks(Long regionId) {
        validateRegionId(regionId);

        return stampbookRepository.findPendingRegionAdminStampbookProjections(
                regionId,
                StampbookStatus.PENDING_REVIEW,
                AuditEventTargetType.STAMPBOOK,
                AuditEventResult.SUCCESS,
                StampbookStatus.DRAFT.name(),
                StampbookStatus.PENDING_REVIEW.name()
            )
            .stream()
            .map(this::toPendingRegionAdminResult)
            .toList();
    }

    public List<OperatorStampbookListResult> findOperatorStampbooks(
        Long operatorUserId,
        Long regionId
    ) {
        validateUserId(operatorUserId);
        validateRegionId(regionId);

        return stampbookRepository.findOperatorStampbookListProjections(operatorUserId, regionId)
            .stream()
            .map(projection -> toOperatorStampbookListResult(projection, regionId))
            .toList();
    }

    public MyStampbookDetailResult findMyStampbookDetail(
        Long userId,
        Long stampbookId
    ) {
        validateUserId(userId);
        validateStampbookId(stampbookId);

        List<MyStampbookDetailProjection> projections = stampbookRepository.findMyStampbookDetailProjections(
            userId,
            stampbookId,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
        if (projections.isEmpty()) {
            throwDetailAccessException(stampbookId);
        }
        return toDetailResult(projections, userId);
    }

    public MyStampEarningsResult findMyStampEarnings(
        Long userId,
        Long stampbookId
    ) {
        validateUserId(userId);
        validateStampbookId(stampbookId);

        List<MyStampEarningProjection> projections = stampbookRepository.findMyStampEarningProjections(
            userId,
            stampbookId,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
        if (projections.isEmpty()) {
            throwDetailAccessException(stampbookId);
        }

        MyStampEarningProjection firstProjection = projections.getFirst();
        validateEarningTarget(firstProjection, userId);
        if (firstProjection.stampEarnId() == null) {
            validateEmptyEarnings(projections, firstProjection);
            return new MyStampEarningsResult(firstProjection.stampbookId(), List.of());
        }

        List<MyStampEarningsResult.Earning> earnings = projections.stream()
            .map(projection -> toEarning(projection, firstProjection, userId))
            .toList();
        validateEarningOrder(earnings);

        return new MyStampEarningsResult(firstProjection.stampbookId(), earnings);
    }

    private MyStampbookListResult toResult(
        MyStampbookListProjection projection,
        Long userId
    ) {
        validateStampbook(projection);

        return new MyStampbookListResult(
            projection.stampbookId(),
            projection.title(),
            projection.regionId(),
            projection.stampbookStatus(),
            projection.publishedAt(),
            toProgress(projection, userId)
        );
    }

    private PendingRegionAdminStampbookResult toPendingRegionAdminResult(
        PendingRegionAdminStampbookProjection projection
    ) {
        if (projection == null
            || projection.stampbookId() == null
            || projection.stampbookId() <= 0
            || projection.regionId() == null
            || projection.regionId() <= 0
            || projection.status() != StampbookStatus.PENDING_REVIEW
            || projection.targetCount() == null
            || projection.targetCount() <= 0
            || projection.targetCount() > Integer.MAX_VALUE
            || projection.rewardCouponPolicyId() == null
            || projection.rewardCouponPolicyId() <= 0
            || projection.requestedAt() == null) {
            throw new IllegalStateException("stampbook review read data is inconsistent");
        }

        return new PendingRegionAdminStampbookResult(
            projection.stampbookId(),
            projection.regionId(),
            projection.status(),
            projection.targetCount().intValue(),
            projection.rewardCouponPolicyId(),
            projection.requestedAt()
        );
    }

    private OperatorStampbookListResult toOperatorStampbookListResult(
        OperatorStampbookListProjection projection,
        Long regionId
    ) {
        if (projection == null
            || projection.stampbookId() == null
            || projection.stampbookId() <= 0
            || projection.title() == null
            || projection.title().isBlank()
            || !isSame(projection.regionId(), regionId)
            || projection.status() == null
            || projection.targetCount() == null
            || projection.targetCount() <= 0
            || projection.targetCount() > Integer.MAX_VALUE
            || !isSame(projection.minimumTargetContentRegionId(), regionId)
            || !isSame(projection.maximumTargetContentRegionId(), regionId)
            || projection.rewardCouponPolicyId() == null
            || projection.rewardCouponPolicyId() <= 0
            || !isSame(projection.rewardCouponPolicyRegionId(), regionId)
            || !hasValidTimestamps(projection.status(), projection.publishedAt(), projection.endedAt())) {
            throw new IllegalStateException("operator stampbook read data is inconsistent");
        }

        return new OperatorStampbookListResult(
            projection.stampbookId(),
            projection.title(),
            projection.regionId(),
            projection.status(),
            projection.targetCount().intValue(),
            projection.rewardCouponPolicyId(),
            projection.publishedAt(),
            projection.endedAt()
        );
    }

    private boolean hasValidTimestamps(
        StampbookStatus status,
        Instant publishedAt,
        Instant endedAt
    ) {
        return switch (status) {
            case DRAFT, PENDING_REVIEW -> publishedAt == null && endedAt == null;
            case PUBLISHED -> publishedAt != null && endedAt == null;
            case ENDED -> publishedAt != null && endedAt != null;
        };
    }

    private MyStampEarningsResult.Earning toEarning(
        MyStampEarningProjection projection,
        MyStampEarningProjection firstProjection,
        Long userId
    ) {
        validateEarningTarget(projection, userId);
        if (!isSame(firstProjection.stampbookId(), projection.stampbookId())
            || firstProjection.stampbookStatus() != projection.stampbookStatus()
            || !isSame(firstProjection.stampbookProgressId(), projection.stampbookProgressId())
            || !isSame(firstProjection.progressUserId(), projection.progressUserId())
            || projection.stampEarnId() == null
            || projection.stampEarnId() <= 0
            || projection.earnedAt() == null
            || projection.visitId() == null
            || projection.visitId() <= 0
            || !isSame(projection.progressUserId(), projection.visitUserId())
            || !isSame(projection.visitContentId(), projection.contentId())
            || !isSame(projection.contentId(), projection.targetContentId())
            || projection.visitedAt() == null
            || projection.contentId() == null
            || projection.contentId() <= 0
            || projection.contentTitle() == null
            || projection.contentTitle().isBlank()) {
            throw new IllegalStateException("stamp earning read data is inconsistent");
        }

        return new MyStampEarningsResult.Earning(
            projection.stampEarnId(),
            projection.visitId(),
            projection.contentId(),
            projection.contentTitle(),
            projection.visitedAt(),
            projection.earnedAt()
        );
    }

    private void validateEarningTarget(
        MyStampEarningProjection projection,
        Long userId
    ) {
        if (projection == null
            || projection.stampbookId() == null
            || projection.stampbookId() <= 0
            || projection.stampbookStatus() != StampbookStatus.PUBLISHED
                && projection.stampbookStatus() != StampbookStatus.ENDED
            || projection.stampbookProgressId() == null
                && projection.stampbookStatus() != StampbookStatus.PUBLISHED
            || projection.stampbookProgressId() == null
                && projection.progressUserId() != null
            || projection.stampbookProgressId() != null
                && (projection.stampbookProgressId() <= 0
                    || projection.progressUserId() == null
                    || projection.progressUserId() <= 0
                    || !isSame(projection.progressUserId(), userId))) {
            throw new IllegalStateException("stamp earning read data is inconsistent");
        }
    }

    private void validateEmptyEarnings(
        List<MyStampEarningProjection> projections,
        MyStampEarningProjection firstProjection
    ) {
        if (projections.size() != 1
            || firstProjection.visitId() != null
            || firstProjection.visitUserId() != null
            || firstProjection.visitContentId() != null
            || firstProjection.visitedAt() != null
            || firstProjection.contentId() != null
            || firstProjection.contentTitle() != null
            || firstProjection.earnedAt() != null) {
            throw new IllegalStateException("stamp earning read data is inconsistent");
        }
    }

    private void validateEarningOrder(List<MyStampEarningsResult.Earning> earnings) {
        MyStampEarningsResult.Earning previous = null;
        for (MyStampEarningsResult.Earning earning : earnings) {
            if (previous != null
                && (previous.earnedAt().isBefore(earning.earnedAt())
                    || previous.earnedAt().equals(earning.earnedAt())
                        && previous.stampEarnId() <= earning.stampEarnId())) {
                throw new IllegalStateException("stamp earning read data is inconsistent");
            }
            previous = earning;
        }
    }

    private MyStampbookDetailResult toDetailResult(
        List<MyStampbookDetailProjection> projections,
        Long userId
    ) {
        MyStampbookDetailProjection firstProjection = projections.getFirst();
        validateDetailStampbook(firstProjection);

        List<MyStampbookDetailResult.TargetContent> targetContents = projections.stream()
            .map(this::toTargetContent)
            .toList();
        validateDetailProjections(projections, firstProjection);

        long earnedCount = targetContents.stream()
            .filter(MyStampbookDetailResult.TargetContent::earned)
            .count();
        long targetCount = targetContents.size();

        return new MyStampbookDetailResult(
            firstProjection.stampbookId(),
            firstProjection.title(),
            firstProjection.regionId(),
            firstProjection.stampbookStatus(),
            firstProjection.publishedAt(),
            firstProjection.endedAt(),
            targetContents,
            toDetailProgress(firstProjection, earnedCount, targetCount, userId)
        );
    }

    private MyStampbookDetailResult.TargetContent toTargetContent(
        MyStampbookDetailProjection projection
    ) {
        validateDetailStampbook(projection);
        return new MyStampbookDetailResult.TargetContent(
            projection.contentId(),
            projection.contentTitle(),
            projection.earnedAt() != null,
            projection.earnedAt()
        );
    }

    private MyStampbookDetailResult.Progress toDetailProgress(
        MyStampbookDetailProjection projection,
        long earnedCount,
        long targetCount,
        Long userId
    ) {
        StampbookProgressStatus progressStatus = projection.progressStatus();

        if (progressStatus == null) {
            if (projection.stampbookStatus() != StampbookStatus.PUBLISHED
                || earnedCount != 0
                || projection.completedAt() != null
                || projection.stampbookProgressId() != null
                || projection.progressUserId() != null) {
                throw new IllegalStateException("stampbook progress read data is inconsistent");
            }
            toCompletionReward(
                null,
                projection.stampbookRewardGrantId(),
                projection.completionRewardCouponPolicyId(),
                projection.stampbookRewardCouponPolicyId()
            );
            return new MyStampbookDetailResult.Progress(
                MyStampbookProgressStatus.NOT_STARTED,
                earnedCount,
                targetCount,
                null,
                null
            );
        }

        validateProgressOwner(
            projection.stampbookProgressId(),
            projection.progressUserId(),
            userId
        );
        validateProgress(
            projection.stampbookStatus(),
            progressStatus,
            projection.completedAt(),
            earnedCount,
            targetCount
        );
        return new MyStampbookDetailResult.Progress(
            MyStampbookProgressStatus.valueOf(progressStatus.name()),
            earnedCount,
            targetCount,
            projection.completedAt(),
            toCompletionReward(
                progressStatus,
                projection.stampbookRewardGrantId(),
                projection.completionRewardCouponPolicyId(),
                projection.stampbookRewardCouponPolicyId()
            )
        );
    }

    private MyStampbookListResult.Progress toProgress(
        MyStampbookListProjection projection,
        Long userId
    ) {
        long earnedCount = requireNonNegative(projection.earnedCount(), "earnedCount");
        long targetCount = requirePositive(projection.targetCount(), "targetCount");
        StampbookProgressStatus progressStatus = projection.progressStatus();

        if (progressStatus == null) {
            validateNotStartedProgress(projection, earnedCount);
            return new MyStampbookListResult.Progress(
                MyStampbookProgressStatus.NOT_STARTED,
                earnedCount,
                targetCount,
                null,
                null,
                null
            );
        }

        validateProgressOwner(
            projection.stampbookProgressId(),
            projection.progressUserId(),
            userId
        );
        validateProgress(projection, earnedCount, targetCount);
        return new MyStampbookListResult.Progress(
            MyStampbookProgressStatus.valueOf(progressStatus.name()),
            earnedCount,
            targetCount,
            projection.completedAt(),
            projection.lastEarnedAt(),
            toCompletionReward(
                progressStatus,
                projection.stampbookRewardGrantId(),
                projection.completionRewardCouponPolicyId(),
                projection.stampbookRewardCouponPolicyId()
            )
        );
    }

    private void validateStampbook(MyStampbookListProjection projection) {
        if (projection == null
            || projection.stampbookId() == null
            || projection.stampbookId() <= 0
            || projection.title() == null
            || projection.title().isBlank()
            || projection.regionId() == null
            || projection.regionId() <= 0
            || projection.publishedAt() == null
            || projection.stampbookStatus() != StampbookStatus.PUBLISHED
                && projection.stampbookStatus() != StampbookStatus.ENDED) {
            throw new IllegalStateException("stampbook read data is inconsistent");
        }
    }

    private void validateNotStartedProgress(
        MyStampbookListProjection projection,
        long earnedCount
    ) {
        if (earnedCount != 0
            || projection.completedAt() != null
            || projection.lastEarnedAt() != null
            || projection.stampbookProgressId() != null
            || projection.progressUserId() != null
            || projection.stampbookRewardGrantId() != null
            || projection.completionRewardCouponPolicyId() != null
            || projection.stampbookRewardCouponPolicyId() == null
            || projection.stampbookRewardCouponPolicyId() <= 0) {
            throw new IllegalStateException("stampbook progress read data is inconsistent");
        }
    }

    private void validateProgress(
        MyStampbookListProjection projection,
        long earnedCount,
        long targetCount
    ) {
        if (earnedCount > targetCount
            || projection.progressStatus() == StampbookProgressStatus.IN_PROGRESS
                && (projection.stampbookStatus() != StampbookStatus.PUBLISHED
                    || earnedCount >= targetCount)
            || projection.progressStatus() == StampbookProgressStatus.COMPLETED
                && earnedCount != targetCount
            || projection.progressStatus() == StampbookProgressStatus.ENDED_INCOMPLETE
                && (projection.stampbookStatus() != StampbookStatus.ENDED
                    || earnedCount >= targetCount)
            || projection.progressStatus() == StampbookProgressStatus.COMPLETED
                && projection.completedAt() == null
            || projection.progressStatus() != StampbookProgressStatus.COMPLETED
                && projection.completedAt() != null) {
            throw new IllegalStateException("stampbook progress read data is inconsistent");
        }
    }

    private void validateDetailStampbook(MyStampbookDetailProjection projection) {
        if (projection == null
            || projection.stampbookId() == null
            || projection.stampbookId() <= 0
            || projection.title() == null
            || projection.title().isBlank()
            || projection.regionId() == null
            || projection.regionId() <= 0
            || projection.contentId() == null
            || projection.contentId() <= 0
            || projection.contentTitle() == null
            || projection.contentTitle().isBlank()
            || projection.publishedAt() == null
            || projection.stampbookStatus() != StampbookStatus.PUBLISHED
                && projection.stampbookStatus() != StampbookStatus.ENDED
            || projection.stampbookStatus() == StampbookStatus.PUBLISHED
                && projection.endedAt() != null
            || projection.stampbookStatus() == StampbookStatus.ENDED
                && projection.endedAt() == null) {
            throw new IllegalStateException("stampbook read data is inconsistent");
        }
    }

    private void validateDetailProjections(
        List<MyStampbookDetailProjection> projections,
        MyStampbookDetailProjection firstProjection
    ) {
        Long previousContentId = null;
        for (MyStampbookDetailProjection projection : projections) {
            if (!isSame(firstProjection.stampbookId(), projection.stampbookId())
                || !isSame(firstProjection.title(), projection.title())
                || !isSame(firstProjection.regionId(), projection.regionId())
                || firstProjection.stampbookStatus() != projection.stampbookStatus()
                || !isSame(firstProjection.publishedAt(), projection.publishedAt())
                || !isSame(firstProjection.endedAt(), projection.endedAt())
                || !isSame(firstProjection.stampbookProgressId(), projection.stampbookProgressId())
                || !isSame(firstProjection.progressUserId(), projection.progressUserId())
                || firstProjection.progressStatus() != projection.progressStatus()
                || !isSame(firstProjection.completedAt(), projection.completedAt())
                || !isSame(firstProjection.stampbookRewardGrantId(), projection.stampbookRewardGrantId())
                || !isSame(
                    firstProjection.completionRewardCouponPolicyId(),
                    projection.completionRewardCouponPolicyId()
                )
                || !isSame(
                    firstProjection.stampbookRewardCouponPolicyId(),
                    projection.stampbookRewardCouponPolicyId()
                )
                || previousContentId != null && previousContentId >= projection.contentId()) {
                throw new IllegalStateException("stampbook read data is inconsistent");
            }
            previousContentId = projection.contentId();
        }
    }

    private void validateProgress(
        StampbookStatus stampbookStatus,
        StampbookProgressStatus progressStatus,
        Instant completedAt,
        long earnedCount,
        long targetCount
    ) {
        if (earnedCount > targetCount
            || progressStatus == StampbookProgressStatus.IN_PROGRESS
                && (stampbookStatus != StampbookStatus.PUBLISHED || earnedCount >= targetCount)
            || progressStatus == StampbookProgressStatus.COMPLETED && earnedCount != targetCount
            || progressStatus == StampbookProgressStatus.ENDED_INCOMPLETE
                && (stampbookStatus != StampbookStatus.ENDED || earnedCount >= targetCount)
            || progressStatus == StampbookProgressStatus.COMPLETED && completedAt == null
            || progressStatus != StampbookProgressStatus.COMPLETED && completedAt != null) {
            throw new IllegalStateException("stampbook progress read data is inconsistent");
        }
    }

    private StampbookCompletionReward toCompletionReward(
        StampbookProgressStatus progressStatus,
        Long stampbookRewardGrantId,
        Long completionRewardCouponPolicyId,
        Long stampbookRewardCouponPolicyId
    ) {
        if (progressStatus != StampbookProgressStatus.COMPLETED) {
            if (stampbookRewardGrantId != null
                || completionRewardCouponPolicyId != null
                || stampbookRewardCouponPolicyId == null
                || stampbookRewardCouponPolicyId <= 0) {
                throw new IllegalStateException("stampbook completion reward read data is inconsistent");
            }
            return null;
        }

        if (stampbookRewardGrantId == null
            || stampbookRewardGrantId <= 0
            || completionRewardCouponPolicyId == null
            || completionRewardCouponPolicyId <= 0
            || stampbookRewardCouponPolicyId == null
            || stampbookRewardCouponPolicyId <= 0
            || !isSame(completionRewardCouponPolicyId, stampbookRewardCouponPolicyId)) {
            throw new IllegalStateException("stampbook completion reward read data is inconsistent");
        }
        return new StampbookCompletionReward(
            completionRewardCouponPolicyId,
            stampbookRewardGrantId
        );
    }

    private void validateProgressOwner(
        Long stampbookProgressId,
        Long progressUserId,
        Long userId
    ) {
        if (stampbookProgressId == null
            || stampbookProgressId <= 0
            || progressUserId == null
            || progressUserId <= 0
            || !isSame(progressUserId, userId)) {
            throw new IllegalStateException("stampbook progress read data is inconsistent");
        }
    }

    private void throwDetailAccessException(Long stampbookId) {
        if (!stampbookRepository.existsById(stampbookId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private boolean isSame(
        Object expected,
        Object actual
    ) {
        return expected == actual || expected != null && expected.equals(actual);
    }

    private long requireNonNegative(
        Long value,
        String fieldName
    ) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + " must be non-negative");
        }
        return value;
    }

    private long requirePositive(
        Long value,
        String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(fieldName + " must be positive");
        }
        return value;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private void validateRegionId(Long regionId) {
        if (regionId == null || regionId <= 0) {
            throw new IllegalArgumentException("regionId must be positive");
        }
    }

    private void validateStampbookId(Long stampbookId) {
        if (stampbookId == null || stampbookId <= 0) {
            throw new IllegalArgumentException("stampbookId must be positive");
        }
    }
}
