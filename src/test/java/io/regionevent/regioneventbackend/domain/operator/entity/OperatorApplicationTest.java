package io.regionevent.regioneventbackend.domain.operator.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class OperatorApplicationTest {

    @Test
    void 승인과_반려_상태의_필수값을_생성_시_검증한다() {
        AppUser applicant = newUser("applicant@example.com");
        AppUser inspector = newUser("inspector@example.com");
        Region region = new Region("GIMHAE", "김해시", true);

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

    private AppUser newUser(String loginIdentifier) {
        return new AppUser(
            loginIdentifier,
            "hashed-password",
            "홍길동",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
    }
}
