# 인증·프로필 운영자 신청 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-09, AUTH-02 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 `PENDING` 운영자 신청을 반려하고 사유를 남긴다. 반려된 신청은 `REJECTED`로 종결하며,
신청자는 새 신청 행을 만들어 다시 신청할 수 있다. 반려 결과와 감사 이벤트는 같은 트랜잭션에서 기록한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-09, AUTH-02 | `POST /api/v1/region-admin/operator-requests/{requestId}/reject` | `operator_application`, `user_role_assignment`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 요청·응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot으로 1차 인가하고, DB에서 활성 `ORDINARY` 계정과 신청 요청 지역에 대한 현재 담당 지역 일치를 확인한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 반려 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 명령이므로 적용하지 않는다. |

## 3. 운영자 신청 사유 포함 반려

`PENDING` 신청을 반려하고 반려 사유와 처리자·시각을 기록한다. 같은 트랜잭션에서 `operator_application`을 대상으로
요청 지역, `PENDING → REJECTED` 전이, `reason_code = OPERATOR_APPLICATION_REJECTED`, `REGION_ADMIN` 처리자 역할과 활성
처리자 연결을 포함한 성공 감사 이벤트를 기록한다. 반려 사유 원문은 `operator_application.rejected_reason`에만 기록하고
감사 이벤트에는 넣지 않는다. 사업자 정보 원문은 성공 응답, 애플리케이션·접근 로그, 감사 이벤트, 오류 응답과 지표에
포함하지 않는다. 상태 전이 또는 감사 기록이 실패하면 모두 롤백한다. 이미 `REJECTED`인 신청을 다시 반려하면 저장된
결과를 반환하며 감사 이벤트를 추가하지 않는다. 반려 시 `OPERATOR` 역할과 담당 지역을 생성하거나 변경하지 않는다.

### Request

```http
POST /api/v1/region-admin/operator-requests/{requestId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/operator-requests/21/reject HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "rejectedReason": "제출한 사업자 정보로는 해당 지역의 운영 자격을 확인할 수 없습니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `requestId` | Long | Y | 운영자 신청 식별자. 양의 정수다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "rejectedReason": "제출한 사업자 정보로는 해당 지역의 운영 자격을 확인할 수 없습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `rejectedReason` | String | Y | 앞뒤 공백을 제거한 1~2,000자 텍스트여야 한다. `null`, 빈 문자열, 공백만으로 된 값은 허용하지 않는다. 최대 길이 초과는 `INVALID_INPUT`이다. |

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
  "message": "운영자 신청 반려에 성공했습니다.",
  "data": {
    "operatorApplicationId": 21,
    "status": "REJECTED",
    "rejectedReason": "제출한 사업자 정보로는 해당 지역의 운영 자격을 확인할 수 없습니다.",
    "processedAt": "2026-07-29T11:00:00+09:00"
  }
}
```

이미 `REJECTED`인 신청을 다시 반려하면 새 사유로 덮어쓰지 않고 기존 반려 결과를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `운영자 신청 반려에 성공했습니다.` |
| `data.operatorApplicationId` | Long | 운영자 신청 식별자. 양의 정수다. |
| `data.status` | String | 반려 후 상태인 `REJECTED` |
| `data.rejectedReason` | String | 최초 반려 때 기록한 반려 사유 |
| `data.processedAt` | String | 반려 처리 시각. `operator_application.updated_at`과 같은 ISO 8601 오프셋 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `requestId`가 양의 정수가 아니거나 `rejectedReason`이 누락·공백·2,000자 초과다. 신청 상태와 감사 기록은 변경되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 신청 상태는 변경되지 않으며 본문을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_TYPE` | `requestId`를 정수로 변환할 수 없다. 신청 상태는 변경되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 만료·변조되었다. 신청 상태는 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| 403 | `FORBIDDEN` | 공통 권한 행렬 또는 이 API의 활성 계정·담당 지역 조건을 충족하지 않는다. 신청 상태는 변경되지 않는다. |
| 404 | `NOT_FOUND` | 신청이 없거나 인증된 지역 관리자의 담당 지역에 속하지 않는다. 신청 상태는 변경되지 않는다. |
| 409 | `OPERATOR_APPLICATION_STATE_CONFLICT` | 신청이 `APPROVED` 또는 `CANCELLED`여서 반려할 수 없다. 신청 상태는 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "OPERATOR_APPLICATION_STATE_CONFLICT",
  "message": "운영자 신청 상태가 요청과 일치하지 않습니다.",
  "data": null
}
```
