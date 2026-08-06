# 로컬스탬프 P1 ERD 초안

> 상태: 정책 반영 초안
> 작성일: 2026-08-05
> 근거: [P1 정책 결정 로그](p1-policy-decision-log.md)와 제안 ADR-0064~ADR-0071
> 범위: P0 ERD를 대체하지 않는다. P1에서 추가·변경되는 논리 테이블, 제약, 상태 전이와 P0 재사용 경계만 정의한다.

## 1. 기준과 범위

이 초안은 다음 프로젝트 문서를 기준으로 한다.

- `docs/erd.md`의 P0 기준 테이블과 개인정보 경계
- `docs/p1/stampbook.md`, `docs/p1/regional-mission.md`, `docs/p1/coupon.md`
- `docs/p1/payment-refund.md`, `docs/p1/platform-admin.md`
- `docs/adr/0012-retain-author-unlinked-reviews-and-visits-after-withdrawal.md`

P1은 다음 P0 사실을 변경하지 않고 재사용한다.

| P0 요소 | P1에서의 사용 |
| --- | --- |
| `visit` | 스탬프·미션·방문 보상 쿠폰의 변경 불가능한 원본 근거 |
| `capacity_hold` | 유료 결제 중에도 최대 10분 동안 정원을 선점하는 유일한 단위 |
| `reservation` | 서버 검증 승인 또는 0원 예약 확정 뒤에만 생성되는 예약 |
| `region.is_public` | `false`: 비공개·준비, `true`: 공개·운영. 별도 지역 운영 상태는 만들지 않음 |
| `audit_event` | P1의 특권·혜택·거래 상태 변경을 남기는 공통 감사 기록 |

P1은 `PENDING_PAYMENT` 예약, 포인트·현금성 보상, 다중 쿠폰 사용, 부분 환불, PortOne·웹훅 원문 저장을 만들지 않는다.

## 2. 핵심 설계 원칙

1. **근거 우선**: 혜택은 `visit`, 보상 수령, 가격 스냅샷처럼 실제 원본 근거에만 연결한다.
2. **현재 상태와 이력 분리**: 쿠폰·권한·결제·환불의 현재 상태와 추가 전용 이력을 분리한다.
3. **예약과 결제 분리**: 결제 대기 중에는 P0 `capacity_hold`만 존재하고, 서버가 승인한 뒤에만 예약을 만든다.
4. **개인정보 비식별화**: 탈퇴 뒤 P1 테이블의 `user_id`와 감사 actor 연결을 제거한다. 이력의 사실·시각·비개인 대상은 보존한다.
5. **외부 원문 미저장**: PortOne V2의 거래·이벤트 ID, 정규화한 검증 결과, 원문 해시만 저장한다. 비밀값·토큰·웹훅 원문·결제수단 전체 정보는 저장하지 않는다.

## 3. ER 다이어그램

계정·권한·지역 ERD는 기존 그림을 유지한다. 그 밖의 그림은 한 그림에 너무 많은 테이블을 넣어 열이 접히거나 관계선이 겹치던 문제를 없애기 위해 도메인별로 분리했다.

- 각 P1 테이블은 **자신이 주로 속한 그림에서 모든 열을 표시**한다.
- 다른 그림에 다시 등장하는 P1 테이블과 P0 재사용 테이블은 관계를 읽기 위한 PK만 보인다. 전체 열은 최초로 등장한 그림에서 확인한다.
- 관계 표기에서 `|`는 정확히 하나, `o`는 0개 허용, `{`는 여러 개를 뜻한다. 예를 들어 `stampbook ||--|{ stampbook_content`는 스탬프북 하나가 대상 콘텐츠를 하나 이상 가진다는 뜻이다.

### 3.1 계정·권한·지역

```mermaid
erDiagram
    app_user ||--o{ platform_admin_assignment : has
    app_user o|--o{ user_role_assignment : has
    region ||--o{ user_role_assignment : scopes
    region ||--o{ audit_event : scopes
    audit_event ||--o| audit_event_actor_link : identifies_actor
    app_user ||--o{ audit_event_actor_link : links

    app_user {
        bigint user_id PK "사용자 식별자; NOT NULL"
        string account_kind "계정 분류: ORDINARY|PRIVILEGED; NOT NULL"
        string status "P0 계정 상태: ACTIVE|WITHDRAWING; NOT NULL"
    }
    platform_admin_assignment {
        bigint platform_admin_assignment_id PK "고권한 배정 식별자; NOT NULL"
        bigint user_id FK "고권한 로그인 계정; 탈퇴 뒤 NULL 가능"
        string grade "등급: SUPER_ADMIN|PLATFORM_ADMIN; NOT NULL"
        string status "배정 상태: ACTIVE|INACTIVE; NOT NULL"
        timestamp granted_at "배정 시각; NOT NULL"
        timestamp inactivated_at "비활성화 시각; ACTIVE면 NULL 가능"
        string inactive_reason_code "비활성화 사유 코드; ACTIVE면 NULL 가능"
    }
    user_role_assignment {
        bigint role_assignment_id PK "일반 역할 배정 식별자; NOT NULL"
        bigint user_id FK "역할을 가진 계정; 탈퇴 뒤 NULL 가능"
        string role "역할: VISITOR|OPERATOR|REGION_ADMIN; NOT NULL"
        bigint region_id FK "담당 지역; VISITOR면 NULL, 그 외 NOT NULL"
        string status "배정 상태: ACTIVE|REVOKED; NOT NULL"
        timestamp granted_at "배정 시각; NOT NULL"
        timestamp revoked_at "회수 시각; ACTIVE면 NULL 가능"
        string revoke_reason_code "회수 사유 코드; ACTIVE면 NULL 가능"
    }
```

### 3.2 스탬프북

`app_user`, `region`, `content`, `visit`은 P0 재사용 테이블이고, `coupon_policy`의 전체 열은 [3.4 쿠폰](#34-쿠폰-발급과-사용)에 있다. 이 그림에는 모든 외래 키의 부모를 관계선과 PK로 표시한다.

```mermaid
erDiagram
    region ||--o{ stampbook : scopes
    coupon_policy ||--o{ stampbook : rewards
    content ||--o{ stampbook_content : targets
    stampbook ||--|{ stampbook_content : contains
    stampbook ||--o{ stampbook_progress : has
    app_user ||--o{ stampbook_progress : progresses
    stampbook_progress ||--o{ stamp_earn : records
    content ||--o{ stamp_earn : earned_for
    visit ||--o{ stamp_earn : proves
    stampbook_progress ||--o| stampbook_reward_grant : completes
    coupon_policy ||--o{ stampbook_reward_grant : rewards

    app_user {
        BIGINT user_id PK "P0 사용자 식별자; NOT NULL"
    }
    region {
        BIGINT region_id PK "P0 지역 식별자; NOT NULL"
    }
    content {
        BIGINT content_id PK "P0 콘텐츠 식별자; NOT NULL"
    }
    visit {
        BIGINT visit_id PK "P0 유효 방문 식별자; NOT NULL"
    }
    coupon_policy {
        BIGINT coupon_policy_id PK "쿠폰 정책 식별자; NOT NULL"
    }
    stampbook {
        BIGINT stampbook_id PK "스탬프북 식별자; NOT NULL"
        BIGINT region_id FK "소속 지역; NOT NULL"
        BIGINT reward_coupon_policy_id FK "완료 보상 쿠폰 정책; NOT NULL"
        VARCHAR status "상태: DRAFT|PENDING_REVIEW|PUBLISHED|ENDED; NOT NULL"
        TIMESTAMP published_at "공개 승인 시각; 공개 전 NULL 가능"
        TIMESTAMP ended_at "종료 시각; 종료 전 NULL 가능"
    }
    stampbook_content {
        BIGINT stampbook_id PK, FK "대상 스탬프북; NOT NULL"
        BIGINT content_id PK, FK "적립 대상 콘텐츠; NOT NULL"
    }
    stampbook_progress {
        BIGINT stampbook_progress_id PK "사용자별 진행 식별자; NOT NULL"
        BIGINT stampbook_id FK "대상 스탬프북; NOT NULL; UNIQUE(user_id 조합)"
        BIGINT user_id FK "참여 사용자; 탈퇴 뒤 NULL 가능; UNIQUE(stampbook_id 조합)"
        VARCHAR status "진행 상태: IN_PROGRESS|COMPLETED|ENDED_INCOMPLETE; NOT NULL"
        TIMESTAMP completed_at "완료 시각; 미완료면 NULL 가능"
    }
    stamp_earn {
        BIGINT stamp_earn_id PK "스탬프 적립 식별자; NOT NULL"
        BIGINT stampbook_progress_id FK "적립 대상 진행; NOT NULL"
        BIGINT visit_id FK "적립을 증명한 유효 방문; NOT NULL; UNIQUE(stampbook_progress_id 조합)"
        BIGINT content_id FK "방문한 대상 콘텐츠; NOT NULL; UNIQUE(stampbook_progress_id 조합)"
        TIMESTAMP earned_at "적립 확정 시각; NOT NULL"
    }
    stampbook_reward_grant {
        BIGINT stampbook_reward_grant_id PK "완료 보상 식별자; NOT NULL"
        BIGINT stampbook_progress_id FK "완료된 스탬프북 진행; NOT NULL"
        BIGINT coupon_policy_id FK "지급할 쿠폰 정책; NOT NULL"
        TIMESTAMP granted_at "보상 지급 시각; NOT NULL"
    }
```

### 3.3 지역 미션

`app_user`, `region`, `content`, `visit`은 P0 재사용 테이블이고, `coupon_policy`의 전체 열은 [3.4 쿠폰](#34-쿠폰-발급과-사용)에 있다. 이 그림에는 모든 외래 키의 부모를 관계선과 PK로 표시한다.

```mermaid
erDiagram
    region ||--o{ mission : scopes
    coupon_policy ||--o{ mission : rewards
    mission ||--o{ mission_target_content : defines
    content ||--o{ mission_target_content : targets
    mission ||--o{ mission_participation : has
    app_user ||--o{ mission_participation : participates
    mission_participation ||--o{ mission_progress : records
    content ||--o{ mission_progress : visited_content
    visit ||--o{ mission_progress : proves
    mission_participation ||--o| mission_reward_claim : claims
    coupon_policy ||--o{ mission_reward_claim : rewards

    app_user {
        BIGINT user_id PK "P0 사용자 식별자; NOT NULL"
    }
    region {
        BIGINT region_id PK "P0 지역 식별자; NOT NULL"
    }
    content {
        BIGINT content_id PK "P0 콘텐츠 식별자; NOT NULL"
    }
    visit {
        BIGINT visit_id PK "P0 유효 방문 식별자; NOT NULL"
    }
    coupon_policy {
        BIGINT coupon_policy_id PK "쿠폰 정책 식별자; NOT NULL"
    }
    mission {
        BIGINT mission_id PK "지역 미션 식별자; NOT NULL"
        BIGINT region_id FK "미션 운영 지역; NOT NULL"
        VARCHAR condition_type "완료 조건: VISIT_COUNT|CONTENT_SET; NOT NULL"
        INT required_visit_count "VISIT_COUNT의 목표 횟수(양수); CONTENT_SET면 NULL"
        BIGINT reward_coupon_policy_id FK "완료 보상 쿠폰 정책; NOT NULL"
        VARCHAR status "상태: DRAFT|PENDING_REVIEW|PUBLISHED|ENDED; NOT NULL"
        TIMESTAMP ends_at "예정 종료 시각; NOT NULL"
        TIMESTAMP published_at "공개 승인 시각; 공개 전 NULL 가능"
        TIMESTAMP ended_at "실제 종료 확정 시각; 종료 전 NULL 가능"
    }
    mission_target_content {
        BIGINT mission_id PK, FK "CONTENT_SET 미션; NOT NULL"
        BIGINT content_id PK, FK "완료에 필요한 콘텐츠; NOT NULL"
    }
    mission_participation {
        BIGINT mission_participation_id PK "사용자 미션 참여 식별자; NOT NULL"
        BIGINT mission_id FK "참여한 미션; NOT NULL"
        BIGINT user_id FK "참여 사용자; 탈퇴 뒤 NULL 가능"
        VARCHAR status "진행 상태: IN_PROGRESS|COMPLETED|ENDED_INCOMPLETE; NOT NULL"
        TIMESTAMP joined_at "사용자 참여 시각; NOT NULL"
        TIMESTAMP completed_at "완료 시각; 미완료면 NULL 가능"
    }
    mission_progress {
        BIGINT mission_participation_id PK, FK "반영 대상 참여; NOT NULL"
        BIGINT visit_id PK, FK "참여 뒤 유효 방문; NOT NULL"
        BIGINT content_id FK "방문 콘텐츠(visit과 일치); NOT NULL"
        TIMESTAMP recorded_at "진행 반영 시각; NOT NULL"
    }
    mission_reward_claim {
        BIGINT mission_reward_claim_id PK "보상 수령 요청 식별자; NOT NULL"
        BIGINT mission_participation_id FK "완료된 미션 참여; NOT NULL; UNIQUE"
        BIGINT coupon_policy_id FK "지급할 쿠폰 정책; NOT NULL"
        TIMESTAMP claimed_at "사용자 수령 요청·지급 시각; NOT NULL"
    }
```

### 3.4 쿠폰 발급과 사용

`mission_reward_claim`, `stampbook_reward_grant`는 [스탬프북](#32-스탬프북)과 [지역 미션](#33-지역-미션) 그림에서 전체 열을 확인한다. 이 그림에서는 세 발급 근거 중 정확히 하나만 `coupon_issuance`에 연결된다는 점을 보여 준다. `app_user`, `region`, `visit`, `reservation`은 P0 재사용 테이블이며, 모든 외래 키의 부모를 관계선과 PK로 표시한다.

```mermaid
erDiagram
    region ||--o{ coupon_policy : scopes
    coupon_policy ||--o{ coupon : governs
    coupon_policy ||--o{ coupon_issuance : issues
    app_user ||--o{ coupon : owns
    app_user ||--o{ coupon_issuance : receives
    coupon ||--|| coupon_issuance : has_source
    visit o|--o{ coupon_issuance : visit_source
    mission_reward_claim o|--o| coupon_issuance : mission_source
    stampbook_reward_grant o|--o| coupon_issuance : stampbook_source
    coupon ||--o{ coupon_status_history : changes
    coupon ||--o| coupon_redemption : redeemed_once
    reservation ||--o| coupon_redemption : consumes

    app_user {
        BIGINT user_id PK "P0 사용자 식별자; NOT NULL"
    }
    region {
        BIGINT region_id PK "P0 지역 식별자; NOT NULL"
    }
    visit {
        BIGINT visit_id PK "P0 유효 방문 식별자; NOT NULL"
    }
    reservation {
        BIGINT reservation_id PK "P0 확정 예약 식별자; NOT NULL"
    }
    mission_reward_claim {
        BIGINT mission_reward_claim_id PK "미션 보상 수령 요청 식별자; NOT NULL"
    }
    stampbook_reward_grant {
        BIGINT stampbook_reward_grant_id PK "스탬프북 완료 보상 식별자; NOT NULL"
    }
    coupon_policy {
        BIGINT coupon_policy_id PK "쿠폰 정책 식별자; NOT NULL"
        BIGINT region_id FK "적용 지역; NOT NULL"
        VARCHAR issuance_type "발급 경로: VISIT|MISSION_REWARD|STAMPBOOK_COMPLETION; NOT NULL"
        BIGINT discount_amount "정액 할인 금액; NOT NULL"
        INT valid_days "발급 뒤 유효 일수; NOT NULL"
        VARCHAR status "상태: DRAFT|PENDING_REVIEW|PUBLISHED|ENDED; NOT NULL"
    }
    coupon {
        BIGINT coupon_id PK "발급 쿠폰 식별자; NOT NULL"
        BIGINT coupon_policy_id FK "적용된 쿠폰 정책; NOT NULL"
        BIGINT user_id FK "보유 사용자; 탈퇴 뒤 NULL 가능"
        VARCHAR status "상태: AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED; NOT NULL"
        TIMESTAMP issued_at "발급 시각; NOT NULL"
        TIMESTAMP expires_at "원래 만료 시각; NOT NULL"
    }
    coupon_issuance {
        BIGINT coupon_issuance_id PK "쿠폰 발급 근거 식별자; NOT NULL"
        BIGINT coupon_id FK "발급된 쿠폰; NOT NULL; UNIQUE"
        BIGINT coupon_policy_id FK "발급 정책(coupon과 일치); NOT NULL"
        BIGINT recipient_user_id FK "수령 사용자(coupon과 일치); 탈퇴 뒤 NULL 가능"
        BIGINT visit_id FK "방문 보상 근거; 다른 근거 사용 시 NULL"
        BIGINT mission_reward_claim_id FK "미션 보상 근거; 다른 근거 사용 시 NULL"
        BIGINT stampbook_reward_grant_id FK "스탬프북 보상 근거; 다른 근거 사용 시 NULL"
        VARCHAR issuance_identity_hash "서버 계산 발급 중복 차단 키; NOT NULL; UNIQUE"
        TIMESTAMP issued_at "발급 근거 확정 시각; NOT NULL"
    }
    coupon_status_history {
        BIGINT coupon_status_history_id PK "쿠폰 상태 이력 식별자; NOT NULL"
        BIGINT coupon_id FK "상태가 바뀐 쿠폰; NOT NULL"
        VARCHAR previous_status "전 상태; 최초 발급 행이면 NULL"
        VARCHAR next_status "후 상태; NOT NULL"
        VARCHAR reason_code "전이 사유 코드; NOT NULL"
        VARCHAR actor_kind "처리 주체 유형; NOT NULL"
        TIMESTAMP occurred_at "상태 전이 시각; NOT NULL"
    }
    coupon_redemption {
        BIGINT coupon_redemption_id PK "쿠폰 사용 확정 식별자; NOT NULL"
        BIGINT coupon_id FK "사용된 쿠폰(쿠폰당 최대 1행); NOT NULL"
        BIGINT reservation_id FK "쿠폰을 소비한 확정 예약; NOT NULL"
        TIMESTAMP redeemed_at "사용 확정 시각; NOT NULL"
    }
```

### 3.5 가격·결제·환불

`app_user`, `capacity_hold`, `reservation`은 P0 재사용 테이블이고, `coupon`의 전체 열은 [3.4 쿠폰](#34-쿠폰-발급과-사용)에 있다. `payment_webhook.payment_id`는 결제 연결을 찾지 못한 웹훅도 남겨야 하므로 nullable이다.

```mermaid
erDiagram
    capacity_hold ||--o| reservation_price_snapshot : snapshots
    coupon o|--o{ reservation_price_snapshot : applies
    capacity_hold ||--o{ payment : starts
    reservation_price_snapshot ||--o{ payment : fixes_price
    payment_idempotency o|--o| payment : returns
    payment o|--o| reservation : confirms
    payment ||--o{ payment_verification : verifies
    payment o|--o{ payment_webhook : receives
    payment ||--o| payment_discrepancy : detects
    payment_discrepancy ||--o{ payment_discrepancy_action : resolves
    payment ||--o| refund : refunds
    refund ||--o{ refund_attempt : retries

    app_user {
        BIGINT user_id PK "P0 사용자 식별자; NOT NULL"
    }
    capacity_hold {
        BIGINT hold_id PK "P0 정원 홀드 식별자; NOT NULL"
        BIGINT user_id FK "P0 홀드 소유자; NOT NULL"
        BIGINT region_id FK "P0 홀드 지역; NOT NULL"
    }
    reservation {
        BIGINT reservation_id PK "P0 확정 예약 식별자; NOT NULL"
    }
    coupon {
        BIGINT coupon_id PK "적용 쿠폰 식별자; NOT NULL"
    }
    reservation_price_snapshot {
        BIGINT reservation_price_snapshot_id PK "홀드 가격 스냅샷 식별자; NOT NULL"
        BIGINT hold_id FK "가격을 고정한 정원 홀드(홀드당 1개); NOT NULL"
        BIGINT coupon_id FK "적용 쿠폰; 쿠폰 미사용이면 NULL"
        BIGINT base_amount "할인 전 기본 금액; NOT NULL"
        BIGINT discount_amount "적용 할인 금액; NOT NULL"
        BIGINT final_amount "결제 최종 금액(0 이상); NOT NULL"
        VARCHAR currency "통화 코드; NOT NULL"
        TIMESTAMP created_at "스냅샷 생성 시각; NOT NULL"
    }
    payment {
        BIGINT payment_id PK "내부 결제 시도 식별자; NOT NULL"
        BIGINT hold_id FK "결제 대상 정원 홀드; NOT NULL"
        BIGINT reservation_price_snapshot_id FK "사용한 불변 가격 스냅샷; NOT NULL"
        BIGINT reservation_id FK "승인 뒤 생성된 확정 예약; 승인 전 NULL"
        VARCHAR order_id "내부 주문 식별자(유일); NOT NULL"
        VARCHAR portone_payment_id "PortOne V2 거래 ID; 결제 시작 전 NULL 가능"
        VARCHAR status "상태: PENDING|APPROVED|DECLINED|CANCELLED|EXPIRED|DISCREPANT; NOT NULL"
        TIMESTAMP finalized_at "종결 시각; PENDING이면 NULL 가능"
    }
    payment_idempotency {
        BIGINT payment_idempotency_id PK "결제 생성 멱등 기록 식별자; NOT NULL"
        BIGINT actor_user_id "생성 때 활성 계정을 검증해 기록한 요청자 식별값; FK 아님; 만료 전까지 유지; UNIQUE(operation·key 조합)"
        VARCHAR operation "PAYMENT_CREATE만 허용; NOT NULL; UNIQUE(actor·key 조합)"
        VARCHAR idempotency_key_hash "Idempotency-Key 해시; NOT NULL; UNIQUE(actor·operation 조합)"
        VARCHAR request_hash "정규화한 결제 생성 요청 해시; NOT NULL"
        VARCHAR status "처리 상태: PROCESSING|SUCCEEDED|FAILED; NOT NULL"
        BIGINT payment_id FK "성공한 결제 생성 결과; 처리 중·실패면 NULL; UNIQUE"
        TIMESTAMP completed_at "결제 생성 요청 완료 시각; 처리 중 NULL 가능"
        TIMESTAMP expires_at "결제 종결 뒤 24시간 만료; PENDING 결제면 NULL"
    }
    payment_verification {
        BIGINT payment_verification_id PK "서버 검증 이력 식별자; NOT NULL"
        BIGINT payment_id FK "검증한 결제 시도; NOT NULL"
        VARCHAR verification_reason "검증을 시작한 사유; NOT NULL"
        BIGINT observed_amount "외부 조회 금액; NOT NULL"
        VARCHAR observed_currency "외부 조회 통화; NOT NULL"
        VARCHAR observed_order_id "외부 조회 주문 ID; NOT NULL"
        VARCHAR external_status "외부 결제 상태; NOT NULL"
        VARCHAR internal_decision "서버 검증 판정; NOT NULL"
        VARCHAR response_hash "검증 응답 원문 해시; NOT NULL"
        TIMESTAMP verified_at "검증 시각; NOT NULL"
    }
    payment_webhook {
        BIGINT payment_webhook_id PK "웹훅 수신 이력 식별자; NOT NULL"
        VARCHAR provider_event_id "PortOne 이벤트 ID(유일); NOT NULL"
        BIGINT payment_id FK "연결된 결제; 미결합 웹훅이면 NULL"
        VARCHAR authentication_result "웹훅 인증 결과; NOT NULL"
        VARCHAR processing_result "내부 처리 결과; NOT NULL"
        VARCHAR payload_hash "웹훅 원문 해시; NOT NULL"
        TIMESTAMP received_at "수신 시각; NOT NULL"
    }
    payment_discrepancy {
        BIGINT payment_discrepancy_id PK "결제 불일치 식별자; NOT NULL"
        BIGINT payment_id FK "조사 대상 결제; NOT NULL"
        VARCHAR discrepancy_type "불일치 유형; NOT NULL"
        VARCHAR status "불일치 조사 상태; NOT NULL"
        TIMESTAMP detected_at "불일치 감지 시각; NOT NULL"
    }
    payment_discrepancy_action {
        BIGINT payment_discrepancy_action_id PK "불일치 수동 조치 식별자; NOT NULL"
        BIGINT payment_discrepancy_id FK "대상 불일치; NOT NULL"
        VARCHAR action_type "처리 조치 유형; NOT NULL"
        VARCHAR evidence_reference "비밀값 없는 증빙 참조; NULL 가능"
        VARCHAR reason_code "조치 사유 코드; NOT NULL"
        VARCHAR result_code "조치 결과 코드; NOT NULL"
        TIMESTAMP acted_at "조치 시각; NOT NULL"
    }
    refund {
        BIGINT refund_id PK "전액 환불 식별자; NOT NULL"
        BIGINT payment_id FK "환불할 승인 결제(결제당 최대 1행); NOT NULL"
        BIGINT amount "환불 금액(결제 최종 금액과 동일); NOT NULL"
        VARCHAR status "상태: REQUESTED|PROCESSING|SUCCEEDED|FAILED|DISCREPANT; NOT NULL"
        TIMESTAMP requested_at "환불 요청 시각; NOT NULL"
        TIMESTAMP completed_at "환불 종결 시각; 종결 전 NULL 가능"
    }
    refund_attempt {
        BIGINT refund_attempt_id PK "외부 환불 시도 식별자; NOT NULL"
        BIGINT refund_id FK "대상 환불; NOT NULL"
        INT attempt_no "시도 순번: 1~3; NOT NULL"
        VARCHAR initiator_kind "시작 주체: SYSTEM|SUPER_ADMIN|PLATFORM_ADMIN; NOT NULL"
        VARCHAR portone_cancellation_id "PortOne 취소 ID; 외부 호출 전 NULL 가능"
        VARCHAR outcome_kind "호출 결과: PENDING|RESPONDED|NO_RESPONSE; NOT NULL"
        VARCHAR failure_reason_code "응답 미수신 사유: TIMEOUT|CONNECTION|NETWORK|UNKNOWN; NO_RESPONSE일 때만 NOT NULL"
        VARCHAR external_status "외부 환불 상태; RESPONDED일 때만 NOT NULL"
        VARCHAR result_hash "외부 응답 원문 해시; RESPONDED일 때만 NOT NULL"
        TIMESTAMP attempted_at "외부 환불 시도 시각; NOT NULL"
    }
```

## 4. P0 확장

### 4.1 `app_user`

| 추가 열 | 값 | 규칙 |
| --- | --- | --- |
| `account_kind` | `ORDINARY`, `PRIVILEGED` | 계정 생성 시 설정한 뒤 바꾸지 않는다. 기존 P0 계정은 모두 `ORDINARY`로 이관한다. |

`PRIVILEGED` 계정은 일반 역할을, `ORDINARY` 계정은 고권한 배정을 가질 수 없다. 고권한 배정이 `INACTIVE`가 되어도 계정 분류는 바뀌지 않는다.

### 4.2 `platform_admin_assignment`

| 열 | 설명 |
| --- | --- |
| `platform_admin_assignment_id` | 고권한 배정 식별자 |
| `user_id` | 고권한 로그인 계정. 탈퇴 완료 전 `NULL`로 비식별화 |
| `grade` | `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` |
| `status` | `ACTIVE` 또는 `INACTIVE` |
| `granted_at`, `inactivated_at` | 생성·비활성화 시각 |
| `inactive_reason_code` | 비활성화 사유 코드 |

계정 상태 변경 처리자·증빙·요청 ID는 이 행에 중복 저장하지 않고 `audit_event`에 남긴다. 활성 슈퍼관리자는 최소 한 명, 자기 상태 변경은 금지한다.

### 4.3 `user_role_assignment` 이력화

P0의 복합 PK `(user_id, role)`를 `role_assignment_id`로 대체한다. 역할의 현재 권한은 `status = ACTIVE` 행만 사용하고, 회수는 행 삭제가 아니라 `REVOKED` 전환이다.

| 제약 | 의미 |
| --- | --- |
| 활성 `VISITOR` | `region_id IS NULL` |
| 활성 `OPERATOR`, `REGION_ADMIN` | `region_id IS NOT NULL` |
| 활성 역할 배정 | P0의 역할별 담당 지역 최대 한 곳을 유지 |
| 활성 `REGION_ADMIN` | 같은 지역에 복수 허용 |
| 마지막 지역 관리자 | `content.deleted_at IS NULL` 콘텐츠가 있는 지역은 마지막 활성 배정 회수 금지 |

### 4.4 `region`

새 열을 추가하지 않는다. `is_public = true → false`는 비삭제 콘텐츠가 하나도 없을 때만 허용한다. `SUSPENDED` 같은 새 상태를 도입하지 않는다.

### 4.5 `audit_event` 확장

| 추가·확장 항목 | 규칙 |
| --- | --- |
| `target_type` | 기존 P0 값에 `PLATFORM_ADMIN_ASSIGNMENT`, `USER_ROLE_ASSIGNMENT`, `STAMPBOOK`, `MISSION`, `COUPON_POLICY`, `COUPON`, `RESERVATION_PRICE_SNAPSHOT`, `PAYMENT`, `REFUND`, `PAYMENT_DISCREPANCY`를 추가 |
| `evidence_reference` | 특권 변경·수동 거래 처리의 비밀값 없는 증빙 참조. nullable |
| `audit_event_actor_link` | 활성 actor에만 만든다. 탈퇴 전 제거한다. |

`target_id`에는 `app_user.user_id`를 저장하지 않는다. 고권한 계정 변경의 대상은 `platform_admin_assignment`이다. P0의 90일 공통 감사 보관 기간을 특권·거래 감사에도 그대로 적용할지는 [미확정 항목](#11-미확정-연결-정책)에 남긴다.

## 5. 혜택 도메인

### 5.1 공개 정책 공통 수명주기

`stampbook`, `mission`, `coupon_policy`는 공통으로 다음 상태를 사용한다.

```text
DRAFT → PENDING_REVIEW → PUBLISHED → ENDED
                 └────→ DRAFT  (반려 뒤 수정)
```

- 콘텐츠 운영자가 담당 범위에서 작성·검토 요청한다.
- 담당 지역 관리자가 승인해야 `PUBLISHED`가 된다.
- 공개 뒤 핵심 값은 수정하지 않고 종료만 허용한다.
- 전이는 대상·처리자·사유·시각·요청 ID와 함께 감사한다.

### 5.2 스탬프북

| 테이블 | 핵심 열 | 책임 |
| --- | --- | --- |
| `stampbook` | `stampbook_id`, `region_id`, `reward_coupon_policy_id`, 상태·공개·종료 시각 | 지역 단위 스탬프북의 현재 상태와 보상 정책 |
| `stampbook_content` | `stampbook_id`, `content_id` | 적립 대상 콘텐츠 목록 |
| `stampbook_progress` | `stampbook_progress_id`, `stampbook_id`, `user_id`, `status`, `completed_at` | 사용자별 현재 진행·완료·종료 미완료 상태 |
| `stamp_earn` | `stamp_earn_id`, `stampbook_progress_id`, `visit_id`, `content_id`, `earned_at` | 콘텐츠별 한 번의 스탬프 근거 |
| `stampbook_reward_grant` | `stampbook_reward_grant_id`, `stampbook_progress_id`, `coupon_policy_id`, `granted_at` | 완료에 따른 쿠폰 보상 자격·지급 근거 |

`stampbook_content`의 행 수가 목표 스탬프 수다. 사용자는 대상 콘텐츠별로 한 스탬프만 받는다. 같은 콘텐츠의 다른 회차 방문은 추가 스탬프가 아니다.

| 무결성 | 규칙 |
| --- | --- |
| 대상 콘텐츠 | `content.region_id = stampbook.region_id` |
| 적립 원본 | `visit.content_id = stamp_earn.content_id`, `visit.user_id = stampbook_progress.user_id` |
| 사용자 진행 | `UNIQUE (stampbook_id, user_id)`로 활성 사용자의 스탬프북 진행 행을 하나만 둔다. |
| 중복 차단 | `UNIQUE (stampbook_progress_id, content_id)`로 콘텐츠별 한 번 적립하고, `UNIQUE (stampbook_progress_id, visit_id)`로 같은 방문 재전달을 막는다. 두 제약은 모두 `stamp_earn`의 실제 열로 만든다. |
| 완료 보상 | `UNIQUE (stampbook_progress_id)` |
| 상태 | `IN_PROGRESS → COMPLETED` 또는 미완료 상태에서 종료 시 `ENDED_INCOMPLETE` |

적립은 진행 행 생성·조회와 `stamp_earn` 삽입을 같은 트랜잭션에서 처리한다. 유일 키 충돌은 기존 적립 결과를 반환하며, 중복 행을 새로 만들지 않는다.

### 5.3 지역 미션

| 테이블 | 핵심 열 | 책임 |
| --- | --- | --- |
| `mission` | `mission_id`, `region_id`, `condition_type`, `required_visit_count`, `reward_coupon_policy_id`, 상태·예정·실제 종료 시각 | 미션 조건과 공개·종료 상태 |
| `mission_target_content` | `mission_id`, `content_id` | `CONTENT_SET` 미션의 지정 콘텐츠 |
| `mission_participation` | `mission_participation_id`, `mission_id`, `user_id`, `status`, `joined_at`, `completed_at` | 사용자의 명시적 참여와 완료 자격 |
| `mission_progress` | `mission_participation_id`, `visit_id`, `content_id`, `recorded_at` | 참여 뒤 방문의 진행 근거 |
| `mission_reward_claim` | `mission_reward_claim_id`, `mission_participation_id`, `coupon_policy_id`, `claimed_at` | 완료 사용자의 멱등 보상 수령 결과 |

| 조건 유형 | 저장 규칙 |
| --- | --- |
| `VISIT_COUNT` | `required_visit_count > 0`, 목표 콘텐츠 행 없음. 참여 뒤 같은 지역의 모든 유효 방문을 센다. |
| `CONTENT_SET` | `required_visit_count IS NULL`, 목표 콘텐츠 행이 하나 이상. 지정 콘텐츠를 각각 한 번 방문해야 완료한다. |

| 무결성 | 규칙 |
| --- | --- |
| 예정 종료 | `ends_at`은 필수다. `PUBLISHED` 전이 때 `published_at < ends_at`를 검증하며, 공개 뒤 수정하지 않는다. |
| 자동 종료 | 종료 작업은 `status = PUBLISHED AND ends_at <= 현재 시각`인 행만 `ENDED`로 조건부 전이하고, 그 실제 처리 시각을 `ended_at`에 기록한다. |
| 참여 멱등성 | `UNIQUE (mission_id, user_id)`로 사용자·미션당 참여 행을 하나만 허용한다. 중복 키 요청은 기존 참여 결과를 반환한다. |
| 보상 수령 멱등성 | `UNIQUE (mission_participation_id)`로 참여당 보상 수령 행을 하나만 허용한다. 중복 키 요청은 기존 수령 결과를 반환한다. |
| 진행도 | `UNIQUE (mission_participation_id, visit_id)`로 같은 참여에 같은 방문을 두 번 반영하지 않는다. |

같은 유효 방문은 조건을 만족하는 여러 공개 미션에 각각 반영할 수 있다. `mission_progress.content_id`를 유지하므로 `visit.content_id = mission_progress.content_id`와 `visit.user_id = mission_participation.user_id`를 함께 검증한다. 참여·진행도 반영은 모두 `status = PUBLISHED AND ends_at > 현재 시각`인 미션에만 허용한다. 따라서 종료 작업이 지연돼도 종료 시각 뒤 신규 참여·진행도는 생기지 않는다.

### 5.4 쿠폰 정책과 쿠폰

| 테이블 | 핵심 열 | 책임 |
| --- | --- | --- |
| `coupon_policy` | `coupon_policy_id`, `region_id`, `issuance_type`, `discount_amount`, `valid_days`, 상태 | 지역 전체 유료 콘텐츠에 적용되는 정액 할인·발급 규칙 |
| `coupon` | `coupon_id`, `coupon_policy_id`, `user_id`, `status`, `issued_at`, `expires_at` | 사용자가 보유한 현재 쿠폰 상태 |
| `coupon_issuance` | `coupon_issuance_id`, `coupon_id`, `coupon_policy_id`, `recipient_user_id`, 발급 근거 FK 3종, 발급 식별 키, `issued_at` | 발급 근거와 중복 지급 차단 |
| `coupon_status_history` | `coupon_status_history_id`, `coupon_id`, 이전·이후 상태, 사유, actor 종류, 시각 | 모든 쿠폰 상태 전이의 추가 전용 이력 |
| `coupon_redemption` | `coupon_redemption_id`, `coupon_id`, `reservation_id`, `redeemed_at` | 예약 확정에 따른 한 번의 사용 사실 |

`coupon_policy.issuance_type`은 다음 중 하나다.

| 값 | 발급 근거 | 추가 제한 |
| --- | --- | --- |
| `VISIT` | `coupon_issuance.visit_id` | 사용자당 정책별 한 장만 발급 |
| `MISSION_REWARD` | `coupon_issuance.mission_reward_claim_id` | 연결 미션 보상 수령 결과당 한 장 |
| `STAMPBOOK_COMPLETION` | `coupon_issuance.stampbook_reward_grant_id` | 연결 스탬프북 완료 보상당 한 장 |

`coupon_issuance`에는 세 근거 FK 중 정확히 하나만 존재해야 한다. 근거 유형은 정책의 `issuance_type`과 같고, `coupon_policy_id`·`recipient_user_id`는 연결한 `coupon`의 정책·소유자와 각각 일치해야 한다.

발급 근거와 정책·수령자도 일치해야 한다. `VISIT`은 `visit.user_id = recipient_user_id`를, `MISSION_REWARD`는 수령 행의 `coupon_policy_id`·참여 사용자 일치를, `STAMPBOOK_COMPLETION`은 완료 보상 행의 `coupon_policy_id`·진행 사용자 일치를 각각 검증한다.

`issuance_identity_hash`는 서버가 발급 경로별로 결정적으로 계산한다. `VISIT`은 `(coupon_policy_id, recipient_user_id)`, `MISSION_REWARD`는 `(coupon_policy_id, recipient_user_id, mission_reward_claim_id)`, `STAMPBOOK_COMPLETION`은 `(coupon_policy_id, recipient_user_id, stampbook_reward_grant_id)`를 입력으로 사용한다. `UNIQUE (issuance_identity_hash)`와 `UNIQUE (coupon_id)`가 같은 발급을 한 쿠폰으로 수렴시킨다. 추가로 `UNIQUE (mission_reward_claim_id)`, `UNIQUE (stampbook_reward_grant_id)`를 둔다.

새 쿠폰 발급은 `coupon_policy` 행을 잠근 뒤 `status = PUBLISHED`일 때만 시작한다. `DRAFT`·`PENDING_REVIEW`·`ENDED` 정책은 발급 근거가 될 수 없다. 쿠폰 생성·발급 이력·최초 `AVAILABLE` 상태 이력은 이 검증과 같은 트랜잭션으로 처리한다. 발급 식별 키 충돌은 기존 쿠폰과 발급 이력을 반환하며, 서로 다른 재전달 식별자가 와도 새 쿠폰을 만들지 않는다.

쿠폰 상태 전이는 다음과 같다.

```text
발급:              NULL → AVAILABLE
결제 시작:         AVAILABLE → RESERVED
결제 승인·예약확정: RESERVED → USED
결제 실패·취소:    RESERVED → AVAILABLE
홀드 만료:         RESERVED → AVAILABLE 또는 EXPIRED
회차 시작 전 취소: USED → AVAILABLE 또는 EXPIRED
만료 배치:         AVAILABLE → EXPIRED
회원 탈퇴:         AVAILABLE 또는 RESERVED → INVALIDATED
```

`USED`는 결제·예약 취소가 아닌 한 다시 바꾸지 않는다. `EXPIRED`, `INVALIDATED`는 종결 상태다. 각 전이는 `coupon_status_history`와 같은 트랜잭션에서 기록한다.

## 6. 가격·결제·환불

### 6.1 예약 가격 스냅샷

| 테이블 | 핵심 열 | 책임 |
| --- | --- | --- |
| `reservation_price_snapshot` | `reservation_price_snapshot_id`, `hold_id`, `coupon_id`, `base_amount`, `discount_amount`, `final_amount`, `currency`, `created_at` | 홀드 기준 가격·쿠폰·최종 금액의 불변 스냅샷 |

`UNIQUE (hold_id)`이다. 같은 홀드의 결제 재시도는 같은 스냅샷을 사용한다. 쿠폰은 최대 하나만 연결할 수 있고, `base_amount - discount_amount = final_amount`, `final_amount >= 0`을 만족한다.

쿠폰을 적용하면 홀드를 잠근 뒤 `coupon.user_id = capacity_hold.user_id`, `coupon_policy.region_id = capacity_hold.region_id`, 쿠폰 `AVAILABLE` 상태와 `expires_at > 현재 시각`을 모두 검증한다. 이미 발급된 쿠폰은 정책 종료 뒤에도 자체 만료 시각까지 사용하므로, 이 사용 검증에서 `coupon_policy.status`를 다시 `PUBLISHED`로 요구하지 않는다.

새 스냅샷은 홀드 잠금·쿠폰의 조건부 `AVAILABLE → RESERVED` 전이·스냅샷 삽입을 같은 트랜잭션에서 처리한다. 같은 홀드의 스냅샷이 있으면 이를 반환하고 쿠폰 상태를 다시 바꾸지 않는다. 조건부 전이에 실패하면 스냅샷을 만들지 않는다. 최종 금액이 0이면 이 트랜잭션에서 홀드 소비·`reservation(CONFIRMED)`·쿠폰 `USED`까지 처리하며, 양수 금액은 서버 검증 승인 때 이 세 변경을 같은 트랜잭션으로 처리한다.

### 6.2 결제와 검증

| 테이블 | 핵심 열 | 책임 |
| --- | --- | --- |
| `payment` | `payment_id`, `hold_id`, `reservation_price_snapshot_id`, `reservation_id`, `order_id`, `portone_payment_id`, 상태·종결 시각 | 외부 결제가 필요한 내부 결제 시도 |
| `payment_idempotency` | `payment_idempotency_id`, 요청자·연산·키·요청 해시, 처리 상태, `payment_id`, 만료 시각 | 결제 생성 요청의 동시 실행·재시도 결과 |
| `payment_verification` | `payment_verification_id`, `payment_id`, 검증 원인·관측 금액·통화·주문 ID·외부 상태·내부 판정·해시 | 서버 조회 결과와 판정 근거 |
| `payment_webhook` | `payment_webhook_id`, `provider_event_id`, `payment_id`, 인증·처리 결과·원문 해시·수신 시각 | 웹훅 재전송 차단과 처리 감사 |
| `payment_discrepancy` | `payment_discrepancy_id`, `payment_id`, 유형·상태·관측 시각 | 결제 불일치의 현재 조사 상태 |
| `payment_discrepancy_action` | `payment_discrepancy_action_id`, `payment_discrepancy_id`, 조치·증빙 참조·사유·결과·시각 | 수동 조사·종결의 상세 이력 |

| 결제 제약 | 규칙 |
| --- | --- |
| 주문 식별자 | `UNIQUE (order_id)` |
| PortOne 거래 | 값이 존재하면 `UNIQUE (portone_payment_id)` |
| 홀드 연결 | 모든 결제는 `hold_id`와 가격 스냅샷을 반드시 가진다. 예약은 승인 전 nullable |
| 진행 중 결제 | 홀드당 `PENDING` 결제는 최대 하나 |
| 멱등 키 | `UNIQUE (actor_user_id, operation, idempotency_key_hash)`, `CHECK (operation = 'PAYMENT_CREATE')`, `UNIQUE (payment_id)`를 `payment_idempotency`에 둔다. `actor_user_id`는 FK가 아니며, 생성 시 잠근 활성 계정과 `capacity_hold.user_id`의 일치를 검증해 기록한다. |
| 멱등 처리 | 먼저 멱등 기록을 점유하고 같은 키·같은 `request_hash`면 기존 결제 또는 진행 중 결과를 반환한다. 같은 키·다른 요청 해시는 충돌로 거부한다. 다른 키로 이미 `PENDING`인 홀드를 시작하려 하면 진행 중 오류를 반환한다. |
| 보관 | 결제 종결 시 `expires_at = payment.finalized_at + 24시간`으로 정하고, 만료한 종결 기록만 정리한다. 결제 생성 전 실패는 `completed_at + 24시간`으로 정리한다. 탈퇴는 `app_user` 행만 파기하며, 이 비-FK 식별값·키 해시·요청 해시는 만료 전까지 그대로 두고 행 전체를 삭제한다. |
| 웹훅 | `UNIQUE (provider_event_id)`. 결제 연결을 찾지 못해도 수신·인증 결과는 남긴다. |

결제 상태는 다음과 같다.

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 외부 결제 진행 또는 서버 검증 대기 |
| `APPROVED` | 서버가 PortOne V2 조회로 주문·금액·통화를 검증했고 예약 확정까지 성공 |
| `DECLINED` | 외부 결제 거절 |
| `CANCELLED` | 사용자가 결제를 취소 |
| `EXPIRED` | 홀드 만료로 결제 가능 시간이 끝남 |
| `DISCREPANT` | 늦은 승인·금액·주문·대상 불일치로 수동 조사 필요 |

`APPROVED`는 `reservation` 생성·홀드 `CONSUMED` 전이와 같은 트랜잭션에서만 된다. 클라이언트 성공 표시는 승인 근거가 아니다. `CANCELLED` 뒤 늦은 외부 성공은 `DISCREPANT`이며 예약을 되살리지 않는다.

### 6.3 환불

| 테이블 | 핵심 열 | 책임 |
| --- | --- | --- |
| `refund` | `refund_id`, `payment_id`, `amount`, `status`, `requested_at`, `completed_at` | 승인 결제 한 건의 전액 환불 현재 상태 |
| `refund_attempt` | `refund_attempt_id`, `refund_id`, `attempt_no`, `initiator_kind`, 호출 결과·응답 미수신 사유·외부 취소 ID·상태·결과 해시·시각 | 외부 환불 호출의 개별 시도 |

| 무결성 | 규칙 |
| --- | --- |
| 환불 수 | `UNIQUE (payment_id)` — 승인 결제당 환불은 최대 한 건 |
| 금액 | `refund.amount = reservation_price_snapshot.final_amount` |
| 시도 번호 | `UNIQUE (refund_id, attempt_no)`, `1 ≤ attempt_no ≤ 3` |
| 호출 이력 | 외부 호출 직전에 `PENDING` 시도 행을 먼저 만들고 `attempt_no`를 점유한다. 응답 수신은 `RESPONDED`, 타임아웃·연결·네트워크 실패처럼 응답을 받지 못한 호출은 `NO_RESPONSE`로 확정한다. `NO_RESPONSE`도 시도 횟수에 포함한다. |
| 응답 값 | `RESPONDED`이면 `external_status`, `result_hash`는 모두 NOT NULL이고 `failure_reason_code`는 NULL이다. `NO_RESPONSE`이면 `failure_reason_code`는 NOT NULL이고 두 응답 값은 NULL이다. `PENDING`이면 셋 모두 NULL이다. |
| 재시도 | 최초 시도는 `SYSTEM`, 이후는 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`만. 자동 재시도 금지 |
| 응답 미수신 | `NO_RESPONSE`는 `refund`를 `DISCREPANT`로 전이한다. PortOne 재조회 증빙으로 실제 환불 미처리가 확인된 경우에만 `FAILED`로 전이해 남은 횟수 안에서 수동 재시도할 수 있다. 성공·미확인은 각각 `SUCCEEDED`·`DISCREPANT`를 유지한다. |
| 쿠폰 복구 | 회차 시작 전 유효 취소와 환불 성공에만 원래 만료 시각을 유지해 복구 |

환불 상태 전이는 `REQUESTED → PROCESSING → SUCCEEDED | FAILED | DISCREPANT`다. 환불 성공은 `payment` 상태에 `REFUNDED`를 추가하지 않는다.

## 7. 핵심 처리 흐름

### 7.1 양수 금액 결제

```text
ACTIVE capacity_hold
  → payment_idempotency 멱등 기록 점유
  → reservation_price_snapshot 생성 + 쿠폰 AVAILABLE→RESERVED
  → payment(PENDING) 생성 및 멱등 기록에 연결
  → PortOne V2 결제 및 서버 재조회
  → 검증 성공: hold CONSUMED + reservation CONFIRMED + coupon USED + payment APPROVED
  → 거절·취소·만료: payment 종결 + 쿠폰 복구 + 필요 시 hold 정원 복구
```

### 7.2 0원 예약

```text
ACTIVE capacity_hold
  → reservation_price_snapshot(final_amount = 0) 생성
  → hold CONSUMED + reservation CONFIRMED + coupon USED
```

P0 무료 예약 확정 흐름을 사용하며 `payment`·PortOne 호출·웹훅 행을 만들지 않는다.

### 7.3 결제 불일치와 환불 실패

```text
DISCREPANT payment 또는 FAILED/DISCREPANT refund
  → 전체관리자 증빙·사유 입력
  → 문제 없음 종결 또는 전액 환불 요청
  → payment_discrepancy_action + audit_event 추가
```

수동 관리자는 외부 결제를 내부적으로 승인 처리하거나 취소된 예약을 강제로 확정하지 못한다.

환불 외부 호출은 `refund_attempt(PENDING, attempt_no)`를 먼저 기록한 뒤 시작한다. 응답을 받지 못하면 해당 행을 `NO_RESPONSE`와 비밀값 없는 실패 사유로 확정하고 `refund`를 `DISCREPANT`로 전이한다. 이 시도도 총 3회에 포함하며, PortOne 재조회로 미처리가 확인되기 전에는 다음 외부 환불을 호출하지 않는다.

### 7.4 미션 자동 종료

```text
PUBLISHED mission AND ends_at <= 현재 시각
  → status = ENDED, ended_at = 실제 처리 시각으로 조건부 전이
  → 미완료 participation은 ENDED_INCOMPLETE로 전이
  → audit_event 기록
```

참여·진행도 반영은 `ends_at`을 함께 검사하므로, 종료 배치가 늦게 실행돼도 예정 종료 시각 뒤에는 새 참여·진행도가 허용되지 않는다.

## 8. 회원 탈퇴와 비식별화

| 대상 | 탈퇴 처리 |
| --- | --- |
| 미종결 유료 예약·진행 환불 | 탈퇴 요청 거부. 먼저 취소·환불을 종결해야 함 |
| 무료 미체크인 예약·활성 홀드 | P0 탈퇴 정책대로 종결 |
| `coupon`, `coupon_issuance` | `AVAILABLE`·`RESERVED`는 `INVALIDATED`; 이후 각각 `user_id`, `recipient_user_id` 제거 |
| 스탬프·미션 진행·근거 | `user_id`를 제거하고 비개인 진행 사실·방문 근거 유지 |
| 결제·환불 | 법정 보관용 분리 거래 기록을 제외한 운영 DB의 `user_id` 제거. `payment_idempotency.actor_user_id`는 `app_user` FK가 아닌 생성 당시 식별값으로 종결·만료 정리 전만 유지 |
| 권한 배정 | 활성 고권한·운영 역할은 먼저 종결. 이력의 `user_id`는 제거 |
| 감사 | `audit_event_actor_link` 제거. `target_id`는 처음부터 사용자 ID를 저장하지 않음 |

탈퇴 완료 뒤 새 계정이 과거 쿠폰·진행·권한을 되찾는 경로를 만들지 않는다.

## 9. 물리 제약과 인덱스 우선순위

| 우선순위 | 대상 | 제약·인덱스 |
| --- | --- | --- |
| 높음 | `platform_admin_assignment` | 활성 고권한 사용자당 한 배정, 활성 슈퍼 최소 한 명은 조건부 쓰기로 보호 |
| 높음 | `user_role_assignment` | 활성 역할의 사용자·역할·지역 범위 유일성, 지역·상태 조회 인덱스 |
| 높음 | `stampbook_progress`, `stamp_earn` | `UNIQUE (stampbook_id, user_id)`, `UNIQUE (stampbook_progress_id, visit_id)`, 콘텐츠별 적립 유일 제약 |
| 높음 | `mission_progress` | 방문 근거 중복 차단 복합 유일 제약 |
| 높음 | `mission_participation` | `UNIQUE (mission_id, user_id)` — 사용자·미션당 참여 하나 |
| 높음 | `mission_reward_claim` | `UNIQUE (mission_participation_id)` — 참여당 보상 수령 하나 |
| 높음 | `coupon_issuance` | `coupon_id`, 발급 식별 키, `mission_reward_claim_id`, `stampbook_reward_grant_id` 유일; 세 FK 중 정확히 하나 CHECK |
| 높음 | `coupon_redemption` | `UNIQUE (coupon_id)` |
| 높음 | `reservation_price_snapshot` | `UNIQUE (hold_id)`, 금액 CHECK |
| 높음 | `payment` | `order_id`, `portone_payment_id` 유일; 홀드당 진행 중 결제 하나 |
| 높음 | `payment_idempotency` | `(actor_user_id, operation, idempotency_key_hash)`, `payment_id` 유일; `actor_user_id`는 비-FK; `expires_at` 정리 인덱스 |
| 높음 | `payment_webhook` | `provider_event_id` 유일 |
| 높음 | `refund_attempt` | `(refund_id, attempt_no)` 유일, `1 ≤ attempt_no ≤ 3`, `outcome_kind`별 응답 값 NULL/NOT NULL CHECK |
| 중간 | `coupon(status, expires_at)` | 만료 배치와 내 쿠폰 목록 |
| 중간 | `mission(status, ends_at)` | 자동 종료 후보 조회와 공개 미션 기간 판정 |
| 중간 | `audit_event(target_type, target_id, occurred_at)` | 특권·거래 조사 |

MySQL의 조건부 유일 제약이 필요한 활성 배정·진행 중 결제는 생성 열을 포함한 유일 인덱스 또는 동등한 조건부 쓰기와 통합 테스트로 강제한다.

## 10. P1에서 제외한 테이블·기능

- `PENDING_PAYMENT` 예약 상태 또는 결제 대기 전용 예약 테이블
- 다중 쿠폰 조합·쿠폰 지갑·정률 할인·최소 결제 금액·부분 환불
- 포인트·현금성 보상·수동 쿠폰 지급
- PortOne 웹훅 원문·API Secret·결제수단 원문 저장소
- 자동 환불 재시도·환불 이중 승인
- 지역 `SUSPENDED` 상태와 지역 전체 중단 기능
- 미래 시작 예약·사전 노출 미션

## 11. 미확정 연결 정책

다음은 테이블 구조가 아니라 이미 확정된 정책들 사이의 권리 경계를 정하는 항목이다. 구현·프로젝트 기준 문서 반영 전에 사용자 결정을 받아야 한다.

1. 종료된 미션에서 이미 `COMPLETED`지만 아직 보상 수령을 요청하지 않은 사용자의 수령 가능 기간
2. 공개 중인 미션·스탬프북이 보상으로 참조한 `coupon_policy`를 `ENDED`로 바꾸려 할 때의 차단·유예·대체 규칙
3. P0 공통 감사의 90일 보관 기간을 고권한·거래 감사에도 적용할지, 별도 보관 기간을 둘지

## 12. 프로젝트 반영 순서

1. ADR-0064~ADR-0071의 상태를 프로젝트 채택 절차에 맞춰 확정한다.
2. 위 미확정 연결 정책을 결정한다.
3. 프로젝트의 P1 정책 문서·API 계약과 이 ERD의 상태·제약을 같은 변경에서 일치시킨다.
4. 그 뒤 migration·코드·통합 테스트를 구현한다.
