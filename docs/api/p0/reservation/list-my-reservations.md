## 10. 내 예약 목록 조회

활성 회원이 본인 소유의 모든 예약을 예약 확정 시각 내림차순으로 조회한다.
이 API는 단순 목록이며 P0에서는 페이지네이션, 상태 필터와 사용자 지정 정렬을 적용하지 않는다.
조회 결과가 없으면 빈 배열을 반환하며 예약, 방문, 회차, 홀드와 정원 상태를 변경하지 않는다.

### Request

```http
GET /me/reservations
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/me/reservations
```

#### Request Example

```http
GET /api/v1/me/reservations HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
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
  "message": "내 예약 목록 조회에 성공했습니다.",
  "data": {
    "reservations": [
      {
        "reservationId": "123",
        "reservationNo": "R20260730A7K3M9Q2W5XZ",
        "status": "CONFIRMED",
        "quantity": 3,
        "confirmedAt": "2026-07-30T03:00:00Z",
        "content": {
          "contentId": "789",
          "title": "김해 가야문화 체험",
          "locationText": "김해시 대성동고분박물관"
        },
        "session": {
          "sessionId": "456",
          "status": "SCHEDULED",
          "startsAt": "2026-08-03T14:00:00+09:00",
          "endsAt": "2026-08-03T16:00:00+09:00"
        },
        "checkIn": {
          "checkedIn": false,
          "checkedAt": null,
          "visitId": null
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservations` | Array | 내 예약 목록. 결과가 없으면 빈 배열 `[]` |
| `data.reservations[].reservationId` | String | 예약 식별자 |
| `data.reservations[].reservationNo` | String | 시스템 전체에서 유일한 예약 번호. 서버가 `Asia/Seoul` 날짜의 `RyyyyMMdd`와 12자리 Crockford Base32 난수 접미사로 생성한다. |
| `data.reservations[].status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservations[].quantity` | Number | 예약이 소비한 홀드의 확정 인원. 항상 양수이며 회차의 공통 값이 아니다. |
| `data.reservations[].confirmedAt` | String | 예약 확정 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.reservations[].content.contentId` | String | 예약 콘텐츠 식별자 |
| `data.reservations[].content.title` | String | 예약 콘텐츠 제목 |
| `data.reservations[].content.locationText` | String | 예약 콘텐츠 위치 안내 |
| `data.reservations[].session.sessionId` | String | 예약 회차 식별자 |
| `data.reservations[].session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.reservations[].session.startsAt` | String | 회차 시작 시각 |
| `data.reservations[].session.endsAt` | String | 회차 종료 시각 |
| `data.reservations[].checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.reservations[].checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.reservations[].checkIn.visitId` | String or null | `checkedIn = true`인 경우 해당 예약·회차에 연결된 방문 기록 식별자. 그 외에는 `null` |

예약 QR 토큰, `qr_reference`, 사용자 식별자와 다른 예약자의 정보는 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니다. 조회 대상과 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 내 예약 목록 조회 중 예상하지 못한 서버 오류 또는 예약·회차·방문 연결 정합성 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있지만 정합성 오류는 해결 전까지 재시도해도 성공하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증 정보가 없거나 유효하지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 서버는 `reservation.user_id = 인증 회원 식별자` 조건을 만족하는 예약만 조회한다.
3. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 상태의 예약을 모두 포함한다. 콘텐츠 종료·중단·철회 또는 회차 완료·취소 뒤에도 본인 예약 이력과 콘텐츠 표시 정보를 유지한다.
4. 목록은 `confirmed_at` 내림차순, 같은 시각이면 `reservation_id` 내림차순으로 정렬한다.
5. P0에서는 페이지네이션, 상태 필터와 사용자 지정 정렬을 제공하지 않는다.
6. 예약이 없으면 `data.reservations`에 빈 배열 `[]`을 반환한다.
7. 각 항목은 예약의 `session_id`와 일치하는 회차, 회차의 `content_id`와 일치하는 콘텐츠만 반환하며, 연결된 콘텐츠의 `contentId`, `title`, `locationText`를 반환한다.
8. 각 예약은 소비한 홀드의 확정 인원 `quantity`를 반환하며, `quantity`는 항상 양수이고 회차의 공통 값으로 대체하지 않는다.
9. 예약 상태가 `CHECKED_IN`이면 같은 예약과 회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`, 그 방문의 `checked_at`과 `visitId`를 반환한다.
10. 예약 상태가 `CHECKED_IN`이 아니면 `checkedIn = false`, `checkedAt = null`, `visitId = null`을 반환한다.
11. `CHECKED_IN` 예약에 방문 기록이 없거나 방문의 예약·회차·콘텐츠·지역 연결이 일치하지 않으면 정상 목록 항목을 만들지 않고 정합성 오류로 관찰한다.
12. 조회 시 예약, 방문, 회차, 콘텐츠, 홀드, 정원, QR과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 결과 건수와 결과 코드만 구조화 로그로 남기며, 예약 번호·QR 참조·사용자 식별자와 개인정보 원문을 로그에 남기지 않는다.
- 목록의 각 `CHECKED_IN` 상태와 방문 기록 연결의 불일치는 데이터 정합성 오류로 관찰하고, 다른 예약의 정보로 대체해 응답하지 않는다.
