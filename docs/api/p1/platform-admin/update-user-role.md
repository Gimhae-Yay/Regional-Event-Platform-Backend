# 지역관리자 역할 부여·회수 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-03`, `ADM-05` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0065](../../../adr/0065-use-is-public-for-region-availability-and-history-roles.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot을 가진 활성 `PRIVILEGED` 계정이 `ORDINARY` 계정에 지역 관리자(`REGION_ADMIN`) 역할과
담당 지역을 임명하거나 기존 임명을 회수하는 HTTP API 계약을 정의한다. `OPERATOR` 역할과 담당 지역은
[운영자 신청 승인](../../p0/auth-profile/operator-request-approve.md)에서 이미 부여하며, 고권한
(`SUPER_ADMIN`·`PLATFORM_ADMIN`) 배정은 [전체관리자 계정 생성](create-admin-account.md)이 별도로 관리하므로
이 API의 대상이 아니다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-03, ADM-05 | `PATCH /api/v1/platform-admin/users/{userId}/role` | `user_role_assignment`, `app_user`, `audit_event` |

## 2. 공통 계약 참조

전이·응답·오류 규칙은 [전체관리자 API](platform-admin.md#2-공통-계약-참조)를 따른다. 이 API는 목록 API가
아니므로 페이지네이션을 적용하지 않는다.

## 3. 지역관리자 역할 부여·회수

`ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot을 가진 활성 `PRIVILEGED` 계정이 `user_role_assignment`에 `REGION_ADMIN` 배정을 새로 만들거나
기존 활성 배정을 `REVOKED`로 전환한다. 한 사용자는 한 시점에 최대 하나의 활성 `REGION_ADMIN` 배정만 가지며,
다른 지역으로 재배정하면 기존 활성 배정을 회수하고 같은 트랜잭션에서 새 배정을 만든다. 같은 지역으로
재요청하면 새 배정을 만들지 않고 기존 활성 배정을 그대로 반환한다. 비삭제 콘텐츠가 있는 지역의 마지막
활성 지역 관리자는 회수할 수 없다. 배정 전이와 성공 감사 이벤트는 함께 커밋한다.

### Request

```http
PATCH /api/v1/platform-admin/users/{userId}/role
```

#### Request Example

```http
PATCH /api/v1/platform-admin/users/2001/role HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "role": "REGION_ADMIN",
  "regionId": "12",
  "reasonCode": "REGION_ADMIN_APPOINTMENT",
  "evidenceReference": "OPS-2026-0807-004"
}
```

회수 요청은 `regionId` 없이 `role`을 `NONE`으로 보낸다.

```http
PATCH /api/v1/platform-admin/users/2001/role HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "role": "NONE",
  "reasonCode": "REGION_ADMIN_REVOCATION",
  "evidenceReference": "OPS-2026-0807-005"
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
| `userId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 대상 사용자 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "role": "REGION_ADMIN",
  "regionId": "12",
  "reasonCode": "REGION_ADMIN_APPOINTMENT",
  "evidenceReference": "OPS-2026-0807-004"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `role` | String | Y | 적용할 역할이다. `REGION_ADMIN` 또는 `NONE` 중 하나만 허용한다. `REGION_ADMIN`이면 새 임명을, `NONE`이면 기존 활성 배정의 회수를 뜻한다. |
| `regionId` | String | N | 양의 10진 문자열인 담당 지역 식별자다. `role = REGION_ADMIN`이면 필수이며, `role = NONE`이면 생략해야 한다. |
| `reasonCode` | String | Y | 비어 있지 않은 처리 사유 코드다. `role = NONE`이면 `user_role_assignment.revoke_reason_code`에 기록하고, `role = REGION_ADMIN`이면 성공 감사 이벤트에만 기록한다. |
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
  "message": "지역관리자 역할 변경에 성공했습니다.",
  "data": {
    "userId": "2001",
    "roleAssignmentId": "305",
    "role": "REGION_ADMIN",
    "regionId": "12",
    "status": "ACTIVE",
    "grantedAt": "2026-08-07T02:00:00Z",
    "revokedAt": null
  }
}
```

`role = NONE`으로 회수하면 같은 형식으로 `data.role`이 `null`, `data.status`가 `REVOKED`, `data.revokedAt`이
회수 시각으로 채워져 반환되며, `data.regionId`에는 회수된 배정이 담당하던 지역이 그대로 남는다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.userId` | String | 대상 사용자 식별자다. |
| `data.roleAssignmentId` | String | 생성되었거나 회수된 역할 배정 식별자다. |
| `data.role` | String 또는 null | 처리 뒤 활성 역할이다. 임명이면 `REGION_ADMIN`, 회수면 `null`이다. |
| `data.regionId` | String | 배정이 담당하는(했던) 지역 식별자다. |
| `data.status` | String | 배정 상태다. `ACTIVE` 또는 `REVOKED` 중 하나다. |
| `data.grantedAt` | String | UTC ISO 8601 형식의 배정 시각이다. |
| `data.revokedAt` | String 또는 null | UTC ISO 8601 형식의 회수 시각이다. `ACTIVE`면 `null`이다. |

응답에는 `reasonCode`, `evidenceReference`와 같은 감사 사유를 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `userId` 또는 `regionId`를 양의 정수 식별자로 처리할 수 없다. 역할·감사 이벤트를 변경하지 않는다. |
| `400` | `INVALID_INPUT` | `role`이 `REGION_ADMIN`·`NONE`이 아니거나, `role = REGION_ADMIN`인데 `regionId`가 없거나, `role = NONE`인데 `regionId`가 있거나, `reasonCode`·`evidenceReference`가 누락·공백·형식 위반이거나 증빙 참조에 비밀값이 포함됐다. 역할·감사 이벤트를 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 역할·감사 이벤트를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 역할·감사 이벤트를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` authority가 없거나 활성 `PRIVILEGED` 계정이 아니다. 역할·감사 이벤트를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 사용자가 없거나 비활성 상태다, 또는 `role = REGION_ADMIN`인데 대상 지역이 없다. 역할·감사 이벤트를 변경하지 않는다. |
| `409` | `ROLE_ASSIGNMENT_CONFLICT` | 대상 계정이 `PRIVILEGED`다, `role = NONE`인데 활성 `REGION_ADMIN` 배정이 없다, 또는 비삭제 콘텐츠가 있는 지역의 마지막 활성 지역 관리자를 회수하려 한다. 역할을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 역할·감사 이벤트를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "ROLE_ASSIGNMENT_CONFLICT",
  "message": "역할을 변경할 수 없습니다.",
  "data": null
}
```
