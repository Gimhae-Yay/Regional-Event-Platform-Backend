# 콘텐츠 철회 승인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-07`, `CON-09`, `SES-02` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [인증·프로필](../../../p0/auth-profile.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [API 공통 계약](../../common/README.md), 선행 결정 PR의 ADR-0032 |

## 1. 개요

담당 지역 관리자가 소유 운영자의 미종결 철회 요청을 승인한다. 승인 요청 본문은 받지 않고,
`WITHDRAWAL_REQUESTED` 로그에 보존된 요청 사유를 `WITHDRAWN` 로그에도 보존해 `PUBLISHED → WITHDRAWN`으로 전이한다.
`WITHDRAWAL_REQUESTED` 이벤트 코드·로그 제약과 소유 운영자의 철회 요청 계약은 선행 결정 PR의 ADR-0032·ERD·PRD에서
정의되어야 하는 이 API의 선행 조건이며, 이 7개 지역 관리자 API 명세는 그 선행 계약을 생성하거나 대체하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14`, `CON-07` | `POST /region-admin/contents/{contentId}/withdraw` | `content`, `content_log`, `capacity_hold` |
| `AUTH-01`, `CON-09` | `POST /region-admin/contents/{contentId}/withdraw` | `content.region_id`, `user_role_assignment`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/contents/{contentId}/withdraw`; 생성·수정·심사·처리 이벤트 시각은 ISO 8601 UTC `Z` 문자열이며 콘텐츠 일정값만 `+09:00` 오프셋 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN`과 대상 콘텐츠의 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 `CONTENT_WITHDRAWAL_APPROVAL_CONFLICT`를 포함한 API별 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 명령 API이므로 적용하지 않음 |

## 3. 콘텐츠 철회 승인

### Request

```http
POST /region-admin/contents/{contentId}/withdraw
```

#### Request Example

```http
POST /api/v1/region-admin/contents/101/withdraw HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 상태의 `REGION_ADMIN`이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 필요하지 않다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 철회 승인할 콘텐츠 식별자. 양의 10진 문자열이어야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

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
  "message": "콘텐츠 철회 승인에 성공했습니다.",
  "data": {
    "contentId": "101",
    "status": "WITHDRAWN",
    "withdrawnAt": "2026-07-30T01:00:00Z",
    "withdrawalReason": "운영상 더 이상 프로그램을 제공할 수 없습니다."
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.contentId` | String | 양의 10진 문자열 철회된 콘텐츠 식별자 |
| `data.status` | String | 항상 `WITHDRAWN` |
| `data.withdrawnAt` | String | `WITHDRAWN` 상태 로그의 처리 시각. UTC `Z` 문자열 |
| `data.withdrawalReason` | String | 가장 최근 `WITHDRAWAL_REQUESTED` 로그의 `reason`을 변경 없이 보존한 철회 요청 사유 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양의 10진 문자열이 아니다. 콘텐츠·홀드·로그·감사 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `contentId`를 양의 10진 문자열 식별자로 해석할 수 없다. 콘텐츠·홀드·로그·감사 기록을 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 콘텐츠·홀드·로그·감사 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 대상 콘텐츠의 지역이 담당 지역과 일치하지 않는다. 콘텐츠·홀드·로그·감사 기록을 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없다. 콘텐츠·홀드·로그·감사 기록을 변경하지 않으며 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `CONTENT_WITHDRAWAL_APPROVAL_CONFLICT` | 콘텐츠가 `PUBLISHED`가 아니거나, `(date, id)` 내림차순으로 판정한 가장 최근 철회 관련 로그가 사유와 소유 운영자 정보를 가진 `WITHDRAWAL_REQUESTED`가 아니거나, 다른 상태 전이가 먼저 성공했다. 콘텐츠·홀드·로그·성공 감사 기록을 부분 변경하지 않으며 현재 상태를 다시 조회해야 한다. 롤백 뒤에는 비개인 실패 `audit_event`를 별도 트랜잭션으로 기록한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠·철회 요청 로그·회차·홀드 연결의 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 콘텐츠·홀드·로그·감사 기록은 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_WITHDRAWAL_APPROVAL_CONFLICT",
  "message": "콘텐츠 철회 요청을 승인할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 상태이며 담당 `region_id`가 연결된 `REGION_ADMIN`이어야 한다.
2. 대상 콘텐츠 `region_id`와 인증 지역 관리자의 담당 `region_id`가 일치해야 한다.
3. 서버는 대상 `content` 행을 잠근 뒤 콘텐츠 상태와 `WITHDRAWAL_REQUESTED`, `WITHDRAWN` 로그 중 `(date, id)` 내림차순의 가장 최근 철회 관련 로그를 다시 판정한다.
4. 승인 조건은 콘텐츠가 `PUBLISHED`이고, 가장 최근 철회 관련 로그가 `actor_id = content.operator_id`, 비어 있지 않은 `reason`, 요청 시각을 가진 `WITHDRAWAL_REQUESTED`인 경우다. 이 로그는 이 API 호출 전에 소유 운영자의 철회 요청으로 기록되어야 하며, 이 API는 해당 요청 로그를 생성·수정하지 않는다.
5. 성공 시 요청 로그의 사유를 변경하지 않고 `WITHDRAWN` 로그의 `reason`으로 보존하며 콘텐츠를 `PUBLISHED → WITHDRAWN`으로 전이한다.
6. 신규 홀드 생성과 예약 확정은 모두 `content` 행 다음 `capacity_hold` 행 순서로 같은 잠금을 획득하고, 잠금 획득 뒤 콘텐츠가 `PUBLISHED`인지 다시 확인한다. 철회 전이가 먼저 커밋되면 홀드 생성과 `ACTIVE → CONSUMED` 확정은 성공하지 않는다. 반대로 홀드 생성 또는 확정이 먼저 커밋된 경우에만 그 결과를 기존 홀드 또는 기존 `CONFIRMED` 예약으로 처리한다.
7. `SUSPENDED`, `ENDED`, 이미 `WITHDRAWN`이거나 미종결 철회 요청이 없는 콘텐츠는 승인할 수 없다.
8. 성공 뒤 신규 홀드를 차단하고 `ACTIVE` 홀드를 `INVALIDATED`로 전이해 각 홀드의 정원을 한 번만 복구한다.
9. 기존 `CONFIRMED` 예약은 명시적인 회차 취소가 없으면 유지한다.
10. P0에서는 철회 요청 반려·취소 API를 제공하지 않는다.

### 감사 및 정합성

- 콘텐츠 행 잠금·상태 전이, 요청 사유가 있는 `WITHDRAWN` 로그, 활성 홀드 무효화·정원 1회 복구와 성공 `audit_event`는 하나의 MySQL 트랜잭션에서 함께 커밋하거나 함께 롤백한다. 이 잠금은 신규 홀드 생성·예약 확정의 `PUBLISHED` 조건 확인보다 먼저 `content` 행을 획득하고, 두 경로 모두 `content → capacity_hold` 순서를 따른다.
- `WITHDRAWAL_REQUESTED` 로그와 `WITHDRAWN` 로그는 요청자·요청 시각과 승인 처리자를 구분해 철회 이력을 재현할 수 있어야 하며, 두 로그의 `reason`은 같은 철회 요청 사유여야 한다.
- 실패 시 콘텐츠 상태, 기존 철회 요청 로그, 기존 예약과 홀드 정원, 성공 감사 이벤트를 변경하지 않는다. 롤백된 철회 승인 거부·충돌은 롤백 완료 뒤 별도 트랜잭션에서 비개인 `FAILURE` `audit_event`로 기록한다.
