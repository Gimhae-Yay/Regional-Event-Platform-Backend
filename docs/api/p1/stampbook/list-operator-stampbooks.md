# 운영자 스탬프북 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 담당 지역이고 현재 연결된 모든 대상 콘텐츠를 소유한 스탬프북을 조회한다.
`DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED`를 모두 반환하며, 이 API는 상태·대상 콘텐츠·완료 보상 정책·감사 이력을 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `GET /api/v1/operator/stampbooks` | `stampbook`, `stampbook_content`, `content`, `coupon_policy` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지·커서·총 건수·상태 필터와 사용자 지정 정렬을 제공하지 않는다.

## 3. 운영자 스탬프북 목록 조회

### Request

```http
GET /api/v1/operator/stampbooks
```

#### Request Example

```http
GET /api/v1/operator/stampbooks HTTP/1.1
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
  "message": "운영자 스탬프북 목록 조회에 성공했습니다.",
  "data": {
    "stampbooks": [
      {
        "stampbookId": "101",
        "title": "김해 가야 문화 완주",
        "regionId": "1",
        "status": "PUBLISHED",
        "targetCount": 3,
        "rewardCouponPolicyId": "301",
        "publishedAt": "2026-08-01T01:00:00Z",
        "endedAt": null
      },
      {
        "stampbookId": "102",
        "title": "김해 역사 산책",
        "regionId": "1",
        "status": "DRAFT",
        "targetCount": 2,
        "rewardCouponPolicyId": "302",
        "publishedAt": null,
        "endedAt": null
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.stampbooks: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbooks` | Array | 인증 운영자의 담당·소유 범위 스탬프북 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.stampbooks[].stampbookId` | String | 양의 10진 문자열인 스탬프북 식별자다. |
| `data.stampbooks[].title` | String | 스탬프북에 저장된 고유 제목이다. 앞뒤 공백 제거 뒤 1~100자이며 대상 콘텐츠 제목으로 조합하지 않는다. |
| `data.stampbooks[].regionId` | String | 인증 운영자의 담당 지역과 같은 스탬프북 소속 지역 식별자다. |
| `data.stampbooks[].status` | String | 스탬프북 상태다. `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 중 하나다. |
| `data.stampbooks[].targetCount` | Integer | 현재 연결된 `stampbook_content` 행 수다. |
| `data.stampbooks[].rewardCouponPolicyId` | String | 현재 연결된 완료 보상 쿠폰 정책 식별자다. |
| `data.stampbooks[].publishedAt` | String 또는 null | `PUBLISHED`, `ENDED`이면 공개 승인 시각이고, `DRAFT`, `PENDING_REVIEW`이면 `null`이다. UTC ISO 8601 형식이다. |
| `data.stampbooks[].endedAt` | String 또는 null | `ENDED`이면 종료 시각이고, 그 외 상태이면 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 목록을 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성·승인된 `OPERATOR`가 아니거나 담당 지역 배정이 없다. 목록을 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 반환 대상의 스탬프북·대상 콘텐츠·완료 보상 쿠폰 정책 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 서버는 인증 주체가 활성·승인된 `OPERATOR`이고 담당 `region_id`를 보유하는지 검증한다.
2. 목록에는 스탬프북의 `region_id`가 인증 운영자의 담당 지역이고, 현재 연결된 모든 `stampbook_content`의 콘텐츠 소유자가 인증 운영자인 항목만 포함한다. 다른 운영자·다른 지역·일부 대상 콘텐츠만 소유한 스탬프북은 존재 여부를 반환하지 않는다.
3. 목록은 `stampbookId` 내림차순으로 고정 정렬한다. 상태·제목·지역·기간 필터와 사용자 지정 정렬을 제공하지 않는다.
4. 모든 수명주기 상태를 같은 계약으로 반환한다. `DRAFT`, `PENDING_REVIEW`는 `publishedAt = null`, `endedAt = null`이고, `PUBLISHED`는 `publishedAt`만, `ENDED`는 `publishedAt`과 `endedAt`을 모두 반환한다.
5. 반환 대상의 대상 콘텐츠가 없거나 스탬프북 지역과 다른 콘텐츠가 연결됐거나, 완료 보상 쿠폰 정책이 없거나 스탬프북 지역과 다르면 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
6. 조회 시 스탬프북, 대상 콘텐츠 연결, 쿠폰 정책과 감사 이력을 생성·수정·삭제하지 않는다. 성공·실패는 `requestId`, 인증 운영자 식별자와 공개 오류 코드만 구조화 로그로 남기며 제목과 종료 사유는 로그에 남기지 않는다.
