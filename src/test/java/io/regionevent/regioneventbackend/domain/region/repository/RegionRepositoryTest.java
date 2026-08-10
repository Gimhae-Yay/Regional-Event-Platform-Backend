package io.regionevent.regioneventbackend.domain.region.repository;

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

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class RegionRepositoryTest {

    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final EntityManager entityManager;

    @Autowired
    RegionRepositoryTest(
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        EntityManager entityManager
    ) {
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 지역을_저장하고_식별자로_조회한다() {
        Region region = regionRepository.saveAndFlush(
            new Region("GIMHAE", "김해시", true)
        );

        Region foundRegion = regionRepository.findById(region.getRegionId()).orElseThrow();

        assertThat(foundRegion.getRegionCode()).isEqualTo("GIMHAE");
        assertThat(foundRegion.getName()).isEqualTo("김해시");
        assertThat(foundRegion.isPublic()).isTrue();
        assertThat(foundRegion.getCreatedAt()).isNotNull();
        assertThat(foundRegion.getUpdatedAt()).isNotNull();
    }

    @Test
    void 지역_코드는_중복될_수_없다() {
        regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));

        assertThatThrownBy(
            () -> regionRepository.saveAndFlush(new Region("GIMHAE", "다른 지역", false))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 공개_지역_검증_정보를_이름과_지역_식별자_오름차순으로_조회한다() {
        Region privateRegion = regionRepository.saveAndFlush(new Region("PRIVATE", "Aardvark", false));
        Region beta = regionRepository.saveAndFlush(new Region("BETA", "Beta", true));
        Region firstSameName = regionRepository.saveAndFlush(new Region("SAME-ONE", "Same", true));
        Region secondSameName = regionRepository.saveAndFlush(new Region("SAME-TWO", "Same", true));

        List<PublicRegionVerificationProjection> regions = regionRepository.findPublicRegionVerifications();

        assertThat(regions)
            .extracting(PublicRegionVerificationProjection::regionId)
            .containsExactly(
                beta.getRegionId(),
                firstSameName.getRegionId(),
                secondSameName.getRegionId()
            );
        assertThat(regions)
            .extracting(PublicRegionVerificationProjection::regionId)
            .doesNotContain(privateRegion.getRegionId());
    }

    @Test
    void 공개_지역_정적_표시_정보를_별도로_조회한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));

        PublicRegionStaticProjection staticInfo = regionRepository.findPublicRegionStaticInfo(
            region.getRegionId()
        ).orElseThrow();

        assertThat(staticInfo).isEqualTo(
            new PublicRegionStaticProjection(region.getRegionId(), "GIMHAE", "김해시")
        );
    }

    @Test
    void 전체관리자_지역목록은_활성일반지역관리자만집계하고_고정정렬한다() {
        Region firstSameName = regionRepository.saveAndFlush(new Region("FIRST", "가나다", false));
        Region secondSameName = regionRepository.saveAndFlush(new Region("SECOND", "가나다", true));
        Region lastRegion = regionRepository.saveAndFlush(new Region("LAST", "다라마바사", true));
        Long firstSameNameId = firstSameName.getRegionId();

        assignRegionAdmin(firstSameName, "active-admin-1", AppUserAccountKind.ORDINARY, AppUserStatus.ACTIVE);
        assignRegionAdmin(firstSameName, "active-admin-2", AppUserAccountKind.ORDINARY, AppUserStatus.ACTIVE);
        UserRoleAssignment revokedAssignment = assignRegionAdmin(
            firstSameName,
            "revoked-admin",
            AppUserAccountKind.ORDINARY,
            AppUserStatus.ACTIVE
        );
        revokedAssignment.revoke(Instant.parse("2026-08-09T02:00:00Z"), "TEST_REVOKE");
        userRoleAssignmentRepository.saveAndFlush(revokedAssignment);
        assignRegionAdmin(firstSameName, "withdrawing-admin", AppUserAccountKind.ORDINARY, AppUserStatus.WITHDRAWING);
        assignRegionAdmin(firstSameName, "privileged-admin", AppUserAccountKind.PRIVILEGED, AppUserStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();
        firstSameName = regionRepository.findById(firstSameNameId).orElseThrow();
        Instant firstRegionUpdatedAt = firstSameName.getUpdatedAt();

        List<PlatformAdminRegionListProjection> regions = regionRepository
            .findPlatformAdminRegionList(null);
        List<PlatformAdminRegionListProjection> privateRegions = regionRepository
            .findPlatformAdminRegionList(false);

        assertThat(regions)
            .extracting(PlatformAdminRegionListProjection::regionId)
            .containsExactly(
                firstSameName.getRegionId(),
                secondSameName.getRegionId(),
                lastRegion.getRegionId()
            );
        assertThat(regions)
            .extracting(PlatformAdminRegionListProjection::regionAdminCount)
            .containsExactly(2L, 0L, 0L);
        assertThat(privateRegions).containsExactly(new PlatformAdminRegionListProjection(
            firstSameName.getRegionId(),
            "FIRST",
            "가나다",
            false,
            2L,
            firstSameName.getCreatedAt(),
            firstRegionUpdatedAt
        ));
        assertThat(firstSameName.getUpdatedAt()).isEqualTo(firstRegionUpdatedAt);
        assertThat(revokedAssignment.getStatus()).isEqualTo(UserRoleAssignmentStatus.REVOKED);
    }

    private UserRoleAssignment assignRegionAdmin(
        Region region,
        String loginIdentifier,
        AppUserAccountKind accountKind,
        AppUserStatus status
    ) {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "{bcrypt}password",
            "관리자",
            "010-0000-0000",
            accountKind,
            status
        ));
        return userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            user,
            UserRole.REGION_ADMIN,
            region
        ));
    }
}
