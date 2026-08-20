# 지역·콘텐츠 카탈로그 운영자 소유 콘텐츠 회차 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `AUTH-01`, `SES-01`, `SES-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0038](../../../adr/0038-create-sessions-with-lifecycle-and-review-session-changes.md), [ADR-0091](../../../adr/0091-store-content-wide-reservation-price-and-snapshot-at-payment-creation.md), [ADR-0112](../../../adr/0112-list-operator-owned-content-sessions-separately.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 화면 새로고침이나 재접속 뒤에도 콘텐츠에 연결된 회차와 현재 심사 대기 변경 요청을 복구할 수 있도록
회차 목록을 조회한다. 공개 회차 목록과 달리 콘텐츠의 공개 상태와 회차 상태로 필터링하지 않으며, 소프트 삭제되지 않은
소유 콘텐츠의 `PENDING`, `SCHEDULED`, `REJECTED`, `COMPLETED`, `CANCELLED` 회차를 모두 반환한다.

`PENDING` 변경 요청은 실제 회차를 바꾸지 않으므로 현재 `content_session`과 후보 `session_revision`을 구분해 반환한다.
승인·반려로 종결된 변경 요청 이력, 별도 운영자 회차 상세와 회차별 가격은 이 API 범위에 포함하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03`, `AUTH-01`, `SES-01`, `SES-02` | `GET /api/v1/operator/contents/{contentId}/sessions` | `content`, `content_session`, `session_revision`, `user_role_assignment` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 일정 시각, 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `OPERATOR` 역할, 담당 지역과 콘텐츠 지역의 일치, 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 공통 오류 코드를 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 페이지네이션을 적용하지 않는다. |

## 3. 운영자 소유 콘텐츠 회차 목록 조회

### Request

```http
GET /api/v1/operator/contents/{contentId}/sessions
```

#### Request Example

```http
GET /api/v1/operator/contents/10/sessions HTTP/1.1
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
| `contentId` | String | Y | API 공통 규칙에 따른 조회 대상 콘텐츠 식별자다. |

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
  "message": "내 콘텐츠 회차 목록 조회에 성공했습니다.",
  "data": {
    "contentId": "10",
    "sessions": [
      {
        "sessionId": "21",
        "status": "SCHEDULED",
        "version": 3,
        "startsAt": "2026-08-22T10:00:00+09:00",
        "endsAt": "2026-08-22T12:00:00+09:00",
        "checkinOpenAt": "2026-08-22T09:30:00+09:00",
        "checkinCloseAt": "2026-08-22T11:30:00+09:00",
        "capacity": 30,
        "remainingCapacity": 30,
        "rejectReason": null,
        "cancelledAt": null,
        "cancellationReason": null,
        "completedAt": null,
        "createdAt": "2026-08-01T01:00:00Z",
        "pendingChangeRequest": {
          "revisionId": "52",
          "status": "PENDING",
          "baseSessionVersion": 3,
          "candidate": {
            "startsAt": "2026-08-29T10:00:00+09:00",
            "endsAt": "2026-08-29T12:00:00+09:00",
            "checkinOpenAt": "2026-08-29T09:30:00+09:00",
            "checkinCloseAt": "2026-08-29T11:30:00+09:00",
            "capacity": 30
          },
          "submittedAt": "2026-08-02T01:00:00Z"
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `내 콘텐츠 회차 목록 조회에 성공했습니다.` |
| `data.contentId` | String | API 공통 규칙에 따른 소유 콘텐츠 식별자 |
| `data.sessions` | Array | `startsAt` 오름차순, 같은 시각이면 `sessionId` 오름차순인 전체 회차 배열. 없으면 `[]`다. |
| `data.sessions[].sessionId` | String | API 공통 규칙에 따른 회차 식별자 |
| `data.sessions[].status` | String | 현재 회차 상태. `PENDING`, `SCHEDULED`, `REJECTED`, `COMPLETED`, `CANCELLED` 중 하나다. |
| `data.sessions[].version` | Integer | 현재 회차 버전. 변경 요청의 기준 버전과 승인 시 일치해야 한다. |
| `data.sessions[].startsAt` | String | 현재 회차 시작 일정 시각 |
| `data.sessions[].endsAt` | String | 현재 회차 종료 일정 시각 |
| `data.sessions[].checkinOpenAt` | String | 현재 체크인 시작 일정 시각 |
| `data.sessions[].checkinCloseAt` | String | 현재 체크인 종료 일정 시각 |
| `data.sessions[].capacity` | Integer | 현재 총정원 |
| `data.sessions[].remainingCapacity` | Integer | 조회 시점의 현재 잔여 정원. 예약 가능 여부를 보장하지 않는다. |
| `data.sessions[].rejectReason` | String or null | `REJECTED` 회차의 반려 사유. 그 외 상태이면 `null`이다. |
| `data.sessions[].cancelledAt` | String or null | `CANCELLED` 회차의 취소 사건 시각. 그 외 상태이면 `null`이다. |
| `data.sessions[].cancellationReason` | String or null | `CANCELLED` 회차의 취소 사유. 그 외 상태이면 `null`이다. |
| `data.sessions[].completedAt` | String or null | `COMPLETED` 회차의 완료 사건 시각. 그 외 상태이면 `null`이다. |
| `data.sessions[].createdAt` | String | 회차 생성 사건 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.sessions[].pendingChangeRequest` | Object or null | 대상 회차의 현재 `PENDING` 변경 요청. 활성 요청이 없으면 `null`이다. |
| `data.sessions[].pendingChangeRequest.revisionId` | String | API 공통 규칙에 따른 변경 요청 식별자 |
| `data.sessions[].pendingChangeRequest.status` | String | 항상 `PENDING`이다. |
| `data.sessions[].pendingChangeRequest.baseSessionVersion` | Integer | 요청 제출 시 복사한 대상 회차 버전 |
| `data.sessions[].pendingChangeRequest.candidate` | Object | 승인 시에만 반영할 후보 일정·체크인 창·정원 |
| `data.sessions[].pendingChangeRequest.candidate.startsAt` | String | 후보 회차 시작 일정 시각 |
| `data.sessions[].pendingChangeRequest.candidate.endsAt` | String | 후보 회차 종료 일정 시각 |
| `data.sessions[].pendingChangeRequest.candidate.checkinOpenAt` | String | 후보 체크인 시작 일정 시각 |
| `data.sessions[].pendingChangeRequest.candidate.checkinCloseAt` | String | 후보 체크인 종료 일정 시각 |
| `data.sessions[].pendingChangeRequest.candidate.capacity` | Integer | 후보 총정원 |
| `data.sessions[].pendingChangeRequest.submittedAt` | String | 변경 요청 제출 사건 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `contentId`가 양의 정수가 아니다. 상태를 변경하지 않는다. |
| 400 | `INVALID_TYPE` | `contentId`를 정수로 변환할 수 없다. 상태를 변경하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 회차를 반환하지 않는다. |
| 403 | `FORBIDDEN` | 활성 `OPERATOR` 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 회차를 반환하지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐다. 회차를 반환하지 않는다. |
| 500 | `INTERNAL_SERVER_ERROR` | 콘텐츠·회차·변경 요청·지역 관계가 정책과 일치하지 않는다. |

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

1. 인증 주체가 활성 `OPERATOR`인지, 현재 담당 지역과 콘텐츠 지역이 일치하는지, 인증 사용자가
   `content.operator_id`인지 확인한다.
2. 콘텐츠가 소프트 삭제되지 않았다면 콘텐츠 상태와 관계없이 연결된 모든 `content_session`을 조회한다.
3. 회차는 `PENDING`, `SCHEDULED`, `REJECTED`, `COMPLETED`, `CANCELLED` 상태를 모두 포함하고
   `starts_at` 오름차순, 같은 시각이면 `session_id` 오름차순으로 정렬한다.
4. 각 회차에 현재 `PENDING`인 `session_revision`이 있으면 실제 회차와 구분된 `pendingChangeRequest`로 반환한다.
   승인·반려로 종결된 변경 요청은 반환하지 않는다.
5. 상태별 종결 필드는 ERD의 nullable 규칙을 따른다. 다른 상태의 사유·종결 시각을 임의로 채우지 않는다.
6. 가격은 콘텐츠 공통 `reservationPrice`를 사용하며 이 회차 목록에 가격 필드를 반환하지 않는다.
7. 조회는 콘텐츠·회차·변경 요청·정원·홀드·예약·감사 기록을 변경하지 않는다.
