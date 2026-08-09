package io.regionevent.regioneventbackend.domain.audit.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditEventCatalogTest {

    @Test
    void 감사_결과는_성공과_실패를_표현한다() {
        assertThat(AuditEventResult.values())
            .containsExactly(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
    }

    @Test
    void 감사_대상_유형은_비개인_도메인으로만_제한한다() {
        assertThat(AuditEventTargetType.values()).containsExactly(
            AuditEventTargetType.REGION,
            AuditEventTargetType.OPERATOR_APPLICATION,
            AuditEventTargetType.CONTENT,
            AuditEventTargetType.CONTENT_SESSION,
            AuditEventTargetType.CAPACITY_HOLD,
            AuditEventTargetType.RESERVATION,
            AuditEventTargetType.VISIT,
            AuditEventTargetType.REVIEW,
            AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT,
            AuditEventTargetType.USER_ROLE_ASSIGNMENT,
            AuditEventTargetType.STAMPBOOK,
            AuditEventTargetType.MISSION,
            AuditEventTargetType.COUPON_POLICY,
            AuditEventTargetType.COUPON,
            AuditEventTargetType.RESERVATION_PRICE_SNAPSHOT,
            AuditEventTargetType.PAYMENT,
            AuditEventTargetType.REFUND,
            AuditEventTargetType.PAYMENT_DISCREPANCY
        );
    }
}
