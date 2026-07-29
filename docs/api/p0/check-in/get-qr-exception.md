## 4. QR 예외·마스킹 예약자 단건 조회

담당 지역 관리자가 QR 실패 또는 보조 처리 기록 한 건과 안전하게 연결된 예약자의 마스킹 정보를 조회한다.
지역 관리자는 이 응답으로 예약자를 확인할 수 있지만 운영자 소유권을 우회해 체크인할 수 없다.

### Request

```http
GET /region-admin/qr-exceptions/{exceptionId}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/region-admin/qr-exceptions/{exceptionId}
```

#### Request Example

```http
GET /api/v1/region-admin/qr-exceptions/900 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 대상 이벤트 지역의 활성 지역 관리자여야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `exceptionId` | Long | Y | 조회할 QR 실패·보조 처리 `audit_event_id`. 양수여야 한다. |

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
  "message": "QR 예외 상세 조회에 성공했습니다.",
  "data": {
    "exceptionId": 900,
    "exceptionType": "MANUAL_CHECK_IN",
    "result": "SUCCESS",
    "reasonCode": "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS",
    "occurredAt": "2026-08-01T10:02:00+09:00",
    "reservationResolved": true,
    "reservation": {
      "reservationId": 123,
      "reservationNo": "R202607290001",
      "status": "CHECKED_IN",
      "contentId": 77,
      "contentTitle": "김해 도자기 체험",
      "sessionId": 456,
      "startsAt": "2026-08-01T10:00:00+09:00",
      "checkinOpenAt": "2026-08-01T09:30:00+09:00",
      "checkinCloseAt": "2026-08-01T10:30:00+09:00",
      "participant": {
        "memberLinked": true,
        "name": "김*수",
        "phone": "010-****-1234"
      },
      "checkIn": {
        "checkedIn": true,
        "canCheckIn": false,
        "checkedAt": "2026-08-01T10:02:00+09:00"
      }
    }
  }
}
```

예약을 안전하게 식별하지 못한 QR 검증 실패는 `reservationResolved = false`, `reservation = null`로 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.exceptionId` | Long | 조회한 `audit_event_id` |
| `data.exceptionType` | String | `QR_CHECK_IN_FAILURE`, `RESERVATION_NUMBER_LOOKUP`, `MANUAL_CHECK_IN` 중 하나 |
| `data.result` | String | 감사 결과. `SUCCESS` 또는 `FAILURE` |
| `data.reasonCode` | String | 비개인 처리 사유 코드 |
| `data.occurredAt` | String | MySQL 기준 이벤트 발생 시각 |
| `data.reservationResolved` | Boolean | 검증된 서버 데이터로 예약을 안전하게 식별했으면 `true` |
| `data.reservation` | Object or null | 안전하게 연결한 예약과 마스킹 참여자 정보. 식별하지 못했으면 `null` |
| `data.reservation.reservationId` | Long | 예약 식별자 |
| `data.reservation.reservationNo` | String | 시스템 전체에서 유일한 예약 번호 |
| `data.reservation.status` | String | `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservation.contentId` | Long | 예약 콘텐츠 식별자 |
| `data.reservation.contentTitle` | String | 예약 콘텐츠 제목 |
| `data.reservation.sessionId` | Long | 예약 회차 식별자 |
| `data.reservation.startsAt` | String | 회차 시작 시각 |
| `data.reservation.checkinOpenAt` | String | 체크인 창 시작 시각 |
| `data.reservation.checkinCloseAt` | String | 체크인 창 종료 시각 |
| `data.reservation.participant.memberLinked` | Boolean | 예약에 활성 회원 연결이 남아 있으면 `true` |
| `data.reservation.participant.name` | String | `김*수` 형식의 마스킹 이름. 작성자 연결이 해제되면 `탈퇴한 사용자` |
| `data.reservation.participant.phone` | String or null | `010-****-1234` 형식의 마스킹 연락처. 작성자 연결이 해제되면 `null` |
| `data.reservation.checkIn.checkedIn` | Boolean | 현재 예약 상태가 `CHECKED_IN`이고 일치하는 방문이 있으면 `true` |
| `data.reservation.checkIn.canCheckIn` | Boolean | 지역 관리자는 체크인 권한이 없으므로 항상 `false` |
| `data.reservation.checkIn.checkedAt` | String or null | 체크인됐다면 일치하는 방문의 최초 체크인 시각. 그 외에는 `null` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `exceptionId`가 양수가 아니다. 상태와 감사 기록을 변경하지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `exceptionId`의 형식이 올바르지 않다. 상태와 감사 기록을 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 상태와 감사 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 이벤트 지역이 인증 주체의 담당 지역과 다르다. 예약·방문 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 감사 이벤트가 없거나 QR 실패·보조 처리 조회 범위에 속하지 않는다. 상태와 감사 기록을 변경하지 않으며 식별자를 확인한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 감사·예약·회차·콘텐츠·방문 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태이며 담당 `region_id`가 연결된 `REGION_ADMIN`이어야 한다.
2. `exceptionId`로 QR 실패·보조 처리 대상 `audit_event`를 조회하고 이벤트의 `region_id`가 인증 주체의 담당 지역과 일치하는지 검증한다.
3. QR 예외 목록에서 제외되는 일반 감사 이벤트는 이 API로 조회할 수 없다.
4. 이벤트가 검증된 예약 또는 방문을 참조하면 대상에서 예약·회차·콘텐츠를 조회하고, 각 대상의 `region_id`, `audit_event.region_id`와 인증 지역 관리자의 담당 지역이 모두 일치하는지 검증한다. 하나라도 다르면 다른 지역 정보를 반환하지 않고 정합성 오류로 처리한다.
5. QR 검증 단계에서 예약을 안전하게 식별하지 못한 이벤트는 검증되지 않은 토큰 payload로 대상을 추정하지 않고 `reservation = null`로 반환한다.
6. 이벤트의 결과·사유·발생 시각은 감사 발생 시점의 불변 기록이고, `reservation` 객체는 조회 시점의 현재 예약·방문 상태다.
7. 활성 회원 연결이 있으면 이름과 연락처를 각각 `김*수`, `010-****-1234` 형식으로 마스킹한다.
8. 예약의 회원 연결이 제거됐으면 이름은 공통 표시 `탈퇴한 사용자`, 연락처는 `null`, `memberLinked = false`로 반환한다.
9. `CHECKED_IN` 예약은 같은 예약·회차·콘텐츠·지역에 연결된 방문이 정확히 한 건 존재해야 한다. 불일치하면 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
10. 지역 관리자의 `canCheckIn`은 항상 `false`다. 지역 관리자는 운영자 소유권을 우회해 QR 또는 보조 체크인 명령을 호출할 수 없다.
11. 응답에는 사용자 식별자, 원문 이름·연락처, QR 원문, `qr_reference`와 HMAC 정보를 포함하지 않는다.
12. 조회는 예약, 방문, 체크인, 홀드와 정원 상태를 변경하지 않는다.

### 관측 및 정합성

- 단건 조회는 새 `audit_event`를 생성하지 않는다.
- 구조화 로그에는 `requestId`, 담당 지역 식별자, `exceptionId`와 결과 코드만 기록하며 예약번호, 사용자 식별자, 이름·연락처, QR 원문과 `qr_reference`를 기록하지 않는다.
