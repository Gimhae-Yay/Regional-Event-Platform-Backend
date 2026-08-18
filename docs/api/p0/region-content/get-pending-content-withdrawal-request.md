# 전체 콘텐츠 철회 요청 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-07` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [P0 명세](../../../p0-spec.md), [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0101](../../../adr/0101-store-content-withdrawal-requests-and-serialize-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 승인 또는 반려할 `PENDING` 전체 콘텐츠 철회 요청의 대상 콘텐츠, 요청자, 요청 사유와 요청 시각을
조회한다. 이 API는 심사 판단에 필요한 현재 요청 스냅샷만 제공하며 승인·반려 또는 콘텐츠 상태 변경을 수행하지 않는다.

요청이 없거나 더 이상 `PENDING` 심사 대상이 아니면 상세 정보를 반환하지 않는다. 목록 또는 상세 조회 뒤 상태가 바뀔 수
있으므로 승인·반려 API는 명령 시점의 요청 상태, 콘텐츠 상태와 담당 지역을 다시 검증한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-10`, `FR-14`, `CON-07` | `GET /api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}` | `content_withdrawal_request`, `content`, `app_user` |
| `AUTH-01` | `GET /api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}` | `content.region_id`, `user_role_assignment.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}`이며, 사건 시각은 UTC `Z`, 식별자는 양의 10진 문자열이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot, 활성 `ORDINARY` 계정과 현재 담당 지역 관계가 필요하고 요청 콘텐츠의 지역이 담당 지역과 일치해야 한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 공통 네 필드를 사용하며 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 전체 콘텐츠 철회 요청 상세 조회

### Request

```http
GET /api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}
```

#### Request Example

```http
GET /api/v1/region-admin/content-withdrawal-requests/7001 HTTP/1.1
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

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `withdrawalRequestId` | String | Y | 조회할 전체 콘텐츠 철회 요청 식별자. 양의 10진 문자열이어야 한다. |

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
  "message": "전체 콘텐츠 철회 요청 상세 조회에 성공했습니다.",
  "data": {
    "withdrawalRequestId": "7001",
    "status": "PENDING",
    "content": {
      "contentId": "101",
      "contentType": "EVENT_EXPERIENCE",
      "title": "김해 가야문화 체험",
      "status": "PUBLISHED",
      "publishAt": "2026-08-01T09:00:00+09:00"
    },
    "requester": {
      "userId": "20",
      "name": "김운영"
    },
    "requestReason": "운영 계획 변경으로 콘텐츠 전체 철회를 요청합니다.",
    "requestedAt": "2026-08-16T04:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Nullable | Description |
| --- | --- | --- | --- |
| `statusCode` | Number | N | HTTP 상태 코드. 항상 `200` |
| `code` | String | N | 성공 코드. 항상 `SUCCESS` |
| `message` | String | N | 공개 성공 메시지 |
| `data.withdrawalRequestId` | String | N | 전체 콘텐츠 철회 요청 식별자 |
| `data.status` | String | N | 심사 대상 요청 상태. 항상 `PENDING` |
| `data.content` | Object | N | 철회 요청 대상 콘텐츠의 심사용 현재 요약 |
| `data.content.contentId` | String | N | 요청 대상 콘텐츠 식별자 |
| `data.content.contentType` | String | N | 콘텐츠 유형. P0에서는 `EVENT_EXPERIENCE` |
| `data.content.title` | String | N | 요청 대상 콘텐츠 제목 |
| `data.content.status` | String | N | 처리 대기 요청의 콘텐츠 상태. 항상 `PUBLISHED` |
| `data.content.publishAt` | String | N | 승인된 공개 예정 시각. `Asia/Seoul` ISO 8601 오프셋 문자열 |
| `data.requester` | Object | Y | 철회 요청자. 회원 탈퇴로 연결이 제거됐으면 `null`이며 다른 데이터로 요청자를 재식별하지 않는다. |
| `data.requester.userId` | String | N | 연결이 유지된 철회 요청자 식별자 |
| `data.requester.name` | String | N | 연결이 유지된 철회 요청자 이름 |
| `data.requestReason` | String | N | 운영자가 제출하고 서버가 앞뒤 공백을 제거해 저장한 전체 콘텐츠 철회 요청 사유 |
| `data.requestedAt` | String | N | 요청 생성 시각. UTC ISO 8601 `Z` 문자열 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `withdrawalRequestId`가 양수가 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `withdrawalRequestId`를 signed 64비트 양의 정수로 해석할 수 없다. 조회 대상과 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 요청 콘텐츠의 지역이 현재 담당 지역과 다르다. 조회 대상과 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 요청이 없거나 `PENDING` 심사 대상이 아니다. 종결 상태와 존재하지 않는 요청을 구분해 노출하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 요청·콘텐츠·요청자 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ROLE_REGION_ADMIN` snapshot을 가지고 `ACTIVE` 상태의 `ORDINARY` 계정이며 현재 담당 `region_id`를 가져야 한다.
2. 서버는 `content_withdrawal_request`에서 콘텐츠를 조회하고, 요청 콘텐츠의 `region_id`와 인증 지역 관리자의 담당 지역이
   일치하는지 검증한다. 클라이언트가 지역 식별자를 전달하거나 지역 범위를 우회할 수 없다.
3. 요청이 `PENDING`, 콘텐츠가 미삭제 `PUBLISHED`인 경우에만 상세를 반환한다.
4. 다른 지역 요청은 `403 FORBIDDEN`으로 거부하며 요청 내용·요청자·사유를 응답이나 로그에 노출하지 않는다.
5. 요청이 없거나 이미 `APPROVED`, `REJECTED`, `INVALIDATED`로 종결됐으면 `404 NOT_FOUND`를 반환한다.
6. `requester` 연결이 유지되면 요청자 식별자와 이름을 반환한다. 회원 탈퇴로 연결이 제거됐으면 `requester = null`로
   반환하고 감사·멱등 데이터 또는 콘텐츠 소유 관계로 탈퇴 회원을 역참조하지 않는다.
7. 응답에는 `idempotencyKeyHash`, 심사자·무효화 처리자 메타데이터, 예약·홀드·결제·쿠폰 정보를 포함하지 않는다.
8. 조회는 요청·콘텐츠·감사 상태를 생성·수정·삭제하지 않는다.
9. 상세 조회 뒤 요청이 종결될 수 있으므로 승인·반려 API는 명령 시점의 요청 상태, 콘텐츠 상태와 담당 지역을 다시 검증한다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 식별이 완료된 `withdrawalRequestId`, 담당 지역 식별자와 결과 코드만 구조화 로그로 남긴다.
- 입력 검증 또는 대상 식별 전에 실패하면 경로의 원문 식별자를 로그에 남기지 않는다.
- 요청자 이름, 철회 요청 사유와 멱등 키 해시는 구조화 로그에 남기지 않는다.
- 요청·콘텐츠 연결이 없거나 `PENDING` 요청과 `PUBLISHED` 콘텐츠 불변 조건이 맞지 않으면 정상 데이터로 보완하지 않고 정합성 오류로 처리한다.
