# 전체관리자 계정 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01`, `ADM-05` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [ADR-0064](../../../adr/0064-bootstrap-and-deactivate-platform-admin-accounts.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 활성 전체관리자가 추가 전체관리자 계정을 생성하는 HTTP API 계약을 정의한다. 최초 전체관리자 계정은
승인된 배포 절차로만 준비하며 이 API로 생성하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts` | `app_user`, `user_role_assignment`, `audit_event` |

## 2. 공통 계약 참조

생성·응답·오류 규칙은 [전체관리자 API](platform-admin.md#2-공통-계약-참조)를 따른다. 이 API는 목록 API가 아니므로
페이지네이션을 적용하지 않는다.

## 3. 전체관리자 계정 생성

활성 `PLATFORM_ADMIN`이 새 활성 계정과 전역 `PLATFORM_ADMIN` 역할을 생성한다. 요청의 지역 또는 역할을 받지 않으며, 계정·역할·성공
감사 이벤트 중 하나라도 실패하면 모두 생성하지 않는다.

### Request

```http
POST /api/v1/platform-admin/admin-accounts
```

#### Request Example

```http
POST /api/v1/platform-admin/admin-accounts HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "email": "admin@example.com",
  "password": "LocalStamp!2026",
  "name": "관리자",
  "phone": "01012345678",
  "reason": "플랫폼 운영 관리자 추가"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "email": "admin@example.com",
  "password": "LocalStamp!2026",
  "name": "관리자",
  "phone": "01012345678",
  "reason": "플랫폼 운영 관리자 추가"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `email` | String | Y | 이메일 형식, 최대 254자다. 앞뒤 공백을 제거하고 소문자로 정규화하며 기존 로그인 식별자와 중복될 수 없다. |
| `password` | String | Y | 8~64자이면서 UTF-8 인코딩 기준 72바이트 이하다. 영문자·숫자·특수문자를 각각 하나 이상 포함해야 하며 원문은 응답·로그·감사 이력에 남기지 않는다. |
| `name` | String | Y | 앞뒤 공백을 제거한 1~50자다. 빈 문자열·공백만으로 된 값은 허용하지 않는다. |
| `phone` | String | Y | 숫자 10~11자리다. 입력의 하이픈은 제거하고 숫자만 저장한다. |
| `reason` | String | Y | 앞뒤 공백을 제거한 1~500자 생성 사유다. 빈 문자열·공백만으로 된 값은 허용하지 않으며 성공 감사 이벤트에 기록한다. |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "전체관리자 계정 생성에 성공했습니다.",
  "data": {
    "userId": "101",
    "role": "PLATFORM_ADMIN",
    "status": "ACTIVE",
    "createdAt": "2026-08-05T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.userId` | String | 새 전체관리자 계정의 양의 10진 정수 문자열 식별자다. |
| `data.role` | String | 항상 `PLATFORM_ADMIN`이다. |
| `data.status` | String | 새 계정 상태 `ACTIVE`다. |
| `data.createdAt` | String | UTC ISO 8601 형식의 계정 생성 시각이다. |

응답에는 이메일, 전화번호, 비밀번호 원문·해시, Access Token, Refresh Token, 감사 사유를 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 필수값 누락 또는 요청 필드의 형식·길이 위반이다. 계정·역할·감사 이벤트를 생성하지 않으며 값을 수정해 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 계정·역할·감사 이벤트를 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 계정·역할·감사 이벤트를 생성하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `PLATFORM_ADMIN` 역할을 갖지 않는다. 계정·역할·감사 이벤트를 생성하지 않는다. |
| `409` | `DUPLICATE_LOGIN_IDENTIFIER` | 정규화한 이메일과 같은 로그인 식별자가 이미 존재한다. 계정·역할·감사 이벤트를 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 계정·역할·감사 이벤트를 생성하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "DUPLICATE_LOGIN_IDENTIFIER",
  "message": "이미 사용 중인 이메일입니다.",
  "data": null
}
```
