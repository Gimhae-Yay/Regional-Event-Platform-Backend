# 인증·프로필 본인 운영자 신청 현황 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-09, AUTH-02 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0116](../../../adr/0116-use-latest-operator-application-for-status-and-reapplication.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

인증된 회원의 신청자 연결이 유지된 운영자 신청 중 가장 최근 신청 한 건을 조회한다. 신청 상태와 비민감 심사 결과를
반환해 클라이언트가 심사 중, 반려, 승인 상태를 표시하고 `REJECTED`일 때만 재신청 동작을 제공할 수 있게 한다.

가장 최근 신청은 `created_at` 내림차순, 같은 시각이면 `operator_application_id` 내림차순으로 정렬한 첫 행이다.
조회 결과는 재신청 성공을 보장하지 않으며, 재신청 API는 제출 트랜잭션에서 역할과 가장 최근 신청 상태를 다시 확인한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-09, AUTH-02 | `GET /api/v1/me/operator-application` | `app_user`, `operator_application`, `region` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 응답은 `application/json; charset=UTF-8`이다. 신청·심사 시각은 UTC ISO 8601 사건 시각으로 반환한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 유효한 Access Token과 활성 회원 상태가 필요하며, 조회 대상은 항상 인증 주체 본인이다. 역할은 요구하지 않는다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 가장 최근 신청 객체 또는 `null`을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 가장 최근 신청 한 건만 조회하므로 적용하지 않는다. |

## 3. 본인 운영자 신청 현황 조회

신청자 연결이 유지된 본인의 가장 최근 운영자 신청을 조회한다. 사업자 정보 원문, 심사자 식별자·프로필과 신청자
개인정보는 반환하지 않는다.

### Request

```http
GET /api/v1/me/operator-application
```

#### Request Example

```http
GET /api/v1/me/operator-application HTTP/1.1
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

가장 최근 신청이 `REJECTED`인 경우:

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "본인 운영자 신청 현황 조회에 성공했습니다.",
  "data": {
    "operatorApplication": {
      "operatorApplicationId": "21",
      "status": "REJECTED",
      "requestedRegionId": "1",
      "requestedRegionName": "김해시",
      "createdAt": "2026-08-20T01:15:30Z",
      "reviewedAt": "2026-08-21T03:20:10Z",
      "rejectedReason": "사업자 정보를 확인할 수 없습니다."
    }
  }
}
```

신청 이력이 없는 경우:

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "본인 운영자 신청 현황 조회에 성공했습니다.",
  "data": {
    "operatorApplication": null
  }
}
```

#### 상태별 필드 계약

| 상태 | `reviewedAt` | `rejectedReason` | 재신청 제출 |
| --- | --- | --- | --- |
| `PENDING` | `null` | `null` | 허용하지 않으며 POST가 `OPERATOR_APPLICATION_PENDING`을 반환한다. |
| `REJECTED` | 심사 시각 | 반려 사유 | 허용 후보이며 POST가 제출 시점의 최신 상태를 다시 검증한다. |
| `APPROVED` | 심사 시각 | `null` | 허용하지 않는다. 승인된 운영자 안내에 사용할 수 있다. |

회원 탈퇴에서 `PENDING` 신청을 `CANCELLED`로 종결할 때 모든 신청 상태의 신청자 연결과 사업자 정보를 제거하고
계정을 파기한다. 따라서 `CANCELLED`는 현재 인증된 본인 조회에서 반환하는 상태가 아니다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `본인 운영자 신청 현황 조회에 성공했습니다.` |
| `data.operatorApplication` | Object 또는 null | 신청자 연결이 유지된 가장 최근 운영자 신청. 신청 이력이 없으면 `null`이다. |
| `data.operatorApplication.operatorApplicationId` | String | 운영자 신청 식별자. 양의 10진 정수 문자열이다. |
| `data.operatorApplication.status` | String | `PENDING`, `REJECTED`, `APPROVED` 중 하나 |
| `data.operatorApplication.requestedRegionId` | String | 신청한 공개 지역 식별자. 양의 10진 정수 문자열이다. |
| `data.operatorApplication.requestedRegionName` | String | 신청한 공개 지역 이름 |
| `data.operatorApplication.createdAt` | String | 신청 생성 시각. UTC ISO 8601 사건 시각이다. |
| `data.operatorApplication.reviewedAt` | String 또는 null | `APPROVED` 또는 `REJECTED` 심사 종결 시 `updated_at`에 기록한 UTC ISO 8601 사건 시각. `PENDING`이면 `null`이다. |
| `data.operatorApplication.rejectedReason` | String 또는 null | `REJECTED`일 때의 반려 사유. 다른 상태면 `null`이다. |

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
