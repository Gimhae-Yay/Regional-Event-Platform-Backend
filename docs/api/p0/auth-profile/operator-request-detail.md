# 인증·프로필 운영자 승인 요청 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-09, AUTH-02 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

지역 관리자가 담당 지역의 운영자 신청 상세를 조회한다. 서버는 신청의 요청 지역과 인증된 지역 관리자의
`REGION_ADMIN` 담당 지역이 같은 경우에만 응답한다. 사업자 정보 원문은 응답에 포함하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-09, AUTH-02 | `GET /api/v1/region-admin/operator-requests/{requestId}` | `user_role_assignment`, `operator_application`, `region` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `REGION_ADMIN` 역할과 담당 지역 배정이 필요하며, 서버가 요청 지역 경계를 강제한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 심사에 필요한 신청 상세를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 운영자 승인 요청 상세 조회

신청의 현재 상태를 조회한다. 탈퇴 처리로 신청자 연결이 제거된 `CANCELLED` 신청은 해당 필드를 `null`로 반환하며,
다른 지역의 신청은 존재 여부를 노출하지 않는다.

### Request

```http
GET /api/v1/region-admin/operator-requests/{requestId}
```

#### Request Example

```http
GET /api/v1/region-admin/operator-requests/21 HTTP/1.1
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
  "message": "운영자 승인 요청 상세 조회에 성공했습니다.",
  "data": {
    "operatorApplicationId": 21,
    "applicantUserId": 7,
    "requestedRegionId": 1,
    "status": "PENDING",
    "inspectedUserId": null,
    "rejectedReason": null,
    "requestedAt": "2026-07-29T10:15:30+09:00",
    "updatedAt": "2026-07-29T10:15:30+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `운영자 승인 요청 상세 조회에 성공했습니다.` |
| `data.operatorApplicationId` | Long | 운영자 신청 식별자. 양의 정수다. |
| `data.applicantUserId` | Long 또는 null | 신청자 회원 식별자. 탈퇴 처리된 `CANCELLED` 신청이면 `null`이다. |
| `data.requestedRegionId` | Long | 요청 지역 식별자. 인증된 지역 관리자의 담당 지역과 같다. |
| `data.status` | String | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` 중 하나 |
| `data.inspectedUserId` | Long 또는 null | 승인·반려 처리자 식별자. `PENDING` 또는 `CANCELLED`이면 `null`이다. |
| `data.rejectedReason` | String 또는 null | `REJECTED`일 때의 반려 사유. 다른 상태면 `null`이다. |
| `data.requestedAt` | String | 신청 생성 시각. ISO 8601 오프셋 일시다. |
| `data.updatedAt` | String | 신청의 마지막 변경 시각. 승인·반려가 종결되면 심사 시각이다. ISO 8601 오프셋 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `requestId`가 양의 정수가 아니다. 조회 상태는 변경되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_TYPE` | `requestId`를 정수로 변환할 수 없다. 조회 상태는 변경되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 만료·변조되었다. 조회 상태는 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| 403 | `FORBIDDEN` | `REGION_ADMIN` 역할 또는 담당 지역 배정이 없다. 조회 상태는 변경되지 않는다. |
| 404 | `NOT_FOUND` | 신청이 없거나 인증된 지역 관리자의 담당 지역에 속하지 않는다. 조회 상태는 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
