# 스탬프북 심사 대기 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 자신의 담당 지역에서 `PENDING_REVIEW` 상태인 스탬프북 심사 대기 목록을 조회한다. 이 API는
승인·반려를 수행하지 않으며 스탬프북, 대상 콘텐츠, 완료 보상 정책과 감사 이력을 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `GET /api/v1/region-admin/stampbooks?status=PENDING_REVIEW` | `stampbook`, `stampbook_content`, `audit_event` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 심사 대기 목록 전체를
반환하므로 페이지네이션, 검색과 사용자 지정 정렬을 제공하지 않는다.

## 3. 스탬프북 심사 대기 목록 조회

### Request

```http
GET /api/v1/region-admin/stampbooks?status=PENDING_REVIEW
```

#### Request Example

```http
GET /api/v1/region-admin/stampbooks?status=PENDING_REVIEW HTTP/1.1
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

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | 이 API에서는 `PENDING_REVIEW`만 허용한다. 누락·빈 값·다른 상태는 허용하지 않는다. |

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
  "message": "스탬프북 심사 대기 목록 조회에 성공했습니다.",
  "data": {
    "stampbooks": [
      {
        "stampbookId": "101",
        "regionId": "1",
        "status": "PENDING_REVIEW",
        "targetCount": 2,
        "rewardCouponPolicyId": "301",
        "requestedAt": "2026-08-14T02:20:00Z"
      }
    ]
  }
}
```

심사 대기 스탬프북이 없으면 `200 OK`와 `data.stampbooks: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbooks` | Array | 인증 지역 관리자의 담당 지역에 속한 심사 대기 스탬프북 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.stampbooks[].stampbookId` | String | 양의 10진 문자열인 스탬프북 식별자다. |
| `data.stampbooks[].regionId` | String | 인증 지역 관리자의 담당 지역과 같은 스탬프북 지역 식별자다. |
| `data.stampbooks[].status` | String | 항상 `PENDING_REVIEW`다. |
| `data.stampbooks[].targetCount` | Integer | 연결된 `stampbook_content` 행 수이자 완료 목표 스탬프 수다. 1 이상이다. |
| `data.stampbooks[].rewardCouponPolicyId` | String | 완료 보상 쿠폰 정책 식별자다. |
| `data.stampbooks[].requestedAt` | String | 가장 최근 성공한 `DRAFT → PENDING_REVIEW` 심사 요청 감사 이벤트의 발생 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 누락·공백이거나 `PENDING_REVIEW`가 아니다. 스탬프북과 감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 목록을 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성·승인된 담당 지역의 `REGION_ADMIN`이 아니다. 목록을 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 스탬프북·대상 콘텐츠·심사 요청 감사 이력의 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 상태와 감사 이력을 변경하지 않는다. |

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

1. 서버는 인증 주체가 활성·승인된 `REGION_ADMIN`이고 담당 `region_id`를 보유하는지 확인한다. 클라이언트는 지역 식별자를 전달하거나 담당 지역을 바꿀 수 없다.
2. `stampbook.region_id = 인증 지역 관리자의 담당 region_id` 및 `stampbook.status = PENDING_REVIEW`인 행만 반환한다. 다른 지역 스탬프북의 존재 여부와 개수는 노출하지 않는다.
3. `status`가 누락되거나 `PENDING_REVIEW` 이외의 값이면 빈 목록으로 대체하지 않고 `400 INVALID_INPUT`으로 거부한다.
4. `requestedAt`은 해당 스탬프북의 가장 최근 성공 `STAMPBOOK` 감사 이벤트 중 이전 상태가 `DRAFT`, 이후 상태가 `PENDING_REVIEW`인 이벤트의 `occurred_at`이다. 이 이벤트가 없거나 상태·대상과 일치하지 않으면 정상 항목으로 대체하지 않고 정합성 오류로 처리한다.
5. 목록은 `requestedAt` 오름차순, 같은 시각이면 `stampbookId` 오름차순으로 정렬해 오래 대기한 요청을 먼저 반환한다.
6. 이 API는 단순 목록이다. 페이지네이션, 추가 상태 필터, 검색과 사용자 지정 정렬을 제공하지 않는다.
7. 조회 시 스탬프북, 대상 콘텐츠, 완료 보상 정책과 감사 이력을 생성·수정·삭제하지 않는다. 성공·실패는 `requestId`, 담당 지역 식별자, 결과 건수와 공개 오류 코드만 구조화 로그로 남기며, 심사 요청 사유 원문은 로그에 남기지 않는다.
