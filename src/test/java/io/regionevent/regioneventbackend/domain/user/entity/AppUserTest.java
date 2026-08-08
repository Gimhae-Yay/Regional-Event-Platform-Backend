package io.regionevent.regioneventbackend.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppUserTest {

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

    @Test
    void 기존_생성자는_일반_계정으로_분류한다() {
        AppUser appUser = new AppUser(
            "visitor@example.com",
            "hashed-password",
            "홍길동",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );

        assertThat(appUser.getAccountKind()).isEqualTo(AppUserAccountKind.ORDINARY);
    }

    @Test
    void 고권한_계정_분류를_명시해_생성한다() {
        AppUser appUser = new AppUser(
            "admin@example.com",
            "hashed-password",
            "전체관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        );

        assertThat(appUser.getAccountKind()).isEqualTo(AppUserAccountKind.PRIVILEGED);
    }

    @Test
    void 계정_분류가_null이면_생성할_수_없다() {
        assertThatThrownBy(() -> new AppUser(
            "admin@example.com",
            "hashed-password",
            "전체관리자",
            "010-1234-5678",
            null,
            AppUserStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
