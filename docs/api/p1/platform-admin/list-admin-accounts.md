# 전체관리자 계정 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0064](../../../adr/0064-separate-privileged-account-class-from-ordinary-roles.md), [ADR-0108](../../../adr/0108-use-global-authority-snapshot-for-first-stage-rbac.md), [ADR-0113](../../../adr/0113-separate-platform-admin-self-access-from-account-list.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 `ROLE_SUPER_ADMIN` authority snapshot을 가진 활성 `PRIVILEGED` 계정이 전체관리자 계정의 등급과 배정
상태를 확인하는 HTTP API 계약을 정의한다. `ROLE_PLATFORM_ADMIN`은 다른 고권한 계정의 목록을 조회할 수 없다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01 | `GET /api/v1/platform-admin/admin-accounts` | `app_user`, `platform_admin_assignment` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [전체관리자 API](platform-admin.md#2-공통-계약-참조)를 따른다. 전체관리자 계정 수가 제한적인
운영 목록이므로 페이지네이션과 등급·상태 필터를 제공하지 않는다.

## 3. 전체관리자 계정 목록 조회

조회 대상은 `app_user.account_kind = PRIVILEGED`이면서
`platform_admin_assignment.user_id = app_user.user_id`로 연결되는 계정과 고권한 배정이다. `SUPER_ADMIN`과
`PLATFORM_ADMIN`, `ACTIVE`와 `INACTIVE`를 모두 반환한다. 비활성 고권한 배정도 일반 계정으로 전환하지 않는 정책에 따라
조회 대상에 포함한다. 탈퇴 완료로 `platform_admin_assignment.user_id`가 `null`이 되어 계정과 연결되지 않는 이력은 반환하지
않는다.

조회는 계정, 고권한 배정과 감사 이력을 생성·수정하지 않는다.

### Request

```http
GET /api/v1/platform-admin/admin-accounts
```

#### Request Example

```http
GET /api/v1/platform-admin/admin-accounts HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. `ROLE_SUPER_ADMIN` authority가 필요하다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음. 페이지네이션과 등급·상태 필터를 적용하지 않는다.

결과는 `createdAt` 내림차순, 같은 시각이면 `userId` 내림차순으로 고정한다.

#### Request Body

없음.

#### Request Field

없음.

### 인증·인가

[공통 권한 행렬](../../common/authentication.md#권한-행렬)의 전체관리자 계정 목록 조회 계약을 적용한다.

- 유효한 Access Token의 authority가 `ROLE_SUPER_ADMIN`이어야 한다.
- DB 최종 검증에서 요청자의 `app_user.status = ACTIVE`와 `account_kind = PRIVILEGED`를 확인한다.
- 요청자의 현재 고권한 등급이나 고권한 배정의 활성 여부를 DB에서 authority 판정 근거로 다시 사용하지 않는다.
- Access Token이 없거나 유효하지 않으면 `401 UNAUTHENTICATED`, authority 또는 DB 최종 인가가 부족하면
  `403 FORBIDDEN`이다.

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
  "message": "전체관리자 계정 목록 조회에 성공했습니다.",
  "data": {
    "adminAccounts": [
      {
        "userId": "101",
        "loginIdentifier": "admin@example.com",
        "name": "관리자",
        "grade": "PLATFORM_ADMIN",
        "status": "INACTIVE",
        "createdAt": "2026-08-06T01:00:00Z",
        "inactivatedAt": "2026-08-19T03:00:00Z"
      }
    ]
  }
}
```

빈 결과는 `200 OK`와 `adminAccounts: []`를 반환한다.

#### Response Field

| Name | Type | Nullable | Description |
| --- | --- | --- | --- |
| `statusCode` | Integer | N | HTTP 상태와 같은 `200`이다. |
| `code` | String | N | 성공 코드 `SUCCESS`다. |
| `message` | String | N | 성공 메시지다. |
| `data` | Object | N | 전체관리자 계정 목록 응답 객체다. |
| `data.adminAccounts` | Array | N | 고권한 계정과 배정의 배열이다. 결과가 없으면 빈 배열이다. |
| `data.adminAccounts[].userId` | String | N | 양의 10진 정수 문자열인 고권한 계정 식별자다. |
| `data.adminAccounts[].loginIdentifier` | String | N | 계정을 식별하기 위한 로그인 식별자다. 현재 이메일을 사용한다. |
| `data.adminAccounts[].name` | String | N | 계정 표시 이름이다. |
| `data.adminAccounts[].grade` | String | N | `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 중 하나다. |
| `data.adminAccounts[].status` | String | N | 고권한 배정 상태인 `ACTIVE` 또는 `INACTIVE` 중 하나다. |
| `data.adminAccounts[].createdAt` | String | N | `platform_admin_assignment.granted_at`의 UTC ISO 8601 값이다. |
| `data.adminAccounts[].inactivatedAt` | String | Y | `platform_admin_assignment.inactivated_at`의 UTC ISO 8601 값이다. `ACTIVE`이면 `null`, `INACTIVE`이면 `null`이 아니다. |

응답에는 전화번호, 비밀번호 원문·해시, Access Token, Refresh Token, 비활성화 사유, 감사 증빙과 계약되지 않은 개인정보를
포함하지 않는다. `loginIdentifier`와 `name`은 계정을 식별하고 표시하기 위한 최소 개인정보이므로 응답 본문을 로그·감사
이력에 기록하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 계정·고권한 배정·감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 유효한 Token에 필요한 authority가 없거나 요청자가 활성 `PRIVILEGED` 계정이 아니다. 계정·고권한 배정·감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 계정·고권한 배정·감사 이력을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```
