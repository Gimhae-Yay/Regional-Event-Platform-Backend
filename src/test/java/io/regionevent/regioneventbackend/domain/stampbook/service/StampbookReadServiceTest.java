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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookDetailProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampEarningProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.PendingRegionAdminStampbookProjection;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

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
                null,
                null
            ));
        assertThat(results.get(1).progress())
            .isEqualTo(new MyStampbookListResult.Progress(
                MyStampbookProgressStatus.COMPLETED,
                3L,
                3L,
                completedAt,
                lastEarnedAt,
                new StampbookCompletionReward(301L, 901L)
            ));
        verify(stampbookRepository).findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
    }

    @Test
    void 내_스탬프북_목록_조회는_완료_보상_정책이_스탬프북_보상_정책과_다르면_정합성_오류로_처리한다() {
        when(stampbookRepository.findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(new MyStampbookListProjection(
            101L,
            10L,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-01T00:00:00Z"),
            201L,
            USER_ID,
            StampbookProgressStatus.COMPLETED,
            Instant.parse("2026-08-04T00:00:00Z"),
            1L,
            1L,
            Instant.parse("2026-08-04T00:00:00Z"),
            901L,
            302L,
            301L
        )));

        assertThatThrownBy(() -> stampbookReadService.findMyStampbooks(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook completion reward read data is inconsistent");
    }

    @Test
    void 내_스탬프북_목록_조회는_미완료_진행과_종료_미완료_진행에_보상정보를_반환하지_않는다() {
        when(stampbookRepository.findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(
            projection(
                101L,
                StampbookStatus.PUBLISHED,
                Instant.parse("2026-08-01T00:00:00Z"),
                StampbookProgressStatus.IN_PROGRESS,
                null,
                1L,
                3L,
                Instant.parse("2026-08-02T00:00:00Z")
            ),
            projection(
                102L,
                StampbookStatus.ENDED,
                Instant.parse("2026-08-01T00:00:00Z"),
                StampbookProgressStatus.ENDED_INCOMPLETE,
                null,
                1L,
                3L,
                Instant.parse("2026-08-02T00:00:00Z")
            )
        ));

        List<MyStampbookListResult> results = stampbookReadService.findMyStampbooks(USER_ID);

        assertThat(results)
            .extracting(result -> result.progress().completionReward())
            .containsOnlyNulls();
    }

    @Test
    void 담당지역_심사대기_스탬프북은_제출감사시각과_목표수를_반환한다() {
        Instant requestedAt = Instant.parse("2026-08-14T02:20:00Z");
        when(stampbookRepository.findPendingRegionAdminStampbookProjections(
            10L,
            StampbookStatus.PENDING_REVIEW,
            AuditEventTargetType.STAMPBOOK,
            AuditEventResult.SUCCESS,
            StampbookStatus.DRAFT.name(),
            StampbookStatus.PENDING_REVIEW.name()
        )).thenReturn(List.of(new PendingRegionAdminStampbookProjection(
            101L,
            10L,
            StampbookStatus.PENDING_REVIEW,
            2L,
            301L,
            requestedAt
        )));

        List<PendingRegionAdminStampbookResult> results = stampbookReadService
            .findPendingRegionAdminStampbooks(10L);

        assertThat(results).containsExactly(new PendingRegionAdminStampbookResult(
            101L,
            10L,
            StampbookStatus.PENDING_REVIEW,
            2,
            301L,
            requestedAt
        ));
        verify(stampbookRepository).findPendingRegionAdminStampbookProjections(
            10L,
            StampbookStatus.PENDING_REVIEW,
            AuditEventTargetType.STAMPBOOK,
            AuditEventResult.SUCCESS,
            StampbookStatus.DRAFT.name(),
            StampbookStatus.PENDING_REVIEW.name()
        );
    }

    @Test
    void 심사대기_스탬프북에_제출감사가_없으면_정합성오류로_처리한다() {
        when(stampbookRepository.findPendingRegionAdminStampbookProjections(
            10L,
            StampbookStatus.PENDING_REVIEW,
            AuditEventTargetType.STAMPBOOK,
            AuditEventResult.SUCCESS,
            StampbookStatus.DRAFT.name(),
            StampbookStatus.PENDING_REVIEW.name()
        )).thenReturn(List.of(new PendingRegionAdminStampbookProjection(
            101L,
            10L,
            StampbookStatus.PENDING_REVIEW,
            2L,
            301L,
            null
        )));

        assertThatThrownBy(() -> stampbookReadService.findPendingRegionAdminStampbooks(10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook review read data is inconsistent");
    }

    @Test
    void 내_스탬프북_목록_조회에서_종료된_스탬프북의_진행중_진행도는_정합성_오류로_처리한다() {
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

    @Test
    void 공개_스탬프북의_종료_미완료_진행도는_정합성_오류로_처리한다() {
        when(stampbookRepository.findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(projection(
            101L,
            StampbookStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z"),
            StampbookProgressStatus.ENDED_INCOMPLETE,
            null,
            1L,
            3L,
            Instant.parse("2026-08-02T00:00:00Z")
        )));

        assertThatThrownBy(() -> stampbookReadService.findMyStampbooks(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook progress read data is inconsistent");
    }

    @Test
    void 목표_수를_채운_진행중_진행도는_정합성_오류로_처리한다() {
        when(stampbookRepository.findMyStampbookListProjections(
            USER_ID,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(projection(
            101L,
            StampbookStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z"),
            StampbookProgressStatus.IN_PROGRESS,
            null,
            3L,
            3L,
            Instant.parse("2026-08-02T00:00:00Z")
        )));

        assertThatThrownBy(() -> stampbookReadService.findMyStampbooks(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook progress read data is inconsistent");
    }

    @Test
    void 내_스탬프북_상세_조회는_콘텐츠별_적립과_진행도를_반환한다() {
        Instant publishedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant earnedAt = Instant.parse("2026-08-02T00:00:00Z");
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(
            detailProjection(
                101L,
                StampbookStatus.PUBLISHED,
                publishedAt,
                null,
                StampbookProgressStatus.IN_PROGRESS,
                null,
                201L,
                "첫 번째 콘텐츠",
                earnedAt
            ),
            detailProjection(
                101L,
                StampbookStatus.PUBLISHED,
                publishedAt,
                null,
                StampbookProgressStatus.IN_PROGRESS,
                null,
                202L,
                "두 번째 콘텐츠",
                null
            )
        ));

        MyStampbookDetailResult result = stampbookReadService.findMyStampbookDetail(USER_ID, 101L);

        assertThat(result.targetContents()).containsExactly(
            new MyStampbookDetailResult.TargetContent(201L, "첫 번째 콘텐츠", true, earnedAt),
            new MyStampbookDetailResult.TargetContent(202L, "두 번째 콘텐츠", false, null)
        );
        assertThat(result.progress()).isEqualTo(new MyStampbookDetailResult.Progress(
            MyStampbookProgressStatus.IN_PROGRESS,
            1L,
            2L,
            null,
            null
        ));
        verify(stampbookRepository).findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
    }

    @Test
    void 내_스탬프북_상세_조회는_완료한_본인의_보상_발급_근거를_반환한다() {
        Instant completedAt = Instant.parse("2026-08-04T00:00:00Z");
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(detailProjection(
            101L,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:00Z"),
            StampbookProgressStatus.COMPLETED,
            completedAt,
            201L,
            "첫 번째 콘텐츠",
            completedAt
        )));

        MyStampbookDetailResult result = stampbookReadService.findMyStampbookDetail(USER_ID, 101L);

        assertThat(result.progress()).isEqualTo(new MyStampbookDetailResult.Progress(
            MyStampbookProgressStatus.COMPLETED,
            1L,
            1L,
            completedAt,
            new StampbookCompletionReward(301L, 901L)
        ));
    }

    @Test
    void 종료_미완료_스탬프북은_완료_보상_정보를_반환하지_않는다() {
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(detailProjection(
            101L,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-05T00:00:00Z"),
            StampbookProgressStatus.ENDED_INCOMPLETE,
            null,
            201L,
            "첫 번째 콘텐츠",
            null
        )));

        MyStampbookDetailResult result = stampbookReadService.findMyStampbookDetail(USER_ID, 101L);

        assertThat(result.progress().completionReward()).isNull();
    }

    @Test
    void 공개_스탬프북의_진행_행이_없으면_NOT_STARTED를_반환한다() {
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(detailProjection(
            101L,
            StampbookStatus.PUBLISHED,
            Instant.parse("2026-08-01T00:00:00Z"),
            null,
            null,
            null,
            201L,
            "첫 번째 콘텐츠",
            null
        )));

        MyStampbookDetailResult result = stampbookReadService.findMyStampbookDetail(USER_ID, 101L);

        assertThat(result.progress()).isEqualTo(new MyStampbookDetailResult.Progress(
            MyStampbookProgressStatus.NOT_STARTED,
            0L,
            1L,
            null,
            null
        ));
    }

    @Test
    void 내_스탬프북_상세_조회는_존재하지_않는_대상을_NOT_FOUND로_처리한다() {
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of());
        when(stampbookRepository.existsById(101L)).thenReturn(false);

        assertThatThrownBy(() -> stampbookReadService.findMyStampbookDetail(USER_ID, 101L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void 내_스탬프북_상세_조회는_다른_회원의_종료_이력을_FORBIDDEN으로_처리한다() {
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of());
        when(stampbookRepository.existsById(101L)).thenReturn(true);

        assertThatThrownBy(() -> stampbookReadService.findMyStampbookDetail(USER_ID, 101L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    @Test
    void 내_스탬프북_상세_조회에서_종료된_스탬프북의_진행중_진행도는_정합성_오류로_처리한다() {
        when(stampbookRepository.findMyStampbookDetailProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(detailProjection(
            101L,
            StampbookStatus.ENDED,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-04T00:00:00Z"),
            StampbookProgressStatus.IN_PROGRESS,
            null,
            201L,
            "첫 번째 콘텐츠",
            null
        )));

        assertThatThrownBy(() -> stampbookReadService.findMyStampbookDetail(USER_ID, 101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook progress read data is inconsistent");
    }

    @Test
    void 내_스탬프_적립_이력은_적립시각과_식별자_내림차순으로_반환한다() {
        Instant earnedAt = Instant.parse("2026-08-03T01:00:00Z");
        when(stampbookRepository.findMyStampEarningProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(
            earningProjection(
                101L,
                StampbookStatus.PUBLISHED,
                201L,
                USER_ID,
                502L,
                earnedAt,
                702L,
                USER_ID,
                302L,
                Instant.parse("2026-08-03T00:50:00Z"),
                302L,
                "두 번째 콘텐츠"
            ),
            earningProjection(
                101L,
                StampbookStatus.PUBLISHED,
                201L,
                USER_ID,
                501L,
                earnedAt,
                701L,
                USER_ID,
                301L,
                Instant.parse("2026-08-02T00:50:00Z"),
                301L,
                "첫 번째 콘텐츠"
            )
        ));

        MyStampEarningsResult result = stampbookReadService.findMyStampEarnings(USER_ID, 101L);

        assertThat(result.stampbookId()).isEqualTo(101L);
        assertThat(result.earnings()).containsExactly(
            new MyStampEarningsResult.Earning(
                502L,
                702L,
                302L,
                "두 번째 콘텐츠",
                Instant.parse("2026-08-03T00:50:00Z"),
                earnedAt
            ),
            new MyStampEarningsResult.Earning(
                501L,
                701L,
                301L,
                "첫 번째 콘텐츠",
                Instant.parse("2026-08-02T00:50:00Z"),
                earnedAt
            )
        );
        verify(stampbookRepository).findMyStampEarningProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        );
    }

    @Test
    void 공개_스탬프북에_적립_진행이_없으면_빈_이력을_반환한다() {
        when(stampbookRepository.findMyStampEarningProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(earningProjection(
            101L,
            StampbookStatus.PUBLISHED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )));

        MyStampEarningsResult result = stampbookReadService.findMyStampEarnings(USER_ID, 101L);

        assertThat(result.stampbookId()).isEqualTo(101L);
        assertThat(result.earnings()).isEmpty();
    }

    @Test
    void 내_스탬프_적립_이력은_방문_근거의_사용자_정합성이_다르면_오류로_처리한다() {
        when(stampbookRepository.findMyStampEarningProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of(earningProjection(
            101L,
            StampbookStatus.PUBLISHED,
            201L,
            USER_ID,
            501L,
            Instant.parse("2026-08-03T01:00:00Z"),
            701L,
            200L,
            301L,
            Instant.parse("2026-08-03T00:50:00Z"),
            301L,
            "첫 번째 콘텐츠"
        )));

        assertThatThrownBy(() -> stampbookReadService.findMyStampEarnings(USER_ID, 101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stamp earning read data is inconsistent");
    }

    @Test
    void 내_스탬프_적립_이력_조회는_대상이_없으면_NOT_FOUND로_처리한다() {
        when(stampbookRepository.findMyStampEarningProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of());
        when(stampbookRepository.existsById(101L)).thenReturn(false);

        assertThatThrownBy(() -> stampbookReadService.findMyStampEarnings(USER_ID, 101L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void 내_스탬프_적립_이력_조회는_다른_회원의_종료_이력을_FORBIDDEN으로_처리한다() {
        when(stampbookRepository.findMyStampEarningProjections(
            USER_ID,
            101L,
            StampbookStatus.PUBLISHED,
            StampbookStatus.ENDED
        )).thenReturn(List.of());
        when(stampbookRepository.existsById(101L)).thenReturn(true);

        assertThatThrownBy(() -> stampbookReadService.findMyStampEarnings(USER_ID, 101L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
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
            progressStatus == null ? null : 201L,
            progressStatus == null ? null : USER_ID,
            progressStatus,
            completedAt,
            earnedCount,
            targetCount,
            lastEarnedAt,
            progressStatus == StampbookProgressStatus.COMPLETED ? 901L : null,
            progressStatus == StampbookProgressStatus.COMPLETED ? 301L : null,
            301L
        );
    }

    private MyStampbookDetailProjection detailProjection(
        Long stampbookId,
        StampbookStatus stampbookStatus,
        Instant publishedAt,
        Instant endedAt,
        StampbookProgressStatus progressStatus,
        Instant completedAt,
        Long contentId,
        String contentTitle,
        Instant earnedAt
    ) {
        return new MyStampbookDetailProjection(
            stampbookId,
            10L,
            stampbookStatus,
            publishedAt,
            endedAt,
            progressStatus == null ? null : 201L,
            progressStatus == null ? null : USER_ID,
            progressStatus,
            completedAt,
            contentId,
            contentTitle,
            earnedAt,
            progressStatus == StampbookProgressStatus.COMPLETED ? 901L : null,
            progressStatus == StampbookProgressStatus.COMPLETED ? 301L : null,
            301L
        );
    }

    private MyStampEarningProjection earningProjection(
        Long stampbookId,
        StampbookStatus stampbookStatus,
        Long stampbookProgressId,
        Long progressUserId,
        Long stampEarnId,
        Instant earnedAt,
        Long visitId,
        Long visitUserId,
        Long visitContentId,
        Instant visitedAt,
        Long contentId,
        String contentTitle
    ) {
        return new MyStampEarningProjection(
            stampbookId,
            stampbookStatus,
            stampbookProgressId,
            progressUserId,
            stampEarnId,
            earnedAt,
            visitId,
            visitUserId,
            visitContentId,
            visitedAt,
            contentId,
            contentTitle,
            contentId
        );
    }
}
