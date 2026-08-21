# 전체관리자 본인 권한 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [ADR-0108](../../../adr/0108-use-global-authority-snapshot-for-first-stage-rbac.md), [ADR-0113](../../../adr/0113-separate-platform-admin-self-access-from-account-list.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

유효한 Access Token에 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` authority snapshot을 가진 활성
`PRIVILEGED` 계정이 본인의 식별자와 Token에 적용된 고권한 등급을 조회하는 HTTP API 계약을 정의한다. 다른 고권한
계정 정보와 고권한 배정의 현재 상태는 조회하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01 | `GET /api/v1/platform-admin/me` | `app_user`, Access Token authority snapshot |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` authority snapshot과 활성 `PRIVILEGED` 계정 최종 검증이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 인증 주체의 `userId`, Token authority snapshot의 `grade`를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 전체관리자 본인 권한 조회

인증 주체의 `userId`와 현재 요청의 Access Token authority snapshot에 해당하는 `grade`만 반환한다. `grade`는
`SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 중 하나다. 다른 고권한 계정 정보, 일반 역할, 담당 지역, Access Token,
Refresh Token과 고권한 배정의 현재 `status`는 반환하지 않는다.

조회는 계정, 고권한 배정과 감사 이력을 생성·수정하지 않는다.

### Request

```http
GET /api/v1/platform-admin/me
```

#### Request Example

```http
GET /api/v1/platform-admin/me HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` authority가 필요하다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### 인증·인가

[공통 권한 행렬](../../common/authentication.md#권한-행렬)의 전체관리자 본인 조회 계약을 적용한다.

- 유효한 Access Token의 authority가 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN`이어야 한다.
- DB 최종 검증에서 요청자의 `app_user.status = ACTIVE`와 `account_kind = PRIVILEGED`를 확인한다.
- `grade`는 현재 요청의 Access Token authority snapshot에서 결정한다.
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
  "message": "전체관리자 본인 권한 조회에 성공했습니다.",
  "data": {
    "userId": "101",
    "grade": "PLATFORM_ADMIN"
  }
}
```

#### Response Field

| Name | Type | Nullable | Description |
| --- | --- | --- | --- |
| `statusCode` | Integer | N | HTTP 상태와 같은 `200`이다. |
| `code` | String | N | 성공 코드 `SUCCESS`다. |
| `message` | String | N | 성공 메시지다. |
| `data` | Object | N | 전체관리자 본인 권한 응답 객체다. |
| `data.userId` | String | N | 인증 주체의 양의 10진 정수 문자열 식별자다. |
| `data.grade` | String | N | 현재 Access Token authority snapshot에 해당하는 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이다. |

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
