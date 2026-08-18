# 전체관리자 계정 비활성화 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01`, `ADM-05` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0087](../../../adr/0087-bootstrap-and-inactivate-privileged-admin-accounts.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 `ROLE_SUPER_ADMIN` snapshot을 가진 활성 `PRIVILEGED` 계정이 다른 고권한 계정을 비활성화하는 HTTP API 계약을 정의한다. 자기 자신과 마지막 활성
슈퍼관리자는 비활성화할 수 없다. 재활성화 API는 현재 제공하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate` | `platform_admin_assignment`, `audit_event` |

## 2. 공통 계약 참조

상태 전이·응답·오류 규칙은 [전체관리자 API](platform-admin.md#2-공통-계약-참조)를 따른다. 이 API는 목록 API가
아니므로 페이지네이션을 적용하지 않는다.

## 3. 전체관리자 계정 비활성화

`ROLE_SUPER_ADMIN` snapshot을 가진 활성 `PRIVILEGED` 계정이 다른 활성 고권한 배정을 `ACTIVE → INACTIVE`로 전이한다. 이때 `inactivated_at`과
`inactive_reason_code`를 기록하며 `app_user` 상태는 바꾸지 않는다. 배정 상태 전이와 성공 감사 이벤트는 함께 커밋한다.

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
  "reasonCode": "ADMIN_ACCOUNT_INACTIVATION",
  "evidenceReference": "OPS-2026-0806-002"
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
| `userId` | String | Y | 양의 10진 정수 문자열이며 signed 64비트 `Long` 범위를 만족하는 고권한 계정 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reasonCode": "ADMIN_ACCOUNT_INACTIVATION",
  "evidenceReference": "OPS-2026-0806-002"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reasonCode` | String | Y | 비어 있지 않은 비활성화 사유 코드다. `platform_admin_assignment.inactive_reason_code`와 성공 감사 이벤트에 기록한다. |
| `evidenceReference` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 운영 증빙 참조다. 비밀번호·토큰·API 키·결제수단 전체 정보 등 비밀값을 포함할 수 없으며 성공 감사 이벤트의 `evidence_reference`에 기록한다. |

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
    "platformAdminAssignmentId": "201",
    "grade": "PLATFORM_ADMIN",
    "status": "INACTIVE",
    "inactivatedAt": "2026-08-06T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.userId` | String | 비활성화한 고권한 계정의 식별자다. |
| `data.platformAdminAssignmentId` | String | 비활성화한 고권한 배정의 식별자다. |
| `data.grade` | String | 비활성화한 배정의 등급이다. |
| `data.status` | String | 항상 `INACTIVE`다. |
| `data.inactivatedAt` | String | UTC ISO 8601 형식의 비활성화 완료 시각이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `userId`를 양의 정수 식별자로 처리할 수 없다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `400` | `INVALID_INPUT` | `userId`가 양의 10진 문자열·signed 64비트 범위를 만족하지 않거나 `reasonCode`, `evidenceReference`가 누락·공백·형식 위반이거나 증빙 참조에 비밀값이 포함됐다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_SUPER_ADMIN` authority가 없거나 활성 `PRIVILEGED` 계정이 아니다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 사용자에게 고권한 배정이 없다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `409` | `ADMIN_ACCOUNT_DEACTIVATION_CONFLICT` | 처리자 자신, 마지막 활성 `SUPER_ADMIN`, 비활성 배정은 비활성화할 수 없다. 고권한 배정·감사 이벤트를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 고권한 배정·감사 이벤트를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "ADMIN_ACCOUNT_DEACTIVATION_CONFLICT",
  "message": "전체관리자 계정을 비활성화할 수 없습니다.",
  "data": null
}
```
