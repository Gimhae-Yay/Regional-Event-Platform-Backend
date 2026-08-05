package io.regionevent.regioneventbackend.domain.user.entity;

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
}
