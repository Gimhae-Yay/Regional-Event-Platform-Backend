# 전체 콘텐츠 철회 요청 대기 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-07` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [P0 명세](../../../p0-spec.md), [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0101](../../../adr/0101-store-content-withdrawal-requests-and-serialize-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 자신의 지역에 접수된 `PENDING` 전체 콘텐츠 철회 요청을 조회한다. 목록은 승인·반려할
`withdrawalRequestId`와 대상 콘텐츠·요청자를 식별하는 최소 요약만 제공하며, 철회 요청 사유는
[전체 콘텐츠 철회 요청 상세 조회](get-pending-content-withdrawal-request.md)에서 확인한다.

클라이언트는 지역 식별자를 전달하지 않는다. 서버는 `ROLE_REGION_ADMIN` snapshot을 통과한 인증 주체의 현재 담당 지역 관계에서 담당 지역을 결정하고,
요청에 연결된 콘텐츠의 `region_id`가 담당 지역과 일치하는 대기 요청만 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-10`, `FR-14`, `CON-07` | `GET /api/v1/region-admin/content-withdrawal-requests?status=PENDING` | `content_withdrawal_request`, `content`, `app_user` |
| `AUTH-01` | `GET /api/v1/region-admin/content-withdrawal-requests?status=PENDING` | `content.region_id`, `user_role_assignment.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-withdrawal-requests?status=PENDING`이며, 사건 시각은 UTC `Z`, 식별자는 양의 10진 문자열이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot, 활성 `ORDINARY` 계정과 현재 담당 지역 관계가 필요하며 서버가 지역 경계를 강제한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 빈 배열을 포함한 목록을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 심사 대기 목록 전체를 고정 순서로 반환하므로 페이지네이션을 적용하지 않는다. |

## 3. 전체 콘텐츠 철회 요청 대기 목록 조회

### Request

```http
GET /api/v1/region-admin/content-withdrawal-requests?status=PENDING
```

#### Request Example

```http
GET /api/v1/region-admin/content-withdrawal-requests?status=PENDING HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | `PENDING`만 허용한다. 다른 값, 빈 값 또는 누락은 허용하지 않는다. |

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
  "message": "전체 콘텐츠 철회 요청 대기 목록 조회에 성공했습니다.",
  "data": {
    "withdrawalRequests": [
      {
        "withdrawalRequestId": "7001",
        "contentId": "101",
        "contentType": "EVENT_EXPERIENCE",
        "contentTitle": "김해 가야문화 체험",
        "contentStatus": "PUBLISHED",
        "requester": {
          "userId": "20",
          "name": "김운영"
        },
        "requestedAt": "2026-08-16T04:00:00Z"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Nullable | Description |
| --- | --- | --- | --- |
| `statusCode` | Number | N | HTTP 상태 코드. 항상 `200` |
| `code` | String | N | 성공 코드. 항상 `SUCCESS` |
| `message` | String | N | 공개 성공 메시지 |
| `data.withdrawalRequests` | Array | N | 담당 지역의 `PENDING` 철회 요청 목록. 결과가 없으면 빈 배열 `[]` |
| `data.withdrawalRequests[].withdrawalRequestId` | String | N | 전체 콘텐츠 철회 요청 식별자 |
| `data.withdrawalRequests[].contentId` | String | N | 요청 대상 콘텐츠 식별자 |
| `data.withdrawalRequests[].contentType` | String | N | 콘텐츠 유형. P0에서는 `EVENT_EXPERIENCE` |
| `data.withdrawalRequests[].contentTitle` | String | N | 요청 대상 콘텐츠 제목 |
| `data.withdrawalRequests[].contentStatus` | String | N | 처리 대기 요청의 콘텐츠 상태. 항상 `PUBLISHED` |
| `data.withdrawalRequests[].requester` | Object | Y | 철회 요청자. 회원 탈퇴로 연결이 제거됐으면 `null`이며 다른 데이터로 요청자를 재식별하지 않는다. |
| `data.withdrawalRequests[].requester.userId` | String | N | 연결이 유지된 철회 요청자 식별자 |
| `data.withdrawalRequests[].requester.name` | String | N | 연결이 유지된 철회 요청자 이름 |
| `data.withdrawalRequests[].requestedAt` | String | N | 요청 생성 시각. UTC ISO 8601 `Z` 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 누락됐거나 `PENDING`이 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정 또는 현재 담당 지역 관계가 없다. 조회 대상과 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 요청·콘텐츠·요청자 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ROLE_REGION_ADMIN` snapshot을 가지고 `ACTIVE` 상태의 `ORDINARY` 계정이며 현재 담당 `region_id`를 가져야 한다.
2. 서버는 인증 주체의 현재 담당 지역 관계에서 담당 지역을 결정하며 클라이언트가 지역을 지정하거나 변경할 수 없다.
3. `content_withdrawal_request.status = PENDING`, 연결 콘텐츠의 `region_id = 담당 region_id`,
   `content.status = PUBLISHED`, `content.deleted_at IS NULL`인 요청만 반환한다.
4. 다른 지역의 요청 존재 여부와 개수는 응답이나 오류로 노출하지 않는다.
5. `status`가 누락되거나 `PENDING` 이외의 값이면 빈 목록으로 대체하지 않고 `400 INVALID_INPUT`으로 거부한다.
6. 목록은 `requestedAt` 오름차순, 같은 시각이면 `withdrawalRequestId` 오름차순으로 정렬해 오래 기다린 요청을 먼저 반환한다.
7. 대기 요청이 없으면 `404`가 아닌 `200 OK`와 `data.withdrawalRequests = []`를 반환한다.
8. P0에서는 페이지네이션, 추가 상태 필터, 검색과 사용자 지정 정렬을 제공하지 않는다.
9. `requester` 연결이 유지되면 요청자 식별자와 이름을 반환한다. 회원 탈퇴로 연결이 제거됐으면 `requester = null`로
   반환하고 감사·멱등 데이터 또는 콘텐츠 소유 관계로 탈퇴 회원을 역참조하지 않는다.
10. 목록에는 `requestReason`, `idempotencyKeyHash`, 심사·무효화 메타데이터, 예약·홀드·결제·쿠폰 정보를 포함하지 않는다.
11. 조회는 요청·콘텐츠·감사 상태를 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 담당 지역 식별자, 결과 건수와 결과 코드만 구조화 로그로 남긴다.
- 요청자 이름, 철회 요청 사유와 멱등 키 해시는 구조화 로그에 남기지 않는다.
- 목록 조회 뒤 요청이 승인·반려·무효화될 수 있으므로 승인·반려 API는 명령 시점의 요청·콘텐츠 상태와 담당 지역을 다시 검증한다.
