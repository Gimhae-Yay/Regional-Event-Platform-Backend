package io.regionevent.regioneventbackend.domain.operator.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class OperatorApplicationRepositoryTest {

    private final OperatorApplicationRepository operatorApplicationRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OperatorApplicationRepositoryTest(
        OperatorApplicationRepository operatorApplicationRepository,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 승인된_신청을_심사자와_함께_저장한다() {
        AppUser applicant = saveUser("applicant@example.com");
        AppUser inspector = saveUser("inspector@example.com");
        Region region = saveRegion();

        OperatorApplication application = operatorApplicationRepository.saveAndFlush(
            new OperatorApplication(
                applicant,
                region,
                "김해 지역 문화 행사 운영 사업자 정보",
                OperatorApplicationStatus.APPROVED,
                inspector,
                null
            )
        );

        assertThat(application.getOperatorApplicationId()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.APPROVED);
        assertThat(application.getInspectedUser()).isEqualTo(inspector);
        assertThat(application.getRejectedReason()).isNull();
        assertThat(application.getCreatedAt()).isNotNull();
        assertThat(application.getUpdatedAt()).isNotNull();
    }

    @Test
    void 반려된_신청을_심사자와_반려_사유와_함께_저장한다() {
        AppUser applicant = saveUser("rejected-applicant@example.com");
        AppUser inspector = saveUser("rejected-inspector@example.com");
        Region region = saveRegion();

        OperatorApplication application = operatorApplicationRepository.saveAndFlush(
            new OperatorApplication(
                applicant,
                region,
                "김해 지역 문화 행사 운영 사업자 정보",
                OperatorApplicationStatus.REJECTED,
                inspector,
                "사업자 정보가 확인되지 않았습니다."
            )
        );

        assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.REJECTED);
        assertThat(application.getInspectedUser()).isEqualTo(inspector);
        assertThat(application.getRejectedReason()).isEqualTo("사업자 정보가 확인되지 않았습니다.");
    }

    @Test
    void 승인과_반려_상태의_필수값을_생성_시_검증한다() {
        AppUser applicant = saveUser("validation-applicant@example.com");
        AppUser inspector = saveUser("validation-inspector@example.com");
        Region region = saveRegion();

        assertThatThrownBy(() -> new OperatorApplication(
            applicant,
            region,
            "사업자 정보",
            OperatorApplicationStatus.APPROVED,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OperatorApplication(
            applicant,
            region,
            "사업자 정보",
            OperatorApplicationStatus.APPROVED,
            inspector,
            "반려 사유"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OperatorApplication(
            applicant,
            region,
            "사업자 정보",
            OperatorApplicationStatus.REJECTED,
            inspector,
            " "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 심사_결과_필수값을_데이터베이스_제약으로_검증한다() {
        AppUser applicant = saveUser("constraint-applicant@example.com");
        AppUser inspector = saveUser("constraint-inspector@example.com");
        Region region = saveRegion();

        assertThat(jdbcTemplate.update(
            """
                INSERT INTO operator_application (
                    applicant_user_id,
                    requested_region_id,
                    business_information,
                    status,
                    inspected_user_id,
                    rejected_reason,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            applicant.getUserId(),
            region.getRegionId(),
            "사업자 정보",
            "REJECTED",
            inspector.getUserId(),
            "반려 사유"
        )).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO operator_application (
                    applicant_user_id,
                    requested_region_id,
                    business_information,
                    status,
                    inspected_user_id,
                    rejected_reason,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            applicant.getUserId(),
            region.getRegionId(),
            "사업자 정보",
            "REJECTED",
            null,
            "심사자 없음"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO operator_application (
                    applicant_user_id,
                    requested_region_id,
                    business_information,
                    status,
                    inspected_user_id,
                    rejected_reason,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            applicant.getUserId(),
            region.getRegionId(),
            "사업자 정보",
            "APPROVED",
            inspector.getUserId(),
            "승인에는 반려 사유가 없어야 합니다."
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 신청자와_심사자와_요청_지역을_지연_로딩으로_매핑한다() {
        AppUser applicant = saveUser("lazy-applicant@example.com");
        AppUser inspector = saveUser("lazy-inspector@example.com");
        Region region = saveRegion();
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(
            new OperatorApplication(
                applicant,
                region,
                "김해 지역 문화 행사 운영 사업자 정보",
                OperatorApplicationStatus.REJECTED,
                inspector,
                "사업자 정보가 부족합니다."
            )
        );
        entityManager.clear();

        OperatorApplication foundApplication = operatorApplicationRepository
            .findById(application.getOperatorApplicationId())
            .orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundApplication, "applicant")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundApplication, "inspectedUser")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundApplication, "requestedRegion")).isFalse();
        assertThat(foundApplication.getApplicant().getUserId()).isEqualTo(applicant.getUserId());
        assertThat(foundApplication.getInspectedUser().getUserId()).isEqualTo(inspector.getUserId());
        assertThat(foundApplication.getRequestedRegion().getRegionId()).isEqualTo(region.getRegionId());
    }

    @Test
    void 탈퇴_후_신청자와_사업자_정보를_null로_저장할_수_있다() {
        AppUser applicant = saveUser("withdrawn-applicant@example.com");
        Region region = saveRegion();
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(
            new OperatorApplication(
                applicant,
                region,
                "김해 지역 문화 행사 운영 사업자 정보",
                OperatorApplicationStatus.CANCELLED,
                null,
                null
            )
        );

        jdbcTemplate.update(
            """
                UPDATE operator_application
                SET applicant_user_id = NULL,
                    business_information = NULL
                WHERE operator_application_id = ?
                """,
            application.getOperatorApplicationId()
        );
        entityManager.clear();

        OperatorApplication foundApplication = operatorApplicationRepository
            .findById(application.getOperatorApplicationId())
            .orElseThrow();

        assertThat(foundApplication.getApplicant()).isNull();
        assertThat(foundApplication.getBusinessInformation()).isNull();
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(
            new AppUser(loginIdentifier, "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );
    }

    private Region saveRegion() {
        return regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
    }
}
