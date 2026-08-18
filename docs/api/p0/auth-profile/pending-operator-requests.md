# 인증·프로필 운영자 승인 요청 대기 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-09, AUTH-02 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

지역 관리자가 자신의 담당 지역에 접수된 `PENDING` 운영자 신청 목록을 조회한다. 요청 지역 식별자는 클라이언트가
전달하지 않으며, 서버가 인증된 지역 관리자의 `REGION_ADMIN` 담당 지역으로 범위를 제한한다. 사업자 정보 원문은
목록 데이터, 오류 응답, 애플리케이션·접근 로그, 감사 이벤트와 지표에 포함하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-09, AUTH-02 | `GET /api/v1/region-admin/operator-requests?status=PENDING` | `user_role_assignment`, `operator_application`, `region` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | Access Token의 `ROLE_REGION_ADMIN` authority를 1차로 확인하고, DB에서 활성 `ORDINARY` 계정과 현재 담당 지역 관계를 확인해 지역 경계를 강제한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 빈 배열을 포함한 목록을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 심사 대기 목록 전체를 `createdAt` 오름차순, `requestId` 오름차순으로 반환하므로 페이지네이션을 적용하지 않는다. |

## 3. 운영자 승인 요청 대기 목록 조회

담당 지역의 심사 대기 신청을 먼저 접수된 순서로 조회한다. 목록 응답에는 사업자 정보 원문을 포함하지 않으며,
담당 지역 관리자는 심사용 상세 조회에서만 이를 확인한다. 다른 지역 관리자·운영자·방문자 계정에는 목록을 반환하지 않는다.

### Request

```http
GET /api/v1/region-admin/operator-requests?status=PENDING
```

#### Request Example

```http
GET /api/v1/region-admin/operator-requests?status=PENDING HTTP/1.1
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

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | `PENDING`만 허용한다. 다른 값, 빈 값 또는 누락은 허용하지 않는다. |

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
  "message": "운영자 승인 요청 대기 목록 조회에 성공했습니다.",
  "data": {
    "operatorRequests": [
      {
        "operatorApplicationId": 21,
        "applicantUserId": 7,
        "requestedRegionId": 1,
        "requestedAt": "2026-07-29T10:15:30+09:00"
      }
    ]
  }
}
```

대기 신청이 없으면 `200 OK`와 `data.operatorRequests: []`를 반환한다. 목록은 `createdAt` 오름차순,
같은 시각이면 `requestId`(`operatorApplicationId`) 오름차순으로 정렬한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `운영자 승인 요청 대기 목록 조회에 성공했습니다.` |
| `data.operatorRequests` | Array | 인증된 지역 관리자의 담당 지역에 속한 `PENDING` 신청 목록. 없으면 빈 배열이다. |
| `data.operatorRequests[].operatorApplicationId` | Long | 운영자 신청 식별자. 양의 정수이다. |
| `data.operatorRequests[].applicantUserId` | Long | 심사 대상 회원 식별자. 양의 정수이다. |
| `data.operatorRequests[].requestedRegionId` | Long | 인증된 지역 관리자의 담당 지역과 같은 요청 지역 식별자 |
| `data.operatorRequests[].requestedAt` | String | 신청 생성 시각. ISO 8601 오프셋 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `status`가 누락되었거나 `PENDING`이 아닌 값이다. 목록 상태는 변경되지 않으며 `status=PENDING`으로 다시 요청할 수 있다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 만료·변조되었다. 목록 상태는 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| 403 | `FORBIDDEN` | 공통 권한 행렬 또는 이 API의 활성 계정·담당 지역 조건을 충족하지 않는다. 목록 상태는 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```
