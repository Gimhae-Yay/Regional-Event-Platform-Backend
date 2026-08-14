# 스탬프북 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), [P1-FR-02](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`~`STB-04` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0067](../../../adr/0067-model-stampbook-and-mission-progress-from-immutable-visits.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 운영자의 스탬프북 생성·수정·공개 심사 요청·종료, 지역 관리자의 심사 대기 조회·승인·반려와
방문자의 목록·상세·적립 이력 조회 HTTP 계약을 정의한다. 적립은 P0의 유효 방문 기록을 근거로 한 번만 반영하며,
조회 API들은 스탬프·진행도·쿠폰·감사 이력을 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-02, STB-04 | `GET /api/v1/me/stampbooks` | `stampbook_progress`, `stampbook`, `stamp_earn` |
| P1-FR-02, STB-04 | `GET /api/v1/me/stampbooks/{stampbookId}` | `stampbook_progress`, `stampbook_content`, `stamp_earn` |
| P1-FR-02, STB-03, STB-04 | `GET /api/v1/me/stampbooks/{stampbookId}/earnings` | `stamp_earn`, `visit`, `content` |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/operator/stampbooks` | `stampbook`, `stampbook_content`, `audit_event` |
| P1-FR-01, STB-01, STB-02 | `PATCH /api/v1/operator/stampbooks/{stampbookId}` | `stampbook`, `stampbook_content`, `audit_event` |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/operator/stampbooks/{stampbookId}/publish` | `stampbook.status`, `audit_event` |
| P1-FR-01, STB-01, STB-02 | `GET /api/v1/region-admin/stampbooks?status=PENDING_REVIEW` | `stampbook`, `stampbook_content`, `audit_event` |
| P1-FR-01, STB-01, STB-02 | `GET /api/v1/region-admin/stampbooks/{stampbookId}` | `stampbook`, `stampbook_content`, `content`, `coupon_policy`, `audit_event` |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/region-admin/stampbooks/{stampbookId}/approve` | `stampbook.status`, `coupon_policy`, `audit_event`, `audit_event_actor_link` |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/region-admin/stampbooks/{stampbookId}/reject` | `stampbook.status`, `audit_event`, `audit_event_actor_link` |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/operator/stampbooks/{stampbookId}/end` | `stampbook.status`, `stampbook_progress`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며, 적립·완료·종료 시각은 UTC ISO 8601 형식이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 운영 API는 승인된 `OPERATOR`와 지역·콘텐츠 소유권, 지역 관리자 심사 API는 담당 지역의 승인된 `REGION_ADMIN`, 방문자 조회 API는 활성 회원의 본인 진행 조건을 검증한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 `data` 필드와 오류 코드를 확인한다. `STAMPBOOK_STATE_CONFLICT`는 P1 구현 시 전역 `ErrorCode`에 추가한다. |
| 감사 이력 | [P1 ERD](../../../p1-erd.md#45-audit_event-확장) | 수명주기 전이는 대상·처리자·이전·이후 상태·사유·시각과 서버가 부여한 `requestId`를 함께 감사한다. `requestId`는 응답에 노출하지 않는다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 내 스탬프북 목록·적립 이력과 지역 관리자 심사 대기 목록은 단순 목록이고, 나머지는 단건·명령 API이므로 페이지네이션을 적용하지 않는다. |

## 3. 공개 심사 요청과 지역 관리자 심사의 역할

- [스탬프북 공개 심사 요청](request-stampbook-publication.md)은 담당 `OPERATOR`가 `DRAFT → PENDING_REVIEW`로
  전이하는 제출 명령이다. 이 요청은 방문자에게 공개하지 않으며, 완료 보상 정책을 `PUBLISHED`로 전이하지도 않는다.
- [심사 대기 목록](list-pending-region-admin-stampbooks.md)과 [심사 상세](get-pending-region-admin-stampbook.md)는
  담당 `REGION_ADMIN`이 같은 지역의 현재 `PENDING_REVIEW` 스탬프북을 확인하는 읽기 전용 API다.
- [스탬프북 승인](approve-region-admin-stampbook.md)은 잠금 뒤 완료 보상 정책이 같은 지역의
  `STAMPBOOK_COMPLETION`, `PUBLISHED`인지와 대상 콘텐츠 관계를 다시 검증한 뒤에만 `PENDING_REVIEW → PUBLISHED`로
  전이하고 `published_at`을 기록한다. [스탬프북 반려](reject-region-admin-stampbook.md)는 필수 사유와 함께
  `PENDING_REVIEW → DRAFT`로 되돌려 운영자가 수정·재제출할 수 있게 한다.

## 4. 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 내 스탬프북 목록 조회 | `GET /me/stampbooks` | [list-my-stampbooks.md](list-my-stampbooks.md) |
| 내 스탬프북 상세·진행도 조회 | `GET /me/stampbooks/{stampbookId}` | [get-my-stampbook.md](get-my-stampbook.md) |
| 내 스탬프 적립 이력 조회 | `GET /me/stampbooks/{stampbookId}/earnings` | [list-my-stamp-earnings.md](list-my-stamp-earnings.md) |
| 스탬프북 생성 | `POST /operator/stampbooks` | [create-stampbook.md](create-stampbook.md) |
| 스탬프북 수정 | `PATCH /operator/stampbooks/{stampbookId}` | [update-stampbook.md](update-stampbook.md) |
| 스탬프북 공개 심사 요청 | `POST /operator/stampbooks/{stampbookId}/publish` | [request-stampbook-publication.md](request-stampbook-publication.md) |
| 스탬프북 심사 대기 목록 조회 | `GET /region-admin/stampbooks?status=PENDING_REVIEW` | [list-pending-region-admin-stampbooks.md](list-pending-region-admin-stampbooks.md) |
| 스탬프북 심사 상세 조회 | `GET /region-admin/stampbooks/{stampbookId}` | [get-pending-region-admin-stampbook.md](get-pending-region-admin-stampbook.md) |
| 스탬프북 승인 | `POST /region-admin/stampbooks/{stampbookId}/approve` | [approve-region-admin-stampbook.md](approve-region-admin-stampbook.md) |
| 스탬프북 반려 | `POST /region-admin/stampbooks/{stampbookId}/reject` | [reject-region-admin-stampbook.md](reject-region-admin-stampbook.md) |
| 스탬프북 종료 | `POST /operator/stampbooks/{stampbookId}/end` | [end-stampbook.md](end-stampbook.md) |
