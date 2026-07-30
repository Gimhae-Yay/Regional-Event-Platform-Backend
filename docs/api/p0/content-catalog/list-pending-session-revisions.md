# 심사 대기 회차 수정 요청 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ADR-0031](../../../adr/0031-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 기존 `SCHEDULED` 회차에 대해 제출된 `PENDING` 수정 요청을 목록으로 조회한다.
수정 요청은 후보값만 보관하므로 이 조회로 기존 회차의 공개·예약 가능 상태는 바뀌지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-04`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-02` | `GET /api/v1/region-admin/session-revisions?status=PENDING` | `content`, `content_session`, `session_revision`, `app_user` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고, 일정 시각·사건 시각·식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN` 역할과 담당 지역 일치가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 공통 오류 코드를 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 페이지네이션을 적용하지 않는다. |

## 3. 심사 대기 회차 수정 요청 목록 조회

### Request

```http
GET /api/v1/region-admin/session-revisions?status=PENDING
```

#### Request Example

```http
GET /api/v1/region-admin/session-revisions?status=PENDING HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 담당 지역 관리자 Access Token |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | 항상 `PENDING`이다. |

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
  "message": "심사 대기 회차 수정 요청 목록 조회에 성공했습니다.",
  "data": {
    "revisions": [
      {
        "revisionId": "52",
        "contentId": "10",
        "contentTitle": "가야문화 체험",
        "targetSessionId": "21",
        "baseSessionVersion": 3,
        "startsAt": "2026-08-29T10:00:00+09:00",
        "endsAt": "2026-08-29T12:00:00+09:00",
        "checkinOpenAt": "2026-08-29T09:30:00+09:00",
        "checkinCloseAt": "2026-08-29T12:30:00+09:00",
        "capacity": 30,
        "submittedAt": "2026-08-01T01:00:00Z",
        "operator": {
          "operatorId": "20",
          "name": "김해운영"
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
| `message` | String | 성공 메시지 |
| `data.revisions` | Array | 담당 지역의 심사 대기 회차 수정 요청 목록. 없으면 빈 배열 `[]` |
| `data.revisions[].revisionId` | String | 수정 요청 식별자 |
| `data.revisions[].contentId`, `contentTitle` | String | 대상 콘텐츠 식별자와 제목 |
| `data.revisions[].targetSessionId` | String | 수정 대상 기존 회차 식별자 |
| `data.revisions[].baseSessionVersion` | Integer | 제출 시 복사한 대상 회차 버전 |
| `data.revisions[].startsAt`~`checkinCloseAt` | String | 후보 일정과 체크인 창 |
| `data.revisions[].capacity` | Integer | 후보 총정원 |
| `data.revisions[].submittedAt` | String | 수정 요청 제출 시각. UTC ISO 8601 일시 |
| `data.revisions[].operator` | Object | 요청한 콘텐츠 소유 운영자 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `status`가 없거나 `PENDING`이 아니다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| 403 | `FORBIDDEN` | 담당 지역 관리자 역할 또는 담당 지역이 없다. |
| 500 | `INTERNAL_SERVER_ERROR` | 수정 요청·대상 회차·콘텐츠·지역 관계가 정책과 일치하지 않는다. |

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

1. 인증 주체의 `REGION_ADMIN` 담당 지역과 `session_revision.region_id`가 같은 행만 반환한다.
2. `session_revision.status = PENDING`이고 콘텐츠가 소프트 삭제되지 않은 행만 반환한다. 승인 가능 여부는 상세·승인 시점에 다시 판단한다.
3. `submitted_at` 오름차순, 같은 시각이면 `request_id` 오름차순으로 정렬한다.
4. 조회는 수정 요청·실제 회차·감사 기록을 변경하지 않는다.
