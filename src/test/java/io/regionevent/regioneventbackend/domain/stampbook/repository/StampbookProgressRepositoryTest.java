package io.regionevent.regioneventbackend.domain.stampbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class StampbookProgressRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-10T00:00:00Z");

    private final StampbookProgressRepository stampbookProgressRepository;
    private final StampbookRepository stampbookRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    StampbookProgressRepositoryTest(
        StampbookProgressRepository stampbookProgressRepository,
        StampbookRepository stampbookRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.stampbookProgressRepository = stampbookProgressRepository;
        this.stampbookRepository = stampbookRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 사용자와_스탬프북_조합별_진행은_하나만_저장된다() {
        Stampbook stampbook = saveStampbook();
        AppUser user = saveUser("visitor@example.com");
        stampbookProgressRepository.saveAndFlush(new StampbookProgress(stampbook, user));

        assertThatThrownBy(() -> stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, user)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 사용자별_진행은_일반_조회와_잠금_조회로_찾을_수_있다() {
        Stampbook stampbook = saveStampbook();
        AppUser user = saveUser("visitor@example.com");
        StampbookProgress progress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, user)
        );
        entityManager.clear();

        StampbookProgress foundProgress = stampbookProgressRepository
            .findByStampbookStampbookIdAndUserUserId(stampbook.getStampbookId(), user.getUserId())
            .orElseThrow();
        StampbookProgress lockedProgress = stampbookProgressRepository
            .findByStampbookIdAndUserIdForUpdate(stampbook.getStampbookId(), user.getUserId())
            .orElseThrow();

        assertThat(foundProgress.getStampbookProgressId()).isEqualTo(progress.getStampbookProgressId());
        assertThat(lockedProgress.getStampbookProgressId()).isEqualTo(progress.getStampbookProgressId());
        assertThat(lockedProgress.getStatus()).isEqualTo(StampbookProgressStatus.IN_PROGRESS);
    }

    @Test
    void 종료_처리를_위한_잠금_조회는_미완료_진행만_식별자_순으로_반환한다() {
        Stampbook stampbook = saveStampbook();
        AppUser completedUser = saveUser("completed@example.com");
        AppUser firstInProgressUser = saveUser("first@example.com");
        AppUser secondInProgressUser = saveUser("second@example.com");
        StampbookProgress completedProgress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, completedUser)
        );
        completedProgress.complete(COMPLETED_AT);
        StampbookProgress firstInProgress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, firstInProgressUser)
        );
        StampbookProgress secondInProgress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, secondInProgressUser)
        );
        stampbookProgressRepository.flush();
        entityManager.clear();

        List<StampbookProgress> inProgresses = stampbookProgressRepository
            .findByStampbookIdAndStatusForUpdate(
                stampbook.getStampbookId(),
                StampbookProgressStatus.IN_PROGRESS
            );
        inProgresses.forEach(StampbookProgress::endIncomplete);
        stampbookProgressRepository.flush();
        entityManager.clear();

        assertThat(inProgresses)
            .extracting(StampbookProgress::getStampbookProgressId)
            .containsExactly(firstInProgress.getStampbookProgressId(), secondInProgress.getStampbookProgressId());
        assertThat(findProgress(stampbook, completedUser).getStatus())
            .isEqualTo(StampbookProgressStatus.COMPLETED);
        assertThat(findProgress(stampbook, completedUser).getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(findProgress(stampbook, firstInProgressUser).getStatus())
            .isEqualTo(StampbookProgressStatus.ENDED_INCOMPLETE);
        assertThat(findProgress(stampbook, firstInProgressUser).getCompletedAt()).isNull();
        assertThat(findProgress(stampbook, secondInProgressUser).getStatus())
            .isEqualTo(StampbookProgressStatus.ENDED_INCOMPLETE);
    }

    @Test
    void 완료와_종료_미완료_상태는_진행_중에서만_전이할_수_있다() {
        Stampbook stampbook = saveStampbook();
        AppUser user = saveUser("visitor@example.com");
        StampbookProgress progress = new StampbookProgress(stampbook, user);

        progress.complete(COMPLETED_AT);

        assertThat(progress.getStatus()).isEqualTo(StampbookProgressStatus.COMPLETED);
        assertThat(progress.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThatThrownBy(progress::endIncomplete)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("stampbook progress status cannot be changed");
    }

    private StampbookProgress findProgress(
        Stampbook stampbook,
        AppUser user
    ) {
        return stampbookProgressRepository
            .findByStampbookStampbookIdAndUserUserId(stampbook.getStampbookId(), user.getUserId())
            .orElseThrow();
    }

    private Stampbook saveStampbook() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            saveUser("operator@example.com"),
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            ISSUE_STARTS_AT
        ));
        CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "스탬프북 완료 보상",
            "스탬프북 완료 시 발급하는 할인 쿠폰입니다.",
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
        return stampbookRepository.saveAndFlush(new Stampbook(region, couponPolicy));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }
}
