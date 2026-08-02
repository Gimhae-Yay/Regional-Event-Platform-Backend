# 인증·프로필 내 역할·담당 지역 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-01, AUTH-01 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

인증된 회원의 현재 역할과 역할별 담당 지역을 조회한다. 담당 지역은 `user_role_assignment`의 역할-지역 연결을
그대로 반환하며, 승인 대기 중인 운영자 신청은 역할이나 담당 지역으로 반환하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01, AUTH-01 | `GET /api/v1/me` | `app_user`, `user_role_assignment` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 유효한 Access Token이 필요하며, 조회 대상은 항상 인증 주체 본인이다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 역할·지역 연결을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 내 역할·담당 지역 조회

현재 회원이 실제로 부여받은 역할과 담당 지역을 조회한다. `roleAssignments`의 각 원소는 `role`, `regionId`, `regionName`만
반환한다. `VISITOR` 역할의 `regionId`와 `regionName`은 항상 `null`이며, `OPERATOR`와 `REGION_ADMIN` 역할의
`regionId`와 `regionName`은 각각 담당 지역의 식별자와 이름이다.

### Request

```http
GET /api/v1/me
```

#### Request Example

```http
GET /api/v1/me HTTP/1.1
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

없음.

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
  "message": "내 역할과 담당 지역 조회에 성공했습니다.",
  "data": {
    "roleAssignments": [
      {
        "role": "REGION_ADMIN",
        "regionId": "1",
        "regionName": "김해시"
      }
    ]
  }
}
```

`OPERATOR` 가입 신청이 아직 `PENDING`이면 역할이 부여되지 않았으므로 `roleAssignments`는 빈 배열이다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `내 역할과 담당 지역 조회에 성공했습니다.` |
| `data.roleAssignments` | Array | 현재 부여된 역할·담당 지역 연결. 역할이 없으면 빈 배열이다. |
| `data.roleAssignments[].role` | String | `VISITOR`, `OPERATOR`, `REGION_ADMIN` 중 하나 |
| `data.roleAssignments[].regionId` | String 또는 null | `VISITOR`이면 `null`, `OPERATOR` 또는 `REGION_ADMIN`이면 담당 지역 식별자. 양의 10진 문자열이다. |
| `data.roleAssignments[].regionName` | String 또는 null | `VISITOR`이면 `null`, `OPERATOR` 또는 `REGION_ADMIN`이면 담당 지역 이름 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 만료·변조되었다. 조회 상태는 변경되지 않으며, 유효한 Token으로 다시 요청할 수 있다. |
| 403 | `FORBIDDEN` | Access Token은 유효하지만 회원이 활성 상태가 아니거나 계정이 없다. 조회 상태는 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증 정보가 없거나 유효하지 않습니다.",
  "data": null
}
```
