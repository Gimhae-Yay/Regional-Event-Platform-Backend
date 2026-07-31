## 12. 회차별 예약자 목록 및 개인정보 마스킹 조회

소유 운영자가 자신의 콘텐츠에 속한 특정 회차의 예약자 목록을 조회한다. 운영자는 현장 운영과 체크인 준비를 위해
예약 상태, 예약 인원과 체크인 상태를 확인할 수 있으며, 예약자 이름과 연락처는 마스킹된 값만 반환한다.

공통 페이지 계약의 페이지 번호·크기·메타데이터가 아직 확정되지 않았으므로, P0에서는 회차 한 건의 단순 목록을
반환한다. 이 API는 예약·방문·체크인·홀드·정원 상태를 변경하지 않는다.

### Request

```http
GET /operator/contents/{contentId}/reservations?sessionId={sessionId}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/operator/contents/{contentId}/reservations?sessionId={sessionId}
```

#### Request Example

```http
GET /api/v1/operator/contents/789/reservations?sessionId=456 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Header

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 승인된 소유 운영자여야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 예약자 목록을 조회할 콘텐츠 식별자. 양수여야 한다. |

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | String | Y | `contentId`에 속한 회차 식별자. 양수여야 한다. |

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
  "message": "회차별 예약자 목록 조회에 성공했습니다.",
  "data": {
    "contentId": "789",
    "session": {
      "sessionId": "456",
      "status": "SCHEDULED",
      "startsAt": "2026-08-01T10:00:00+09:00",
      "endsAt": "2026-08-01T12:00:00+09:00",
      "checkinOpenAt": "2026-08-01T09:30:00+09:00",
      "checkinCloseAt": "2026-08-01T10:30:00+09:00"
    },
    "reservations": [
      {
        "reservationId": "123",
        "reservationNo": "R20260730A7K3M9Q2W5XZ",
        "status": "CONFIRMED",
        "quantity": 2,
        "confirmedAt": "2026-07-29T03:00:00Z",
        "participant": {
          "name": "김*수",
          "phone": "010-****-1234"
        },
        "checkIn": {
          "checkedIn": false,
          "checkedAt": null
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
| `data.contentId` | String | 조회한 콘텐츠 식별자 |
| `data.session.sessionId` | String | 조회한 회차 식별자 |
| `data.session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.session.startsAt` | String | 회차 시작 시각 |
| `data.session.endsAt` | String | 회차 종료 시각 |
| `data.session.checkinOpenAt` | String | 체크인 가능 시작 시각 |
| `data.session.checkinCloseAt` | String | 체크인 가능 종료 시각 |
| `data.reservations` | Array | 회차 예약 목록. 예약이 없으면 빈 배열 `[]` |
| `data.reservations[].reservationId` | String | 예약 식별자 |
| `data.reservations[].reservationNo` | String | 시스템 전체에서 유일한 예약 번호 |
| `data.reservations[].status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservations[].quantity` | Integer | 예약 확정에 사용한 홀드 인원. 항상 양수 |
| `data.reservations[].confirmedAt` | String | 예약 확정 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.reservations[].participant.name` | String | 예약자 이름. `김*수` 형식으로 마스킹하며, 사용자 연결이 해제된 경우 `탈퇴한 사용자` |
| `data.reservations[].participant.phone` | String or null | 예약자 연락처. `010-****-1234` 형식으로 마스킹하며, 사용자 연결이 해제된 경우 `null` |
| `data.reservations[].checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.reservations[].checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` API 공통 규칙에 따른 UTC ISO 8601 일시다. |

`qr_reference`, QR 토큰, 사용자 식별자와 이름·연락처 원문은 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId` 또는 `sessionId`가 없거나 양수가 아니다. 조회 대상과 상태를 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 승인된 운영자가 아니거나 콘텐츠의 소유 운영자 또는 담당 지역이 인증 운영자와 일치하지 않는다. 조회 대상과 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠를 찾을 수 없거나, `sessionId`가 해당 콘텐츠와 지역에 속하지 않는다. 조회 대상과 상태를 변경하지 않으며 콘텐츠와 회차 식별자를 확인한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약·홀드·방문·회차 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있지만 정합성 오류는 해결 전까지 재시도해도 성공하지 않는다. |

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

1. 인증 주체는 승인된 담당 지역의 `OPERATOR`여야 한다.
2. 서버는 `contentId`로 콘텐츠를 찾은 뒤, 콘텐츠의 `operator_id`와 `region_id`가 인증 운영자의 소유·담당 지역과 일치하는지 검증한다.
3. `sessionId`는 조회한 콘텐츠와 같은 `content_id`, 같은 `region_id`를 가진 회차여야 한다. 일치하지 않으면 대상 부재로 처리한다.
4. 해당 회차의 `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 예약을 모두 반환한다. 상태 필터는 P0 범위에 포함하지 않는다.
5. 목록은 `confirmed_at` 오름차순, 같은 시각이면 `reservation_id` 오름차순으로 정렬한다. 페이지네이션과 사용자 지정 정렬은 P0 범위에 포함하지 않는다.
6. 예약이 없으면 `data.reservations`에 빈 배열 `[]`을 반환한다.
7. 각 예약은 해당 예약의 소비된 홀드와 연결된 `quantity`를 반환한다. 예약·홀드·회차·콘텐츠·지역 연결이 일치하지 않으면 정상 항목으로 대체하지 않고 정합성 오류로 처리한다.
8. 예약 상태가 `CHECKED_IN`이면 동일 예약·회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 방문의 `checked_at`을 반환한다. 그 외 상태는 `checkedIn = false`, `checkedAt = null`을 반환한다.
9. 예약자 이름과 연락처는 `AUTH-03`의 고정 형식으로 마스킹한다. 사용자 연결이 해제된 예약은 `탈퇴한 사용자`, `phone = null`을 반환한다.
10. 조회 시 예약, 홀드, 방문, 회차, 콘텐츠, 정원, QR과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 콘텐츠·회차 식별자, 결과 건수와 결과 코드만 구조화 로그로 남긴다. 예약 번호, QR 참조, 사용자 식별자와 개인정보 원문을 로그에 남기지 않는다.
- 예약의 `CHECKED_IN` 상태와 방문 기록, 예약의 소비된 홀드와 회차·지역 연결이 불일치하면 데이터 정합성 오류로 관찰하고, 다른 예약 정보로 대체해 응답하지 않는다.
