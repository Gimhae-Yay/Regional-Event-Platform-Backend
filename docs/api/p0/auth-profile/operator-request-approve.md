# 인증·프로필 운영자 승인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-09, AUTH-02 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 `PENDING` 운영자 신청을 승인한다. 승인 시 신청 상태를 `APPROVED`로 종결하고, 신청자가 여전히
활성 상태인 것을 조건으로 `OPERATOR` 역할, 요청 지역과 성공 감사 이벤트를 하나의 트랜잭션에서 함께 기록한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-09, AUTH-02 | `POST /api/v1/region-admin/operator-requests/{requestId}/approve` | `app_user`, `user_role_assignment`, `operator_application`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `REGION_ADMIN` 역할과 신청 요청 지역과 같은 담당 지역이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 승인·역할·담당 지역 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 명령이므로 적용하지 않는다. |

## 3. 운영자 승인·역할/담당 지역 원자적 부여

`PENDING` 신청을 승인하고 신청자에게 `OPERATOR` 역할과 요청 지역을 원자적으로 부여한다. 같은 트랜잭션에서
`operator_application`을 대상으로 요청 지역, `PENDING → APPROVED` 전이, `reason_code = OPERATOR_APPLICATION_APPROVED`,
`REGION_ADMIN` 처리자 역할과 활성 처리자 연결을 포함한 성공 감사 이벤트를 기록한다. 상태 전이, 역할·담당 지역 부여
또는 감사 기록 중 하나라도 실패하면 모두 롤백한다. 이미 `APPROVED`인 신청을 다시 승인하면 저장된 결과를 반환하며 감사
이벤트를 추가하지 않는다. 콘텐츠 소유 관계는 만들지 않는다.

### Request

```http
POST /api/v1/region-admin/operator-requests/{requestId}/approve
```

#### Request Example

```http
POST /api/v1/region-admin/operator-requests/21/approve HTTP/1.1
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
| `requestId` | Long | Y | 운영자 신청 식별자. 양의 정수다. |

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
  "message": "운영자 승인에 성공했습니다.",
  "data": {
    "operatorApplicationId": 21,
    "status": "APPROVED",
    "operatorRole": "OPERATOR",
    "assignedRegionId": 1,
    "processedAt": "2026-07-29T11:00:00+09:00"
  }
}
```

이미 `APPROVED`인 신청을 다시 승인하면 상태를 다시 변경하지 않고 기존 승인 결과를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `운영자 승인에 성공했습니다.` |
| `data.operatorApplicationId` | Long | 운영자 신청 식별자. 양의 정수다. |
| `data.status` | String | 승인 후 상태인 `APPROVED` |
| `data.operatorRole` | String | 승인으로 부여된 역할 `OPERATOR` |
| `data.assignedRegionId` | Long | 승인으로 부여된 담당 지역 식별자. 요청 지역과 같다. |
| `data.processedAt` | String | 승인 처리 시각. `operator_application.updated_at`과 같은 ISO 8601 오프셋 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `requestId`가 양의 정수가 아니다. 신청·역할·담당 지역은 변경되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_TYPE` | `requestId`를 정수로 변환할 수 없다. 신청·역할·담당 지역은 변경되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 만료·변조되었다. 신청·역할·담당 지역은 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| 403 | `FORBIDDEN` | `REGION_ADMIN` 역할 또는 신청 요청 지역과 같은 담당 지역이 없다. 신청·역할·담당 지역은 변경되지 않는다. |
| 404 | `NOT_FOUND` | 신청이 없거나 인증된 지역 관리자의 담당 지역에 속하지 않는다. 신청·역할·담당 지역은 변경되지 않는다. |
| 409 | `OPERATOR_APPLICATION_STATE_CONFLICT` | 신청이 `REJECTED` 또는 `CANCELLED`여서 승인할 수 없다. 신청·역할·담당 지역은 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "OPERATOR_APPLICATION_STATE_CONFLICT",
  "message": "운영자 신청 상태가 요청과 일치하지 않습니다.",
  "data": null
}
```
