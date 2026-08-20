package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

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

    @Test
    void 담당지역의_대기_요청이면서_공개되고_미삭제인_콘텐츠만_조회한다() {
        Region assignedRegion = saveRegion("WITHDRAWAL-LIST-A");
        Region otherRegion = saveRegion("WITHDRAWAL-LIST-B");
        AppUser requester = saveUser("filter-requester@example.com", "요청자");
        AppUser reviewer = saveUser("filter-reviewer@example.com", "심사자");
        Content eligibleContent = saveContent(
            assignedRegion,
            requester,
            ContentStatus.PUBLISHED,
            "조회 대상"
        );
        ContentWithdrawalRequest eligible = savePendingRequest(
            eligibleContent,
            requester,
            "c",
            REQUESTED_AT
        );
        savePendingRequest(
            saveContent(otherRegion, requester, ContentStatus.PUBLISHED, "다른 지역"),
            requester,
            "d",
            REQUESTED_AT
        );
        savePendingRequest(
            saveContent(assignedRegion, requester, ContentStatus.APPROVED, "비공개"),
            requester,
            "e",
            REQUESTED_AT
        );
        Content deletedContent = saveContent(
            assignedRegion,
            requester,
            ContentStatus.APPROVED,
            "삭제"
        );
        savePendingRequest(deletedContent, requester, "f", REQUESTED_AT);
        deletedContent.softDelete(REQUESTED_AT.plusSeconds(1));
        contentRepository.saveAndFlush(deletedContent);
        ContentWithdrawalRequest approved = savePendingRequest(
            saveContent(assignedRegion, requester, ContentStatus.PUBLISHED, "처리 완료"),
            requester,
            "0",
            REQUESTED_AT
        );
        approved.approve(reviewer, REQUESTED_AT.plusSeconds(2));
        requestRepository.saveAndFlush(approved);
        entityManager.clear();

        List<ContentWithdrawalRequest> requests = requestRepository
            .findReviewCandidatesByRegionId(
                assignedRegion.getRegionId(),
                ContentWithdrawalRequestStatus.PENDING,
                ContentStatus.PUBLISHED
            );

        assertThat(requests).extracting(ContentWithdrawalRequest::getContentWithdrawalRequestId)
            .containsExactly(eligible.getContentWithdrawalRequestId());
        assertThat(requests.getFirst().getContent().getContentId())
            .isEqualTo(eligibleContent.getContentId());
    }

    @Test
    void 요청시각과_요청_ID_오름차순으로_정렬한다() {
        Region region = saveRegion("WITHDRAWAL-ORDER");
        AppUser requester = saveUser("order-requester@example.com", "요청자");
        ContentWithdrawalRequest later = savePendingRequest(
            saveContent(region, requester, ContentStatus.PUBLISHED, "나중 요청"),
            requester,
            "1",
            REQUESTED_AT.plusSeconds(1)
        );
        ContentWithdrawalRequest tiedFirst = savePendingRequest(
            saveContent(region, requester, ContentStatus.PUBLISHED, "동률 첫 요청"),
            requester,
            "2",
            REQUESTED_AT
        );
        ContentWithdrawalRequest tiedSecond = savePendingRequest(
            saveContent(region, requester, ContentStatus.PUBLISHED, "동률 둘째 요청"),
            requester,
            "3",
            REQUESTED_AT
        );
        entityManager.clear();

        List<ContentWithdrawalRequest> requests = requestRepository
            .findReviewCandidatesByRegionId(
                region.getRegionId(),
                ContentWithdrawalRequestStatus.PENDING,
                ContentStatus.PUBLISHED
            );

        assertThat(requests).extracting(ContentWithdrawalRequest::getContentWithdrawalRequestId)
            .containsExactly(
                tiedFirst.getContentWithdrawalRequestId(),
                tiedSecond.getContentWithdrawalRequestId(),
                later.getContentWithdrawalRequestId()
            );
    }

    @Test
    void 요청자_연결이_없어도_대기_요청을_조회한다() {
        Region region = saveRegion("WITHDRAWAL-NULL-REQUESTER");
        AppUser requester = saveUser("null-requester@example.com", "탈퇴 요청자");
        ContentWithdrawalRequest request = savePendingRequest(
            saveContent(region, requester, ContentStatus.PUBLISHED, "요청자 탈퇴 콘텐츠"),
            requester,
            "4",
            REQUESTED_AT
        );
        requestRepository.unlinkRequesterByUserId(requester.getUserId());

        List<ContentWithdrawalRequest> requests = requestRepository
            .findReviewCandidatesByRegionId(
                region.getRegionId(),
                ContentWithdrawalRequestStatus.PENDING,
                ContentStatus.PUBLISHED
            );

        assertThat(requests).singleElement().satisfies(found -> {
            assertThat(found.getContentWithdrawalRequestId())
                .isEqualTo(request.getContentWithdrawalRequestId());
            assertThat(found.getRequestedBy()).isNull();
        });
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

    private Region saveRegion(String code) {
        return regionRepository.saveAndFlush(new Region(code, "테스트 지역", true));
    }

    private AppUser saveUser(String loginIdentifier, String name) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        ContentStatus status,
        String title
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            title,
            "설명",
            "위치",
            "운영 시간",
            "055-000-0000",
            "유의사항",
            "연령",
            "준비물",
            "취소 정책",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
    }

    private ContentWithdrawalRequest savePendingRequest(
        Content content,
        AppUser requester,
        String hashCharacter,
        Instant requestedAt
    ) {
        return requestRepository.saveAndFlush(ContentWithdrawalRequest.createPending(
            content,
            requester,
            hashCharacter.repeat(64),
            "철회 요청 사유",
            requestedAt
        ));
    }

    private record Fixtures(Content content, AppUser requester) {
    }
}
