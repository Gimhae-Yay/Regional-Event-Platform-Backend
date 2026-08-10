package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;

@ExtendWith(MockitoExtension.class)
class StampbookReadServiceTest {

    private static final Long USER_ID = 100L;

    @Mock
    private StampbookRepository stampbookRepository;

    @InjectMocks
    private StampbookReadService stampbookReadService;

    @Test
    void 내_스탬프북_조회는_미시작과_완료_진행도를_응답_상태로_변환한다() {
        Instant publishedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant completedAt = Instant.parse("2026-08-04T00:00:00Z");
        Instant lastEarnedAt = Instant.parse("2026-08-03T00:00:00Z");
        when(stampbookRepository.findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(
            projection(
                101L,
                StampbookStatus.PUBLISHED,
                publishedAt,
                null,
                null,
                0L,
                3L,
                null
            ),
            projection(
                102L,
                StampbookStatus.ENDED,
                publishedAt,
                StampbookProgressStatus.COMPLETED,
                completedAt,
                3L,
                3L,
                lastEarnedAt
            )
        ));

        List<MyStampbookListResult> results = stampbookReadService.findMyStampbooks(USER_ID);

        assertThat(results)
            .extracting(MyStampbookListResult::stampbookId)
            .containsExactly(101L, 102L);
        assertThat(results.getFirst().progress())
            .isEqualTo(new MyStampbookListResult.Progress(
                MyStampbookProgressStatus.NOT_STARTED,
                0L,
                3L,
                null,
                null
            ));
        assertThat(results.get(1).progress())
            .isEqualTo(new MyStampbookListResult.Progress(
                MyStampbookProgressStatus.COMPLETED,
                3L,
                3L,
                completedAt,
                lastEarnedAt
            ));
        verify(stampbookRepository).findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
    }

    @Test
    void 종료된_스탬프북의_진행중_진행도는_정합성_오류로_처리한다() {
        when(stampbookRepository.findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(projection(
            101L,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-01T00:00:00Z"),
            StampbookProgressStatus.IN_PROGRESS,
            null,
            1L,
            3L,
            Instant.parse("2026-08-02T00:00:00Z")
        )));

        assertThatThrownBy(() -> stampbookReadService.findMyStampbooks(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook progress read data is inconsistent");
    }

    private MyStampbookListProjection projection(
        Long stampbookId,
        StampbookStatus stampbookStatus,
        Instant publishedAt,
        StampbookProgressStatus progressStatus,
        Instant completedAt,
        Long earnedCount,
        Long targetCount,
        Instant lastEarnedAt
    ) {
        return new MyStampbookListProjection(
            stampbookId,
            10L,
            stampbookStatus,
            publishedAt,
            progressStatus,
            completedAt,
            earnedCount,
            targetCount,
            lastEarnedAt
        );
    }
}
