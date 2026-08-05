# 전체관리자의 사용자 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자 API](platform-admin.md), [전체관리자](../../../p1/platform-admin.md), [ERD](../../../erd.md), [ADR-0063](../../../adr/0063-use-global-admin-role-in-existing-user-role-assignment.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 활성 전체관리자가 역할 관리 대상을 확인하기 위한 사용자 목록 조회 HTTP API 계약을 정의한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01 | `GET /api/v1/admin/users` | `app_user`, `user_role_assignment`, `region` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [전체관리자 API](platform-admin.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지네이션을 적용하지 않는다.

## 3. 전체관리자의 사용자 목록 조회

활성 전체관리자가 역할 관리 대상을 확인하기 위해 활성 회원 목록과 현재 역할·담당 지역을 조회한다. 조회는
`app_user`나 역할 연결을 변경하지 않으며, 탈퇴 진행 중인 `WITHDRAWING` 회원과 이미 파기된 회원은 반환하지 않는다.

### Request

```http
GET /api/v1/admin/users
```

#### Request Example

```http
GET /api/v1/admin/users HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

결과는 `createdAt` 내림차순, 같은 시각이면 `userId` 내림차순으로 고정한다.

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
  "message": "사용자 목록 조회에 성공했습니다.",
  "data": {
    "users": [
      {
        "userId": "101",
        "loginIdentifier": "operator@example.com",
        "name": "홍길동",
        "roleAssignments": [
          {
            "role": "OPERATOR",
            "regionId": "1",
            "regionName": "김해시"
          }
        ],
        "createdAt": "2026-08-05T01:00:00Z"
      }
    ]
  }
}
```

빈 결과는 `200 OK`와 `users: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.users` | Array | 활성 회원 배열이다. 결과가 없으면 빈 배열이다. |
| `data.users[].userId` | String | 양의 10진 정수 문자열인 회원 식별자다. |
| `data.users[].loginIdentifier` | String | 역할 관리 대상을 식별하기 위한 로그인 식별자다. 현재 이메일을 사용한다. |
| `data.users[].name` | String | 회원의 이름이다. |
| `data.users[].roleAssignments` | Array | 현재 부여된 역할·담당 지역 연결이다. 역할이 없으면 빈 배열이다. |
| `data.users[].roleAssignments[].role` | String | `VISITOR`, `OPERATOR`, `REGION_ADMIN`, `ADMIN` 중 하나다. |
| `data.users[].roleAssignments[].regionId` | String 또는 null | `VISITOR`, `ADMIN`이면 `null`이고, `OPERATOR`, `REGION_ADMIN`이면 담당 지역 식별자다. |
| `data.users[].roleAssignments[].regionName` | String 또는 null | `regionId`가 `null`이면 `null`이고, 그 외에는 담당 지역 이름이다. |
| `data.users[].createdAt` | String | UTC ISO 8601 형식의 회원 생성 시각이다. |

응답에는 전화번호, 비밀번호·해시, 사업자 정보, Access Token, Refresh Token을 포함하지 않는다. `loginIdentifier`와
`name`은 역할 관리 대상을 정확히 식별하기 위한 최소 개인정보이므로, 응답 본문을 로그·감사 이력에 기록하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 상태는 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `ADMIN` 역할을 갖지 않는다. 조회 상태는 변경되지 않으며 사용자 목록과 개인정보를 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 조회 상태는 변경되지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```
