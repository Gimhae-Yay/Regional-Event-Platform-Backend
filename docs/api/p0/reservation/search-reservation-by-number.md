## 11. QR 실패 시 예약번호 보조 조회

소유 운영자 또는 담당 지역 관리자가 QR 검증 실패 상황에서 예약 번호로 담당 범위의 예약을 보조 조회한다.
예약 번호는 시스템 전체에서 유일하므로 하나의 `reservationNo`는 정확히 한 예약만 식별한다.

이 API는 예약·방문·체크인·정원을 변경하지 않는다. 다만 QR 실패 보조 조회의 사유, 처리자와 처리 시각은
감사 이벤트로 남긴다.

### Request

```http
GET /operator/reservations/search?reservationNo={reservationNo}
GET /region-admin/reservations/search?reservationNo={reservationNo}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/operator/reservations/search?reservationNo={reservationNo}
GET /api/v1/region-admin/reservations/search?reservationNo={reservationNo}
```

#### Request Example

```http
GET /api/v1/operator/reservations/search?reservationNo=R202607290001 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Header

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 승인된 소유 운영자 또는 담당 지역 관리자여야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationNo` | String | Y | 시스템 전체에서 유일한 예약 번호. 공백만으로 구성할 수 없다. |

#### Request Body

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
  "message": "예약번호 보조 조회에 성공했습니다.",
  "data": {
    "reservationId": 123,
    "reservationNo": "R202607290001",
    "status": "CONFIRMED",
    "content": {
      "contentId": 789,
      "title": "김해 가야문화 체험"
    },
    "session": {
      "sessionId": 456,
      "status": "SCHEDULED",
      "startsAt": "2026-08-01T10:00:00+09:00",
      "endsAt": "2026-08-01T12:00:00+09:00",
      "checkinOpenAt": "2026-08-01T09:30:00+09:00",
      "checkinCloseAt": "2026-08-01T10:30:00+09:00"
    },
    "participant": {
      "name": "김*수",
      "phone": "010-****-1234"
    },
    "checkIn": {
      "checkedIn": false,
      "canCheckIn": true,
      "checkedAt": null
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | Long | 예약 식별자 |
| `data.reservationNo` | String | 시스템 전체에서 유일한 예약 번호 |
| `data.status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.content.contentId` | Long | 예약 콘텐츠 식별자 |
| `data.content.title` | String | 예약 콘텐츠 제목 |
| `data.session.sessionId` | Long | 예약 회차 식별자 |
| `data.session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.session.startsAt` | String | 회차 시작 시각 |
| `data.session.endsAt` | String | 회차 종료 시각 |
| `data.session.checkinOpenAt` | String | 체크인 가능 시작 시각 |
| `data.session.checkinCloseAt` | String | 체크인 가능 종료 시각 |
| `data.participant.name` | String | 예약자 이름. `김*수` 형식으로 마스킹하며, 작성자 연결이 해제된 경우 `탈퇴한 사용자` |
| `data.participant.phone` | String or null | 예약자 연락처. `010-****-1234` 형식으로 마스킹하며, 작성자 연결이 해제된 경우 `null` |
| `data.checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.checkIn.canCheckIn` | Boolean | 운영자 조회에서 현재 소유권·회차 상태·예약 상태·체크인 창을 모두 만족해 체크인을 시작할 수 있으면 `true`. 지역 관리자 조회에서는 항상 `false` |
| `data.checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` |

`qr_reference`, QR 토큰, 사용자 식별자, 원문 이름·연락처와 다른 예약자의 정보는 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationNo`가 없거나 공백만으로 구성됐다. 예약·방문·체크인·정원과 감사 기록을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자·지역 관리자가 아니거나, 조회한 예약이 인증 주체의 역할별 소유·지역 범위와 일치하지 않는다. 조회 대상과 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | `reservationNo`와 일치하는 예약이 없다. 예약·방문·체크인·정원과 감사 기록을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·회차·콘텐츠·방문 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 롤백되며 성공 감사 이벤트를 남기지 않는다. |

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

1. 인증 주체는 승인된 담당 지역의 `OPERATOR` 또는 `REGION_ADMIN`이어야 한다.
2. 서버는 `reservation_no`로 예약을 조회한 뒤, 역할별 권한 범위를 검증한다. `OPERATOR`는 예약의 지역, 콘텐츠의 소유 운영자와 콘텐츠 지역이 인증 운영자의 소유·담당 지역과 일치해야 한다. `REGION_ADMIN`은 예약의 지역과 콘텐츠 지역이 인증 지역 관리자의 담당 지역과 일치해야 하며 콘텐츠 소유 운영자 일치는 요구하지 않는다.
3. `reservation_no`의 전역 `UNIQUE` 제약으로 조회 결과는 최대 한 건이다. 동일 번호의 복수 예약 중 하나를 임의로 반환하지 않는다.
4. 이 API의 확인 사유는 항상 `QR_VERIFICATION_FAILED`다. 클라이언트는 별도 사유를 입력하지 않는다.
5. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 상태의 예약을 조회할 수 있다. 다만 `CANCELLED`, `EXPIRED`, `CHECKED_IN` 예약은 `canCheckIn = false`다.
6. `OPERATOR` 응답의 `canCheckIn = true`는 예약이 `CONFIRMED`, 회차가 `SCHEDULED`, 현재 MySQL 시각이 `checkin_open_at <= now < checkin_close_at`이고 현재 운영자의 소유권·담당 지역 검증을 모두 통과한 경우에만 반환한다.
7. `REGION_ADMIN` 응답의 `canCheckIn`은 항상 `false`다. 지역 관리자는 담당 지역 예외 건과 마스킹된 예약자 단건만 조회할 수 있으며 운영자 소유권을 우회해 후속 체크인을 처리할 수 없다.
8. `CHECKED_IN` 예약은 동일 예약·회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 방문의 `checked_at`을 반환한다.
9. `CHECKED_IN`이 아닌 예약은 `checkedIn = false`, `checkedAt = null`을 반환한다.
10. 예약·방문·회차·콘텐츠·지역의 연결이 일치하지 않으면 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
11. 보조 조회 성공은 예약, 방문, 체크인, 홀드와 정원 상태를 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 성공한 보조 조회는 하나의 트랜잭션에서 `QR_VERIFICATION_FAILED` 사유, 처리자, 처리자 역할, 예약·회차·콘텐츠·지역 식별자와 MySQL 기준 처리 시각을 `audit_event`에 기록한다.
- 감사 이벤트에는 `reservationNo`, `qr_reference`, 사용자 식별자, 이름·연락처 원문을 저장하지 않는다.
- 보조 조회와 성공 감사 이벤트는 함께 커밋한다. 감사 이벤트 기록에 실패하면 성공 응답을 반환하지 않는다.
- 권한 없음, 대상 없음과 정합성 오류는 예약·방문·체크인·정원 상태를 변경하지 않는다. 구조화 로그에는 `requestId`, 결과 코드와 비개인 식별자만 남긴다.
