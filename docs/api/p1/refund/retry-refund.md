# 환불 재시도 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-07`, `ADM-04` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 `FAILED` 환불에 대해 남은 횟수 안에서 PortOne 취소를 다시
호출한다. 자동 재시도는 없으며, 총 외부 호출 횟수는 3회를 넘길 수 없다. `DISCREPANT` 환불은 먼저
[환불 실패 수동 조치](resolve-refund-failure.md)로 `FAILED`를 확정해야 재시도할 수 있다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-07, ADM-04 | `POST /api/v1/platform-admin/refunds/{refundId}/retry` | `refund`, `refund_attempt` |

## 2. 공통 계약 참조

조치·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다.

## 3. 환불 재시도

### Request

```http
POST /api/v1/platform-admin/refunds/{refundId}/retry
```

#### Request Example

```http
POST /api/v1/platform-admin/refunds/552/retry HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `refundId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 환불 식별자다. |

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
  "message": "환불 재시도에 성공했습니다.",
  "data": {
    "refundId": "552",
    "attemptNo": 2,
    "status": "SUCCEEDED",
    "attemptedAt": "2026-08-07T06:00:00Z"
  }
}
```

재시도한 외부 호출이 응답을 받지 못하면 `data.status`가 `DISCREPANT`로 반환되며, 다음 재시도 전에
[환불 실패 수동 조치](resolve-refund-failure.md)로 다시 확정해야 한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.refundId` | String | 재시도한 환불 식별자다. |
| `data.attemptNo` | Integer | 이번에 점유한 시도 순번이다. 1~3 범위다. |
| `data.status` | String | 재시도 이후 환불 상태다. `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.attemptedAt` | String | 이번 외부 호출 시도 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `refundId`를 양의 정수 식별자로 처리할 수 없다. 재시도하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 환불·시도 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 환불·시도 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 환불이 없다. 환불·시도 상태를 변경하지 않는다. |
| `409` | `REFUND_STATE_CONFLICT` | 대상 환불이 `FAILED`가 아니거나(`DISCREPANT`는 수동 조치가 먼저 필요하다), 이미 총 3회 시도를 모두 사용했다. 새 외부 호출을 만들지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | PortOne 호출 실패를 포함해 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 환불·시도 상태를 변경하지 않으며 일시적 장애라면 동일한 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REFUND_STATE_CONFLICT",
  "message": "환불 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다. `FAILED` 환불의 재시도는 이 두 역할만 수행할 수 있다.
2. 대상 환불은 `FAILED`여야 한다. `DISCREPANT`는 [환불 실패 수동 조치](resolve-refund-failure.md)로 먼저 `FAILED`를 확정해야 하며, 그 외 상태는 `409 REFUND_STATE_CONFLICT`로 거부한다.
3. 기존 `refund_attempt`의 최대 `attempt_no`가 3이면 더 이상 재시도할 수 없고 `409 REFUND_STATE_CONFLICT`로 거부한다.
4. 외부 호출 직전에 `refund_attempt(PENDING, attempt_no = 기존 최대값 + 1)`를 기록해 시도 번호를 점유한다. PortOne 환불 호출의 최대 응답 대기 시간은 30초다.
5. 응답을 받으면 외부 상태와 응답 원문 해시를 저장해 `RESPONDED`로 확정한다. 성공이면 `refund`를 `SUCCEEDED`로 전이하고 `completed_at`을 기록하며, 명시적 실패면 `FAILED`를 유지한다.
6. 타임아웃·연결 단절·네트워크 실패처럼 응답을 받지 못하면 응답 값은 저장하지 않고 비밀값 없는 실패 사유와 함께 `NO_RESPONSE`로 확정하며, `refund`를 `DISCREPANT`로 전이한다. 이 시도도 총 3회에 포함되며, 다음 재시도 전에 수동 조치로 다시 확정해야 한다.
7. 응답 확정 전 프로세스가 종료돼 `PENDING`으로 남은 시도는 이 API가 아니라 1분 고정 지연 복구 작업이 PortOne 재조회로 같은 시도 행을 확정하며, 새 `attempt_no`나 외부 호출을 만들지 않는다.
8. 이중 승인은 적용하지 않는다. 처리자 1명의 요청만으로 재시도가 확정된다.
9. 재시도와 서버가 부여한 `requestId`를 포함한 `REFUND` 감사 이력은 하나의 트랜잭션으로 처리한다.
10. 결제 비밀값, PortOne 원문과 전체 결제수단 정보는 저장하지 않는다.
