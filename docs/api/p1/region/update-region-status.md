# 지역 공개 여부 변경 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02`, `ADM-05` |
| 소유 도메인 | 지역 |
| 기준 문서 | [지역 API](region.md), [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [P0 ERD](../../../erd.md), [P1 ERD](../../../p1-erd.md), [ADR-0065](../../../adr/0065-use-is-public-for-region-availability-and-history-roles.md), [ADR-0072](../../../adr/0072-use-allowlisted-reason-codes-for-region-changes.md), [ADR-0074](../../../adr/0074-treat-repeated-region-visibility-as-no-op-success.md), [ADR-0075](../../../adr/0075-limit-region-success-audits-to-state-transitions.md), [ADR-0077](../../../adr/0077-limit-region-hiding-to-pre-operation.md), [ADR-0078](../../../adr/0078-store-evidence-reference-in-region-visibility-failure-audits.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

전체관리자가 지역의 공개 여부를 변경한다. 실제 변경은 지역 행과 성공 감사 이벤트를 같은 트랜잭션으로 커밋한다.

P1은 별도 운영 상태를 만들지 않는다. `isPublic = false`는 비공개·준비, `true`는 공개·운영을 뜻한다.
`true → false` 전환은 `content.deleted_at IS NULL`인 콘텐츠가 하나도 없을 때만 허용한다.
이 전환은 콘텐츠 운영 전 공개 노출 취소에만 사용하며, 콘텐츠 운영 이력이 있는 지역의 운영 종료·비공개는
P1에서 제공하지 않는다.
현재 값과 같은 `isPublic` 요청은 `200 OK`로 현재 지역 정보를 반환하며 지역과 감사 이력을 변경하지 않는다.

## 2. 공통 계약 참조

변경·인증·응답·오류의 공통 규칙은 [지역 API 명세서](region.md#2-공통-계약-참조)를 따른다.

## 3. 지역 공개 여부 변경

### Request

```http
PATCH /platform-admin/regions/{regionId}/status
```

실제 요청 경로는 다음과 같다.

```http
PATCH /api/v1/platform-admin/regions/{regionId}/status
```

#### Request Example

```http
PATCH /api/v1/platform-admin/regions/3/status HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "isPublic": false,
  "reasonCode": "REGION_PREPARATION",
  "evidenceReference": "OPS-2026-0805-REGION-03"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `regionId` | String | Y | 지역 식별자. 양의 10진 정수 문자열이다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "isPublic": false,
  "reasonCode": "REGION_PREPARATION",
  "evidenceReference": "OPS-2026-0805-REGION-03"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `isPublic` | Boolean | Y | 변경할 공개 여부. `false`는 비공개·준비, `true`는 공개·운영을 뜻한다. |
| `reasonCode` | String | Y | 비개인 공개 여부 변경 사유 코드. 앞뒤 공백 제거 후 목표 `isPublic`에 맞는 아래 허용 코드 중 하나여야 한다. 실제 상태 전이에 성공하면 `audit_event.reason_code`에 저장한다. 상태 조건 실패에는 요청값 대신 서버 실패 코드를 저장하고 동일 상태 무변경 성공에서는 저장하지 않는다. |
| `evidenceReference` | String | Y | 비밀값과 개인정보를 포함하지 않는 내부 증빙 참조. 앞뒤 공백 제거 후 1자 이상 500자 이하여야 한다. 실제 상태 전이 성공과 비삭제 콘텐츠 조건 실패 감사의 `audit_event.evidence_reference`에 저장하고 동일 상태 무변경 성공에서는 저장하지 않는다. |

#### 허용 사유 코드

| 목표 공개 여부 | `reasonCode` | 의미 |
| --- | --- | --- |
| `isPublic = true` | `REGION_LAUNCH` | 지역 운영 개시 |
| `isPublic = true` | `REGION_REOPEN` | 비공개 지역 재공개 |
| `isPublic = false` | `REGION_PREPARATION` | 공개 전 준비 상태 전환 |
| `isPublic = false` | `ADMINISTRATIVE_REORGANIZATION` | 행정구역 개편 반영 |

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
  "message": "지역 공개 여부 요청을 처리했습니다.",
  "data": {
    "regionId": "3",
    "regionCode": "JEONJU",
    "name": "전주시",
    "isPublic": false,
    "updatedAt": "2026-08-05T05:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 지역 공개 여부 요청 처리 성공 메시지. 실제 변경과 동일 상태 무변경 성공에 공통으로 사용한다. |
| `data.regionId` | String | 지역 식별자 |
| `data.regionCode` | String | 시스템에서 사용하는 대문자 정규형 지역 코드 |
| `data.name` | String | 사용자에게 표시할 지역명 |
| `data.isPublic` | Boolean | 공개 여부 |
| `data.updatedAt` | String | 지역 최종 수정 시각. UTC ISO 8601 일시. 동일 상태 무변경 성공에서는 기존 값을 유지한다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_JSON` | 요청 본문이 없거나 JSON 문법이 잘못되어 역직렬화할 수 없다. 지역과 감사 이력은 변경되지 않으며 JSON 본문을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `regionId`를 양의 10진 정수 문자열로 처리할 수 없거나 `isPublic`, `reasonCode`, `evidenceReference`가 선언된 JSON 타입이 아니다. 지역과 감사 이력은 변경되지 않으며 타입을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | 필수값이 누락됐거나 `reasonCode`, `evidenceReference`가 공백 제거 후 형식·길이·허용 목록을 만족하지 않는다. 지역과 감사 이력은 변경되지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 지역과 감사 이력은 변경되지 않으며 유효한 Access Token을 얻은 뒤 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 `PRIVILEGED` 계정의 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 갖지 않는다. 지역과 감사 이력은 변경되지 않으며 활성 고권한 배정을 얻기 전에는 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 지역이 없다. 감사 이력은 생성하지 않으며 올바른 `regionId`로 수정하기 전에는 재시도해도 성공하지 않는다. |
| `409` | `REGION_AVAILABILITY_CONFLICT` | 비삭제 콘텐츠가 있는 공개 지역을 비공개로 바꾸려 한다. 지역과 성공 감사 이력은 변경하지 않고 롤백 뒤 서버 실패 코드와 요청 증빙 참조를 가진 실패 감사 이벤트만 별도 트랜잭션으로 기록한다. 공개 이후 콘텐츠는 삭제할 수 없으므로 콘텐츠 운영 이력이 있는 지역에서는 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 변경 중 예상하지 못한 서버 오류가 발생해 변경 트랜잭션이 롤백됐다. 일시 장애가 해소된 뒤 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REGION_AVAILABILITY_CONFLICT",
  "message": "지역 공개 여부를 변경할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `app_user.account_kind = PRIVILEGED`이고 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다.
2. `reasonCode`와 `evidenceReference`는 앞뒤 공백을 제거한 값으로 검증한다. `reasonCode`가 목표 `isPublic`의 허용 목록에 없으면 `400 INVALID_INPUT`으로 거부한다. 인증·인가와 입력 검증은 동일 상태 판단보다 먼저 수행한다.
3. 같은 트랜잭션에서 대상 지역 행을 쓰기 잠금으로 조회한다. 대상이 없으면 `404 NOT_FOUND`를 반환하고 감사 이벤트를 만들지 않는다.
4. 현재 `is_public`과 요청 `isPublic`이 같으면 `200 OK`와 현재 지역 정보를 반환한다. 지역 행과 `updatedAt`을 변경하지 않고 성공·실패 감사 이벤트도 만들지 않는다.
5. `false → true`는 지역을 공개·운영 상태로 전환한다.
6. `true → false`는 `content.region_id = regionId`이고 `content.deleted_at IS NULL`인 콘텐츠가 하나도 없는 지역의 운영 전 공개 노출 취소에만 허용한다. 공개 이후 콘텐츠 이력이 있는 지역의 운영 종료·비공개는 P1에서 제공하지 않는다.
7. 6번 조건을 만족하지 않으면 `409 REGION_AVAILABILITY_CONFLICT`를 반환하고 성공 감사 이벤트를 만들지 않는다. 롤백 완료 뒤 `region_id`, `target_type = REGION`, `target_id`, 현재·목표 `is_public`, `result = FAILURE`, 서버 판정 `reason_code = REGION_AVAILABILITY_CONFLICT`, 검증된 요청 `evidence_reference`, `actor_kind = USER`, 실제 고권한 `actor_role`, 처리 시각과 `requestId`를 가진 실패 감사 이벤트를 별도 트랜잭션으로 기록하고 활성 처리자를 `audit_event_actor_link`에 연결한다. 요청의 업무 `reasonCode`는 실패 감사에 저장하지 않는다.
8. 실제 상태 전이의 성공 감사 이벤트는 `region_id = 대상 region_id`, `target_type = REGION`, `target_id = 대상 region_id`, 이전·이후 `is_public` 값, `result = SUCCESS`, 요청 `reason_code`·`evidence_reference`, `actor_kind = USER`, 실제 고권한 등급인 `actor_role`, 처리 시각과 `requestId`를 포함한다.
9. 성공·실패 감사 이벤트에는 토큰, 비밀값과 개인정보를 저장하지 않는다. 활성 처리자는 `audit_event_actor_link`에 연결한다.
10. 실제 공개 여부 변경과 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 함께 커밋하거나 롤백한다. 같은 목표 상태의 동시 요청은 지역 행 잠금으로 직렬화하여 첫 요청만 실제 변경과 성공 감사를 만들고 이후 요청은 4번의 무변경 성공으로 처리한다.
