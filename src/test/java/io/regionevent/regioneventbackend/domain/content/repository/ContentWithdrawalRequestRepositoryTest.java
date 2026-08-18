package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.hibernate.Hibernate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentWithdrawalRequestRepositoryTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-16T04:00:00Z");
    private static final String FIRST_KEY_HASH = "a".repeat(64);
    private static final String SECOND_KEY_HASH = "b".repeat(64);

    private final ContentWithdrawalRequestRepository requestRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    ContentWithdrawalRequestRepositoryTest(
        ContentWithdrawalRequestRepository requestRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.requestRepository = requestRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 요청_필드와_생성_컬럼을_매핑하고_두_잠금_조회로_찾는다() {
        Fixtures fixtures = createFixtures();
        ContentWithdrawalRequest saved = requestRepository.saveAndFlush(pendingRequest(
            fixtures,
            FIRST_KEY_HASH,
            "  운영 계획 변경  "
        ));
        entityManager.clear();

        ContentWithdrawalRequest byKey = requestRepository
            .findByContentIdAndIdempotencyKeyHashForUpdate(
                fixtures.content().getContentId(),
                FIRST_KEY_HASH
            )
            .orElseThrow();
        ContentWithdrawalRequest pending = requestRepository.findByContentIdAndStatusForUpdate(
            fixtures.content().getContentId(),
            ContentWithdrawalRequestStatus.PENDING
        ).orElseThrow();

        assertThat(byKey.getContentWithdrawalRequestId())
            .isEqualTo(saved.getContentWithdrawalRequestId());
        assertThat(pending.getContentWithdrawalRequestId())
            .isEqualTo(saved.getContentWithdrawalRequestId());
        assertThat(byKey.getContent().getContentId()).isEqualTo(fixtures.content().getContentId());
        assertThat(byKey.getRequestedBy().getUserId()).isEqualTo(fixtures.requester().getUserId());
        assertThat(byKey.getRequestReason()).isEqualTo("운영 계획 변경");
        assertThat(byKey.getRequestedAt()).isEqualTo(REQUESTED_AT);
    }

    @Test
    void 요청_ID로_콘텐츠_ID를_조회하고_심사_대상을_잠근다() {
        Fixtures fixtures = createFixtures();
        ContentWithdrawalRequest saved = requestRepository.saveAndFlush(pendingRequest(
            fixtures,
            FIRST_KEY_HASH,
            "운영 계획 변경"
        ));
        entityManager.clear();

        Long contentId = requestRepository.findContentIdByWithdrawalRequestId(
            saved.getContentWithdrawalRequestId()
        ).orElseThrow();
        ContentWithdrawalRequest reviewTarget = requestRepository.findReviewTargetForUpdate(
            saved.getContentWithdrawalRequestId()
        ).orElseThrow();

        assertThat(contentId).isEqualTo(fixtures.content().getContentId());
        assertThat(reviewTarget.getContentWithdrawalRequestId())
            .isEqualTo(saved.getContentWithdrawalRequestId());
    }

    @Test
    void 상세_조회는_콘텐츠와_지역과_nullable_요청자를_함께_로딩한다() {
        Fixtures fixtures = createFixtures();
        ContentWithdrawalRequest saved = requestRepository.saveAndFlush(pendingRequest(
            fixtures,
            FIRST_KEY_HASH,
            "운영 계획 변경"
        ));
        entityManager.clear();

        ContentWithdrawalRequest detail = requestRepository.findReviewDetailById(
            saved.getContentWithdrawalRequestId()
        ).orElseThrow();

        assertThat(Hibernate.isInitialized(detail.getContent())).isTrue();
        assertThat(Hibernate.isInitialized(detail.getContent().getRegion())).isTrue();
        assertThat(Hibernate.isInitialized(detail.getRequestedBy())).isTrue();
        assertThat(detail.getContent().getContentId()).isEqualTo(fixtures.content().getContentId());
        assertThat(detail.getContent().getRegion().getRegionId())
            .isEqualTo(fixtures.content().getRegion().getRegionId());
        assertThat(detail.getRequestedBy().getUserId()).isEqualTo(fixtures.requester().getUserId());
    }

    @Test
    void 상세_조회는_종결_요청도_조회한다() {
        Fixtures fixtures = createFixtures();
        ContentWithdrawalRequest saved = requestRepository.saveAndFlush(pendingRequest(
            fixtures,
            FIRST_KEY_HASH,
            "운영 계획 변경"
        ));
        saved.invalidateBySystem(
            REQUESTED_AT.plusSeconds(60),
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        );
        requestRepository.saveAndFlush(saved);
        entityManager.clear();

        ContentWithdrawalRequest detail = requestRepository.findReviewDetailById(
            saved.getContentWithdrawalRequestId()
        ).orElseThrow();

        assertThat(detail.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.INVALIDATED);
    }

    @Test
    void 상세_조회는_요청자_연결이_제거되면_null로_조회한다() {
        Fixtures fixtures = createFixtures();
        ContentWithdrawalRequest saved = requestRepository.saveAndFlush(pendingRequest(
            fixtures,
            FIRST_KEY_HASH,
            "운영 계획 변경"
        ));
        requestRepository.unlinkRequesterByUserId(fixtures.requester().getUserId());
        entityManager.clear();

        ContentWithdrawalRequest detail = requestRepository.findReviewDetailById(
            saved.getContentWithdrawalRequestId()
        ).orElseThrow();

        assertThat(detail.getRequestedBy()).isNull();
    }

    @Test
    void 콘텐츠별_대기_요청은_한_건만_저장한다() {
        Fixtures fixtures = createFixtures();
        requestRepository.saveAndFlush(pendingRequest(fixtures, FIRST_KEY_HASH, "첫 요청"));

        assertThatThrownBy(() -> requestRepository.saveAndFlush(
            pendingRequest(fixtures, SECOND_KEY_HASH, "두 번째 요청")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 심사_종결_뒤에도_콘텐츠와_멱등_키_조합은_유일하다() {
        Fixtures fixtures = createFixtures();
        ContentWithdrawalRequest first = requestRepository.saveAndFlush(
            pendingRequest(fixtures, FIRST_KEY_HASH, "첫 요청")
        );
        first.invalidateBySystem(
            REQUESTED_AT.plusSeconds(60),
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        );
        requestRepository.saveAndFlush(first);

        assertThatThrownBy(() -> requestRepository.saveAndFlush(
            pendingRequest(fixtures, FIRST_KEY_HASH, "재사용 요청")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixtures createFixtures() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser requester = appUserRepository.saveAndFlush(new AppUser(
            "withdrawal-operator@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            requester,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        return new Fixtures(content, requester);
    }

    private ContentWithdrawalRequest pendingRequest(
        Fixtures fixtures,
        String keyHash,
        String reason
    ) {
        return ContentWithdrawalRequest.createPending(
            fixtures.content(),
            fixtures.requester(),
            keyHash,
            reason,
            REQUESTED_AT
        );
    }

    private record Fixtures(Content content, AppUser requester) {
    }
}
