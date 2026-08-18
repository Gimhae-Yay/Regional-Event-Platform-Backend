# 전체 콘텐츠 철회 요청 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-07`, `CON-09` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0101](../../../adr/0101-store-content-withdrawal-requests-and-serialize-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 대기 중인 전체 콘텐츠 철회 요청을 사유와 함께 반려한다. 반려는 요청만
`PENDING → REJECTED`로 종결하고 콘텐츠·수정본·홀드·예약·결제·쿠폰을 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `CON-07` | `POST /region-admin/content-withdrawal-requests/{withdrawalRequestId}/reject` | `content_withdrawal_request` |
| `AUTH-01`, `CON-09` | 같은 경로 | `user_role_assignment`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}/reject`; 시각은 UTC `Z`, 식별자는 양의 10진 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot, 활성 `ORDINARY` 계정과 요청 콘텐츠의 현재 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공과 같은 사유의 저장 결과 재응답은 `200 OK`; 상태·사유 충돌은 `CONTENT_STATE_CONFLICT` |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 명령 API이므로 적용하지 않음 |

## 3. 전체 콘텐츠 철회 요청 반려

### Request

```http
POST /region-admin/content-withdrawal-requests/{withdrawalRequestId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/content-withdrawal-requests/7001/reject HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "진행 중인 운영 일정의 종료 계획을 먼저 보완해 주세요."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. `ROLE_REGION_ADMIN` snapshot을 가져야 하며 DB에서 활성 `ORDINARY` 계정인지 확인한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |
| `Idempotency-Key` | N | 사용하지 않는다. 요청 ID, 터미널 상태와 정규화 반려 사유를 자연 멱등 기준으로 사용한다. |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `withdrawalRequestId` | String | Y | 반려할 전체 철회 요청 식별자. 양의 10진 문자열이어야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "진행 중인 운영 일정의 종료 계획을 먼저 보완해 주세요."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 반려 사유. 앞뒤 공백을 제거한 값이 비어 있으면 안 되며 요청 행에 보존한다. |

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
  "message": "전체 콘텐츠 철회 요청을 반려했습니다.",
  "data": {
    "withdrawalRequestId": "7001",
    "contentId": "101",
    "status": "REJECTED",
    "rejectionReason": "진행 중인 운영 일정의 종료 계획을 먼저 보완해 주세요.",
    "rejectedAt": "2026-08-16T04:10:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | 항상 `200` |
| `code` | String | 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.withdrawalRequestId` | String | 반려한 요청 식별자 |
| `data.contentId` | String | 요청 대상 콘텐츠 식별자 |
| `data.status` | String | 항상 `REJECTED` |
| `data.rejectionReason` | String | 저장된 정규화 반려 사유 |
| `data.rejectedAt` | String | 최초 반려 시각. 재시도에서도 바뀌지 않는 UTC `Z` 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 식별자가 양수가 아니거나 `reason`이 없거나 공백뿐이다. 아무 상태도 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 식별자를 signed 64비트 양의 정수로 해석할 수 없다. 아무 상태도 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 아무 상태도 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 요청 콘텐츠의 지역이 현재 담당 지역과 다르다. |
| `404` | `NOT_FOUND` | 요청을 찾을 수 없다. |
| `409` | `CONTENT_STATE_CONFLICT` | 요청이 `APPROVED`·`INVALIDATED`이거나, 이미 `REJECTED`인 요청의 저장 사유와 다른 사유로 재시도했거나, 승인·중단·종료가 먼저 성공했다. |
| `500` | `INTERNAL_SERVER_ERROR` | 반려·감사 저장 중 예상하지 못한 오류가 발생했다. 커밋되지 않았다면 상태 변경은 없다. |

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

1. 서버는 요청에서 `content_id`를 조회한 뒤 권한용 actor·역할, `region → content → content_withdrawal_request` 순서로 잠그고 관리자 담당 지역과 요청 상태를 다시 검증한다.
2. `PENDING` 요청만 최초 반려할 수 있다. 정규화 사유, 반려 관리자와 MySQL 기준 반려 시각을 저장한다.
3. 이미 `REJECTED`인 요청에 같은 정규화 사유로 재시도하면 새 감사·처리 시각을 만들지 않고 저장 결과를 반환한다. 다른 사유는 기존 사유를 덮어쓰지 않고 `409 CONTENT_STATE_CONFLICT`다.
4. 성공 시 `CONTENT_WITHDRAWAL_REQUEST` 감사 대상에 `PENDING → REJECTED`, 사유 코드 `CONTENT_WITHDRAWAL_REJECTED`와 반려 actor를 기록한다.
5. 반려 뒤 콘텐츠가 계속 `PUBLISHED`이면 소유 운영자는 새 `Idempotency-Key`와 새 요청 행으로 전체 철회를 다시 요청할 수 있다. 같은 키 재시도는 기존 반려 요청의 최초 생성 결과로 수렴한다.
6. 반려는 `content`, `content_revision`, `content_log`, `content_session`, `capacity_hold`, `reservation`, `reservation_price_snapshot`, `payment`, `payment_idempotency`, `coupon`을 읽기 검증 외에 변경하지 않는다.

### 트랜잭션·경합 및 MySQL 검증 조건

- 요청 `PENDING → REJECTED`, 반려 메타데이터와 성공 감사 이벤트·actor link는 하나의 MySQL 트랜잭션에서 함께 커밋하거나 롤백한다.
- 같은 사유의 동시 반려와 순차 재시도는 터미널 행·감사를 한 번만 만들고 같은 결과를 반환한다. 다른 사유의 동시 재시도는 최초 결과 하나만 유지한다.
- 승인과 반려 경합은 요청 행의 조건부 전이에 먼저 성공한 하나만 커밋한다. 반려가 이기면 콘텐츠·수정본·홀드·정원·결제·쿠폰이 승인 전과 동일함을 검증한다.
- 중단·종료가 요청을 `INVALIDATED`로 먼저 종결하면 반려는 `409`; 반려가 먼저 커밋되면 중단·종료는 반려 요청을 다시 바꾸지 않고 기존 수명주기 전이만 수행한다.
- 반려 저장 또는 성공 감사 저장에 예외를 주입하면 요청 상태·심사 메타데이터가 함께 롤백되는지 검증한다.
