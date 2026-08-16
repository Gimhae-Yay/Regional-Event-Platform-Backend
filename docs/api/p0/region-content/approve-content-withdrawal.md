# 전체 콘텐츠 철회 승인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0, P1 연결 데이터 정리 포함 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05`~`CON-07`, `CON-09`, `SES-02` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0101](../../../adr/0101-store-content-withdrawal-requests-and-serialize-review.md), [홀드 종결](../reservation/expire-or-invalidate-holds.md), [예약 확정](../reservation/confirm-reservation-hold.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 대기 중인 전체 콘텐츠 철회 요청을 승인한다. 성공하면 요청은 `APPROVED`, 콘텐츠는
`WITHDRAWN`이 되며 활성 수정본·홀드와 연결된 대기 결제·선점 쿠폰을 기존 종결 계약에 따라 원자적으로 처리한다.
기존 `CONFIRMED` 예약은 유지한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `CON-07` | `POST /region-admin/content-withdrawal-requests/{withdrawalRequestId}/approve` | `content_withdrawal_request`, `content`, `content_log` |
| `CON-05`, `CON-09` | 같은 경로 | `content_revision`, `audit_event` |
| `SES-02` | 같은 경로 | `content_session`, `capacity_hold`, P1 결제·쿠폰 연결 데이터 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}/approve`; 시각은 UTC `Z`, 식별자는 양의 10진 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN`과 요청 콘텐츠의 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공과 저장 결과 재응답은 `200 OK`; 상태 경합은 `CONTENT_STATE_CONFLICT` |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 명령 API이므로 적용하지 않음 |

## 3. 전체 콘텐츠 철회 승인

### Request

```http
POST /region-admin/content-withdrawal-requests/{withdrawalRequestId}/approve
```

#### Request Example

```http
POST /api/v1/region-admin/content-withdrawal-requests/7001/approve HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 활성 `REGION_ADMIN`이어야 한다. |
| `Accept` | N | `application/json` |
| `Idempotency-Key` | N | 사용하지 않는다. 요청 ID와 저장된 터미널 상태를 자연 멱등 기준으로 사용한다. |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `withdrawalRequestId` | String | Y | 승인할 전체 철회 요청 식별자. 양의 10진 문자열이어야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "전체 콘텐츠 철회 요청을 승인했습니다.",
  "data": {
    "withdrawalRequestId": "7001",
    "requestStatus": "APPROVED",
    "contentId": "101",
    "contentStatus": "WITHDRAWN",
    "withdrawalReason": "운영 계획 변경으로 콘텐츠 전체 철회를 요청합니다.",
    "approvedAt": "2026-08-16T04:10:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | 항상 `200` |
| `code` | String | 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.withdrawalRequestId` | String | 승인한 요청 식별자 |
| `data.requestStatus` | String | 항상 `APPROVED` |
| `data.contentId` | String | 철회된 콘텐츠 식별자 |
| `data.contentStatus` | String | 항상 `WITHDRAWN` |
| `data.withdrawalReason` | String | 요청 때 저장되어 `WITHDRAWN` 로그에 사용된 사유 |
| `data.approvedAt` | String | 요청 심사, 콘텐츠 로그와 수정본 무효화에 공통으로 사용한 MySQL 승인 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `withdrawalRequestId`가 양수가 아니다. 아무 상태도 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 식별자를 signed 64비트 양의 정수로 해석할 수 없다. 아무 상태도 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 활성 지역 관리자 역할이 없거나 요청 콘텐츠의 지역이 담당 지역과 다르다. |
| `404` | `NOT_FOUND` | 요청을 찾을 수 없다. |
| `409` | `CONTENT_STATE_CONFLICT` | 요청이 `REJECTED`·`INVALIDATED`이거나 콘텐츠가 `PUBLISHED`가 아니거나 승인·반려·중단·종료 등 다른 전이가 먼저 성공했다. 이미 `APPROVED`인 같은 요청은 오류가 아니라 저장 결과를 반환한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 승인 트랜잭션 중 예상하지 못한 오류가 발생했다. 커밋되지 않았다면 모든 MySQL 변경이 롤백된다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_STATE_CONFLICT",
  "message": "콘텐츠 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 요청에서 `content_id`를 조회한 뒤 권한용 actor·역할, `region → content → content_withdrawal_request` 순서로 잠그고 요청 지역과 관리자 담당 지역, 요청·콘텐츠 상태를 다시 검증한다.
2. 이미 `APPROVED`인 요청은 저장된 승인 결과를 반환하고 로그·감사·홀드·정원·결제·쿠폰 처리를 반복하지 않는다.
3. 최초 승인은 요청을 `PENDING → APPROVED`, 콘텐츠를 `PUBLISHED → WITHDRAWN`으로 전이한다.
4. 요청 때 저장한 사유를 가진 `WITHDRAWN` `content_log`를 추가한다. 로그 actor는 승인 관리자이고 로그 시각은 `approvedAt`이다.
5. 콘텐츠 행 뒤 활성 `EDIT_REQUESTED` 수정본을 잠근다. 있으면 승인 관리자·`approvedAt`·`CONTENT_WITHDRAWN` 사유를 가진 `EDIT_INVALIDATED`로 종결하고 후보를 원본에 반영하지 않는다.
6. 콘텐츠별 회차와 남은 `ACTIVE` 홀드를 순서대로 잠근다. 각 홀드는 `ACTIVE → INVALIDATED`를 한 번만 적용하고 성공한 홀드 수량만 해당 `content_session.remaining_capacity`에 한 번 복구한다. 무효화 사유는 `CONTENT_WITHDRAWN`이다.
7. 무효화된 홀드에 불변 가격 스냅샷과 `PENDING` 결제가 연결돼 있으면 결제를 `EXPIRED`로 종결하고 `finalized_at` 및 결제 멱등 결과 만료 시각을 설정한다.
8. 같은 스냅샷의 쿠폰이 `RESERVED`이면 원래 만료 시각 전에는 `AVAILABLE`, 이후에는 `EXPIRED`로 전이하고 쿠폰 상태 이력을 추가한다. 가격 스냅샷은 변경하지 않는다.
9. 요청·콘텐츠·수정본·실제로 전이된 홀드·결제·쿠폰마다 성공 `audit_event`를 같은 HTTP `requestId`로 기록한다. 요청 대상 유형은 `CONTENT_WITHDRAWAL_REQUEST`다.
10. 기존 `CONSUMED` 홀드와 `CONFIRMED`·`CHECKED_IN` 예약, 회차, 방문, 후기, 가격 스냅샷은 변경하지 않는다. 회차 취소·예약 취소·환불을 생성하지 않는다.
11. 커밋 뒤 공개 콘텐츠 정적 캐시를 최선 노력으로 삭제한다. 삭제 실패는 MySQL 승인을 롤백하지 않으며 공개 조회는 MySQL의 `WITHDRAWN` 상태를 먼저 검증한다.

### 트랜잭션 범위

다음은 하나의 MySQL 쓰기 트랜잭션에서 함께 커밋하거나 롤백한다.

- 요청 심사 결과와 심사자·시각
- `PUBLISHED → WITHDRAWN`
- 요청 사유가 있는 콘텐츠 로그
- 활성 수정본의 선택적 `EDIT_INVALIDATED`와 `CONTENT_WITHDRAWN` 감사
- 남은 `ACTIVE` 홀드의 단일 무효화와 정원의 단일 복구
- 필요한 `PENDING` 결제 종결·결제 멱등 만료 시각
- 필요한 `RESERVED` 쿠폰 선점 해제·상태 이력
- 위 상태 전이의 성공 감사 이벤트와 actor link

Redis 캐시 삭제와 외부 결제 API 호출은 이 원자성 범위에 없다. 이 API는 외부 결제 취소·환불을 호출하지 않는다.

### 동시 요청 및 MySQL 검증 조건

- 잠금 순서는 `region → content → content_withdrawal_request → content_revision → content_session → capacity_hold → reservation_price_snapshot → payment → coupon`이다.
- 동시 승인과 승인 재시도는 요청·콘텐츠 로그·성공 감사·홀드 복구를 각각 한 번만 만든다.
- 승인과 반려 경합은 요청 `PENDING`을 먼저 전이한 한 요청만 성공한다. 반려가 이기면 콘텐츠와 모든 연계 데이터는 그대로다.
- 승인과 수정본 승인·반려·철회가 경합하면 수정본 터미널 상태는 하나만 커밋된다. 전체 철회가 이기면 뒤의 수정본 명령은 `409`이고, 수정본 명령이 이기면 전체 철회는 그 상태를 유지한 채 진행한다.
- 승인과 중단·수동 종료·자동 종료가 경합하면 콘텐츠 `PUBLISHED`를 먼저 전이한 하나만 성공한다. 중단·종료가 이기면 요청은 같은 트랜잭션에서 `INVALIDATED`가 된다.
- 승인과 홀드 생성 경합에서 홀드가 먼저 커밋되면 승인이 이를 무효화·복구하고, 승인이 먼저면 생성은 `WITHDRAWN`을 확인해 실패한다.
- 승인과 예약 확정 경합에서 확정이 먼저면 `CONSUMED` 홀드와 기존 `CONFIRMED` 예약을 유지하고, 승인이 먼저면 확정은 무효화된 홀드로 인해 실패한다.
- 요청·콘텐츠·수정본·로그·감사·홀드·정원·결제·결제 멱등·쿠폰 저장 단계마다 예외를 주입해 전체 롤백을 검증한다.
