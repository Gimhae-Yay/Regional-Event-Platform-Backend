package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class AppUserRepositoryTest {

    private final AppUserRepository appUserRepository;

    @Autowired
    AppUserRepositoryTest(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Test
    void 사용자를_저장하고_식별자로_조회한다() {
        AppUser appUser = appUserRepository.saveAndFlush(
            new AppUser("visitor@example.com", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );

        AppUser foundUser = appUserRepository.findById(appUser.getUserId()).orElseThrow();

        assertThat(foundUser.getLoginIdentifier()).isEqualTo("visitor@example.com");
        assertThat(foundUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(foundUser.getName()).isEqualTo("홍길동");
        assertThat(foundUser.getPhone()).isEqualTo("010-1234-5678");
        assertThat(foundUser.getStatus()).isEqualTo(AppUserStatus.ACTIVE);
        assertThat(foundUser.getCreatedAt()).isNotNull();
        assertThat(foundUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void 로그인_식별자는_중복될_수_없다() {
        appUserRepository.saveAndFlush(
            new AppUser("visitor@example.com", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );

        assertThatThrownBy(
            () -> appUserRepository.saveAndFlush(
                new AppUser("visitor@example.com", "other-hash", "김철수", "010-8765-4321", AppUserStatus.ACTIVE)
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 철회_중인_사용자_상태를_문자열로_매핑한다() {
        AppUser appUser = appUserRepository.saveAndFlush(
            new AppUser("withdrawn@example.com", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.WITHDRAWING)
        );

        assertThat(appUserRepository.findById(appUser.getUserId()).orElseThrow().getStatus())
            .isEqualTo(AppUserStatus.WITHDRAWING);
    }

    @Test
    void 필수_문자열이_null이면_생성할_수_없다() {
        assertThatThrownBy(
            () -> new AppUser(null, "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", null, "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", "hashed-password", null, "010-1234-5678", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", "hashed-password", "홍길동", null, AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 필수_문자열이_blank이면_생성할_수_없다() {
        assertThatThrownBy(
            () -> new AppUser(" ", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", "\t", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", "hashed-password", "\n", "010-1234-5678", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", "hashed-password", "홍길동", "  ", AppUserStatus.ACTIVE)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상태가_null이면_생성할_수_없다() {
        assertThatThrownBy(
            () -> new AppUser("visitor@example.com", "hashed-password", "홍길동", "010-1234-5678", null)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
