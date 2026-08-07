# 내 스탬프 적립 이력 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-02](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-03`, `STB-04` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0067](../../../adr/0067-model-stampbook-and-mission-progress-from-immutable-visits.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 회원이 공개 중인 스탬프북 또는 본인 진행 이력이 있는 종료 스탬프북의 적립 이력과 원본 방문 정보를 조회한다. 적립 이력은
원본 방문을 덮어쓰지 않는 불변 근거이며, 공개 스탬프북에 아직 적립한 이력이 없으면 빈 배열을 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-02, STB-03, STB-04 | `GET /api/v1/me/stampbooks/{stampbookId}/earnings` | `stampbook_progress`, `stamp_earn`, `visit`, `content` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 한 진행에는 대상 콘텐츠별로
최대 한 적립만 존재하므로, 이력 수는 해당 스탬프북의 대상 콘텐츠 수를 넘지 않아 페이지네이션을 적용하지 않는다.

## 3. 내 스탬프 적립 이력 조회

### Request

```http
GET /api/v1/me/stampbooks/{stampbookId}/earnings
```

#### Request Example

```http
GET /api/v1/me/stampbooks/101/earnings HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `stampbookId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 스탬프북 식별자다. |

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
  "message": "내 스탬프 적립 이력 조회에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "earnings": [
      {
        "stampEarnId": "501",
        "visitId": "701",
        "content": {
          "contentId": "201",
          "title": "김해 가야문화 체험"
        },
        "visitedAt": "2026-08-06T00:50:00Z",
        "earnedAt": "2026-08-06T01:00:00Z"
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.earnings: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 조회한 스탬프북 식별자다. |
| `data.earnings` | Array | 인증 회원의 스탬프 적립 이력 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.earnings[].stampEarnId` | String | 스탬프 적립 식별자다. |
| `data.earnings[].visitId` | String | 적립을 증명한 본인의 유효 방문 식별자다. |
| `data.earnings[].content.contentId` | String | 스탬프를 적립한 대상 콘텐츠 식별자다. |
| `data.earnings[].content.title` | String | 적립 당시 대상 콘텐츠를 식별하기 위한 현재 콘텐츠 제목이다. |
| `data.earnings[].visitedAt` | String | 원본 방문의 체크인 완료 시각이다. UTC ISO 8601 형식이다. |
| `data.earnings[].earnedAt` | String | 스탬프 적립 확정 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `stampbookId`가 양의 10진 문자열 또는 signed 64비트 `Long` 범위를 만족하지 않는다. 조회 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 적립 이력을 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 스탬프북 진행의 소유자가 아니다. 적립 이력과 방문 근거를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 스탬프북이 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 적립·방문·콘텐츠 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 서버는 대상 스탬프북이 `PUBLISHED`이거나 `stampbook_progress.user_id = 인증 회원 식별자`인 진행 행이 있는지 검증한다.
2. `PUBLISHED` 스탬프북은 적립 전에도 조회할 수 있으며, 진행 행이 없으면 `data.earnings: []`를 반환한다. `DRAFT`, `PENDING_REVIEW`, 다른 회원의 진행만 있는 `ENDED` 스탬프북은 `FORBIDDEN`을 반환한다.
3. 이력은 `earnedAt` 내림차순, 같은 시각이면 `stampEarnId` 내림차순으로 고정 정렬한다.
4. 각 이력의 `visitId`는 `stamp_earn.visit_id`와 같고, 해당 방문의 사용자·콘텐츠는 진행 사용자와 `stamp_earn.content_id`에 각각 일치해야 한다.
5. 같은 진행의 동일 방문 또는 동일 콘텐츠에 대한 적립은 최대 한 건이다. 중복 전달·동시 처리로 새 이력이 추가되지 않는다.
6. 조회 시 스탬프북, 진행도, 적립 이력, 원본 방문, 쿠폰과 감사 이력을 생성·수정·삭제하지 않는다.
