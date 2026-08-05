# 전체관리자 계정 비활성화 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01`, `ADM-05` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [ADR-0064](../../../adr/0064-bootstrap-and-deactivate-platform-admin-accounts.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 활성 전체관리자가 다른 전체관리자 계정을 비활성화하는 HTTP API 계약을 정의한다. 자기 자신과 마지막 활성
전체관리자는 비활성화할 수 없으며, 재활성화 API는 현재 제공하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate` | `app_user`, Redis Refresh Token 계열, `audit_event` |

## 2. 공통 계약 참조

상태 전이·응답·오류 규칙은 [전체관리자 API](platform-admin.md#2-공통-계약-참조)를 따른다. 이 API는 목록 API가
아니므로 페이지네이션을 적용하지 않는다.

## 3. 전체관리자 계정 비활성화

활성 `PLATFORM_ADMIN`이 다른 활성 `PLATFORM_ADMIN` 계정을 `ACTIVE → INACTIVE`로 전이한다. 대상의 활성 Refresh Token 계열을
먼저 모두 폐기하고, 계정 상태 전이와 성공 감사 이벤트를 하나의 MySQL 트랜잭션으로 함께 커밋한다.

### Request

```http
POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate
```

#### Request Example

```http
POST /api/v1/platform-admin/admin-accounts/101/deactivate HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "담당 운영 종료"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `userId` | String | Y | 양의 10진 정수 문자열이며 signed 64비트 `Long` 범위를 만족하는 전체관리자 계정 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "담당 운영 종료"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 앞뒤 공백을 제거한 1~500자 비활성화 사유다. 빈 문자열·공백만으로 된 값은 허용하지 않으며 성공 감사 이벤트에 기록한다. |

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
  "message": "전체관리자 계정 비활성화에 성공했습니다.",
  "data": {
    "userId": "101",
    "status": "INACTIVE",
    "deactivatedAt": "2026-08-05T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.userId` | String | 비활성화한 전체관리자 계정의 식별자다. |
| `data.status` | String | 항상 `INACTIVE`다. |
| `data.deactivatedAt` | String | UTC ISO 8601 형식의 비활성화 완료 시각이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `userId`를 양의 정수 식별자로 처리할 수 없다. 계정 상태·Refresh Token 폐기·감사 이벤트를 변경하지 않는다. |
| `400` | `INVALID_INPUT` | `userId`가 양의 10진 문자열·signed 64비트 범위를 만족하지 않거나 `reason`이 누락·공백·500자 초과다. 계정 상태·Refresh Token 폐기·감사 이벤트를 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 계정 상태·Refresh Token 폐기·감사 이벤트를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 계정 상태·Refresh Token 폐기·감사 이벤트를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `PLATFORM_ADMIN` 역할을 갖지 않는다. 계정 상태·Refresh Token 폐기·감사 이벤트를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 계정이 없다. 계정 상태·Refresh Token 폐기·감사 이벤트를 변경하지 않는다. |
| `409` | `ADMIN_ACCOUNT_DEACTIVATION_CONFLICT` | 처리자 자신, 마지막 활성 전체관리자, 비활성 상태 또는 `PLATFORM_ADMIN` 역할이 아닌 계정은 비활성화할 수 없다. Refresh Token 폐기와 감사 이벤트를 포함해 변경하지 않는다. |
| `503` | `AUTH_SERVICE_UNAVAILABLE` | Redis에서 대상의 Refresh Token 계열을 안전하게 폐기할 수 없다. 계정 상태와 감사 이벤트를 변경하지 않으며 잠시 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | Redis 폐기 뒤 MySQL 상태 전이 또는 감사 기록에 실패했거나 예상하지 못한 서버 오류가 발생했다. MySQL 상태·감사 이벤트는 커밋하지 않으며, Redis에서 이미 폐기한 계열은 복구하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "ADMIN_ACCOUNT_DEACTIVATION_CONFLICT",
  "message": "전체관리자 계정을 비활성화할 수 없습니다.",
  "data": null
}
```
