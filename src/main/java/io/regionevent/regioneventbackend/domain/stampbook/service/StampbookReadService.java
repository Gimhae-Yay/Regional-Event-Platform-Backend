package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;

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
            .map(this::toResult)
            .toList();
    }

    private MyStampbookListResult toResult(MyStampbookListProjection projection) {
        validateStampbook(projection);

        return new MyStampbookListResult(
            projection.stampbookId(),
            projection.regionId(),
            projection.stampbookStatus(),
            projection.publishedAt(),
            toProgress(projection)
        );
    }

    private MyStampbookListResult.Progress toProgress(MyStampbookListProjection projection) {
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
                null
            );
        }

        validateProgress(projection, earnedCount, targetCount);
        return new MyStampbookListResult.Progress(
            MyStampbookProgressStatus.valueOf(progressStatus.name()),
            earnedCount,
            targetCount,
            projection.completedAt(),
            projection.lastEarnedAt()
        );
    }

    private void validateStampbook(MyStampbookListProjection projection) {
        if (projection == null
            || projection.stampbookId() == null
            || projection.stampbookId() <= 0
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
            || projection.lastEarnedAt() != null) {
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
}
