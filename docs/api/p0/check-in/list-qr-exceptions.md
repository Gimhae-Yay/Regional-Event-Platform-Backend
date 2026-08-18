## 3. QR 실패·보조 처리 목록 조회

담당 지역 관리자가 최근 90일의 QR 검증 실패와 예약번호 조회·보조 체크인 처리 기록을 최신순으로 조회한다.
별도 QR 예외 테이블을 만들지 않고 비개인 기준 기록인 `audit_event`를 조회 모델로 사용한다.

### Request

```http
GET /region-admin/qr-exceptions
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/region-admin/qr-exceptions
```

#### Request Example

```http
GET /api/v1/region-admin/qr-exceptions?size=20 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 지역 관리자여야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `cursor` | String | N | 다음 묶음을 조회하기 위한 불투명 커서. 최초 요청에서는 생략한다. |
| `size` | Integer | N | 한 번에 조회할 최대 건수. 기본값 `20`, 허용 범위 `1`~`100` |

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
  "message": "QR 예외 목록 조회에 성공했습니다.",
  "data": {
    "exceptions": [
      {
        "exceptionId": 901,
        "exceptionType": "QR_CHECK_IN_FAILURE",
        "result": "FAILURE",
        "reasonCode": "QR_CHECK_IN_SIGNATURE_INVALID",
        "reservationResolved": false,
        "reservationId": null,
        "contentId": null,
        "sessionId": null,
        "occurredAt": "2026-08-01T10:03:00+09:00"
      },
      {
        "exceptionId": 900,
        "exceptionType": "MANUAL_CHECK_IN",
        "result": "SUCCESS",
        "reasonCode": "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS",
        "reservationResolved": true,
        "reservationId": 123,
        "contentId": 77,
        "sessionId": 456,
        "occurredAt": "2026-08-01T10:02:00+09:00"
      }
    ],
    "nextCursor": "MjAyNi0wOC0wMVQxMDowMjowMCswOTowMHw5MDA",
    "hasNext": true
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.exceptions` | Array | 담당 지역의 QR 실패·보조 처리 기록. 결과가 없으면 빈 배열 |
| `data.exceptions[].exceptionId` | Long | 예외 단건 조회에 사용할 `audit_event_id` |
| `data.exceptions[].exceptionType` | String | `QR_CHECK_IN_FAILURE`, `RESERVATION_NUMBER_LOOKUP`, `MANUAL_CHECK_IN` 중 하나 |
| `data.exceptions[].result` | String | 감사 결과. `SUCCESS` 또는 `FAILURE` |
| `data.exceptions[].reasonCode` | String | 비개인 처리 사유 코드 |
| `data.exceptions[].reservationResolved` | Boolean | 검증된 서버 데이터로 예약을 안전하게 식별했으면 `true` |
| `data.exceptions[].reservationId` | Long or null | 안전하게 식별한 예약 식별자. 식별하지 못한 QR 실패이면 `null` |
| `data.exceptions[].contentId` | Long or null | 안전하게 식별한 콘텐츠 식별자 |
| `data.exceptions[].sessionId` | Long or null | 안전하게 식별한 회차 식별자 |
| `data.exceptions[].occurredAt` | String | MySQL 기준 이벤트 발생 시각 |
| `data.nextCursor` | String or null | 다음 묶음이 있으면 사용할 불투명 커서. 마지막 묶음이면 `null` |
| `data.hasNext` | Boolean | 다음 묶음 존재 여부 |

결과가 없으면 `200 OK`, `exceptions: []`, `nextCursor: null`, `hasNext: false`를 반환한다.

#### 예외 유형과 사유 매핑

| `exceptionType` | 대상 | 허용 `reasonCode` |
| --- | --- | --- |
| `QR_CHECK_IN_FAILURE` | `POST /operator/check-ins`의 검증·권한·상태 실패 | `QR_CHECK_IN_` 접두사의 [QR 체크인 감사 사유 코드](check-in-by-qr.md#감사-사유-코드) |
| `RESERVATION_NUMBER_LOOKUP` | QR 실패 후 예약번호 보조 조회 | `QR_VERIFICATION_FAILED` |
| `MANUAL_CHECK_IN` | 예약번호·사유 기반 보조 체크인 | `MANUAL_CHECK_IN_{handlingReason}_{outcome}` 형식의 [보조 체크인 감사 사유 코드](manual-check-in-by-reservation-number.md#감사-사유-코드) |

`reasonCode`는 감사 조회용 비개인 사유이며 HTTP 오류 응답의 `code`와 별도 필드다. 새 사유를 외부에 노출하려면
이 표와 생산하는 API 명세를 함께 변경한다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `size`가 허용 범위를 벗어나거나 `cursor`가 비어 있거나 현재 조회 범위에 사용할 수 없다. 상태를 변경하지 않으며 값을 수정하거나 최초 요청부터 다시 조회할 수 있다. |
| `400` | `INVALID_TYPE` | `size`의 형식이 올바르지 않다. 상태를 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 담당 지역 연결이 없다. 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 감사 기록 조회 중 예상하지 못한 서버 오류가 발생했다. 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

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
2. `audit_event.region_id`가 인증 지역 관리자의 담당 지역과 일치하는 이벤트만 조회한다.
3. 조회 대상은 위 표의 QR 체크인 전용 사유, 예약번호 보조 조회 사유와 예약번호 보조 체크인 사유를 가진 감사 이벤트로 한정한다. 일반 QR 체크인 성공은 목록에서 제외한다.
4. `exceptionType`은 `QR_CHECK_IN_`, `MANUAL_CHECK_IN_` 접두사와 `QR_VERIFICATION_FAILED` 값으로 결정한다. 세 집합은 서로 겹치지 않으며 데이터베이스 상태 값으로 추가 저장하지 않는다.
5. 예약번호 조회는 `RESERVATION`, 성공 보조 체크인은 `VISIT`, 예약을 확인한 실패는 `RESERVATION`을 감사 대상으로 사용한다. 예약을 식별하지 못한 QR·예약번호 실패도 `target_type = RESERVATION`, `target_id = null`로 기록한다. 조회 응답의 예약·회차·콘텐츠 식별자는 대상이 있으면 해당 대상의 검증된 관계에서 파생한다.
6. 예약 또는 방문 대상을 가진 이벤트는 대상 관계의 `region_id`, `audit_event.region_id`와 인증 지역 관리자의 담당 지역이 모두 일치해야 한다. 하나라도 다르면 다른 지역 정보를 반환하지 않고 정합성 오류로 처리한다.
7. QR 토큰을 검증하지 못해 예약을 안전하게 식별할 수 없으면 `reservationResolved = false`로 반환하고 예약·콘텐츠·회차 식별자를 모두 `null`로 둔다. 검증되지 않은 토큰 payload를 조회 결과에 사용하지 않는다.
8. 정렬은 `occurred_at DESC, audit_event_id DESC`로 고정한다.
9. 커서는 마지막 항목의 정렬 경계와 담당 지역 조회 범위를 표현하는 불투명 값이다. 클라이언트는 커서 내부를 해석하거나 수정하지 않는다.
10. 감사 이벤트 보관 기간이 90일이므로 파기된 기록은 조회할 수 없다.
11. 목록에는 이름·연락처, 예약번호, 사용자 식별자, QR 원문과 `qr_reference`를 포함하지 않는다.
12. 조회는 예약, 방문, 체크인, 정원과 감사 이벤트를 생성·수정·삭제하지 않는다.

### 관측 및 정합성

- 목록 조회 로그에는 `requestId`, 담당 지역 식별자, 반환 건수와 결과 코드만 기록한다.
- 페이지 묶음 안에서 동일 `audit_event_id`를 중복 반환하지 않는다.
- 다음 묶음 조회 중 새 이벤트가 추가돼도 커서 경계보다 최신인 이벤트를 뒤 페이지에 끼워 넣지 않는다.
