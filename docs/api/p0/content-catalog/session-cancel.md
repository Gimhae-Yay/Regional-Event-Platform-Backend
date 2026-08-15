# 지역·콘텐츠 카탈로그 소유 운영자 회차 취소 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-06, AUTH-01, RSV-06, [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-05` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [유료 결제·환불](../../../p1/payment-refund.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 `SCHEDULED` 회차를 취소한다. 서버는 회차를 `CANCELLED`로 전환하고, 같은 트랜잭션에서 활성
홀드를 무효화하며 미체크인 `CONFIRMED` 예약만 취소한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-06, AUTH-01, RSV-06, P1-FR-08, PAY-05 | `POST /api/v1/operator/sessions/{sessionId}/cancel` | `content`, `content_session`, `capacity_hold`, `reservation`, `payment`, `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 요청·응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `OPERATOR` 역할, 담당 지역과 회차 지역의 일치, 회차 콘텐츠의 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 취소 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 명령이므로 적용하지 않는다. |

## 3. 소유 운영자의 회차 취소

`SCHEDULED → CANCELLED` 전이와 활성 홀드 무효화, `CONFIRMED → CANCELLED` 예약 전이, 성공 감사 기록을 하나의
트랜잭션으로 처리한다. 성공 감사 기록에는 처리자 역할과 활성 처리자 연결, 취소 사유, 회차의 `SCHEDULED → CANCELLED`
전이, 처리 시각을 남긴다. 회차 시작 전의 예약 취소만 정원을 한 번 복구하고, `CHECKED_IN` 예약·방문·후기와 이미
종결된 예약은 변경하지 않는다. 이 핵심 취소 처리의 어느 변경이나 감사 기록이 실패하면 모두 롤백한다. 핵심 취소 처리가
완료되면 취소된 확정 예약의 결제·환불 후처리를 수행한다. 외부 환불 호출과 결과 반영은 장시간 데이터베이스 잠금을
유지하지 않도록 별도의 짧은 트랜잭션 경계를 사용한다. 별도 전달 알림은 P0 범위에 포함하지 않는다.

### 처리 규칙

1. 회차·홀드·미체크인 확정 예약의 취소와 정원 복구는 `RSV-06`을 따른다.
2. 취소된 확정 예약에 `APPROVED` 또는 `DISCREPANT` 결제가 연결돼 있으면 `PAY-05`에 따라 결제 최종 금액 전체의
   환불과 최초 환불 시도를 준비하고 PortOne 취소 절차를 시작한다. 환불 상태 전이와 외부 결과 매핑은
   [유료 예약 취소 API](../../p1/reservation/cancel-reservation.md#처리-규칙)와
   [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md#결정)을 따른다.
3. 기존 결과 매핑 계약에 따라 환불 결과가 `FAILED` 또는 원인과 무관하게 `DISCREPANT`로 저장돼도 완료된 회차·홀드·예약
   취소를 실패로 전환하지 않는다. 환불 결과는 `refund`, `refund_attempt`과 `REFUND` 감사 이력에 보존하고 쿠폰을
   복구하지 않는다.
4. 회차 취소 응답에는 환불 상태를 포함하지 않는다. 환불 상태는
   [담당 예약 결제·환불 상태 조회 API](../../p1/payment/get-operator-reservation-payment.md)를 사용해 별도로 조회한다.

### Request

```http
POST /api/v1/operator/sessions/{sessionId}/cancel
```

#### Request Example

```http
POST /api/v1/operator/sessions/21/cancel HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "cancellationReason": "기상 악화로 회차를 진행할 수 없습니다."
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
| `sessionId` | String | Y | API 공통 규칙을 따르는 취소할 회차 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "cancellationReason": "기상 악화로 회차를 진행할 수 없습니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `cancellationReason` | String | Y | 앞뒤 공백을 제거한 비어 있지 않은 최대 500자 텍스트여야 한다. `null`, 빈 문자열, 공백만으로 된 값 및 500자를 초과한 값은 허용하지 않는다. |

### Response

#### Status

```http
200 OK
```

환불 결과가 `FAILED` 또는 원인과 무관하게 `DISCREPANT`로 저장된 경우에도 `200 OK`를 반환한다. 이 성공 응답은
회차·홀드·미체크인 확정 예약의 취소가 완료됐다는 의미이며 환불 성공을 의미하지 않는다. 응답 DTO에는 환불 상태를
추가하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "회차 취소에 성공했습니다.",
  "data": {
    "sessionId": "21",
    "status": "CANCELLED",
    "cancellationReason": "기상 악화로 회차를 진행할 수 없습니다.",
    "cancelledAt": "2026-08-14T06:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `회차 취소에 성공했습니다.` |
| `data.sessionId` | String | API 공통 규칙에 따른 취소한 회차 식별자 |
| `data.status` | String | 취소 후 상태인 `CANCELLED` |
| `data.cancellationReason` | String | 회차와 미체크인 예약에 기록한 취소 사유 |
| `data.cancelledAt` | String | 회차 취소 사건 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 회차 식별자가 양의 정수가 아니거나 취소 사유가 누락·공백이거나 500자를 초과한다. 회차·홀드·예약은 변경되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 회차·홀드·예약은 변경되지 않는다. |
| 400 | `INVALID_TYPE` | 회차 식별자를 정수로 변환할 수 없다. 회차·홀드·예약은 변경되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 회차·홀드·예약은 변경되지 않는다. |
| 403 | `FORBIDDEN` | `OPERATOR` 역할, 담당 지역 또는 회차 콘텐츠의 소유 관계가 없다. 회차·홀드·예약은 변경되지 않는다. |
| 404 | `NOT_FOUND` | 회차가 없다. 회차·홀드·예약은 변경되지 않는다. |
| 409 | `SESSION_NOT_CANCELLABLE` | 회차가 `SCHEDULED`가 아니다. 회차·홀드·예약은 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "SESSION_NOT_CANCELLABLE",
  "message": "취소할 수 없는 회차 상태입니다.",
  "data": null
}
```
