# 지역 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02`, `ADM-05` |
| 소유 도메인 | 지역 |
| 기준 문서 | [지역 API](region.md), [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [P0 ERD](../../../erd.md), [P1 ERD](../../../p1-erd.md), [ADR-0077](../../../adr/0077-normalize-region-code-to-uppercase.md), [ADR-0085](../../../adr/0085-keep-region-evidence-reference-as-free-string.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

전체관리자가 새 지역을 생성한다. 생성 직후 지역은 공개 사용자 탐색에 노출되지 않도록 `isPublic = false`로
저장한다. 지역 생성과 성공 감사 이벤트는 하나의 트랜잭션으로 커밋하며, 지역 또는 감사 이벤트 저장 중 하나라도
실패하면 모두 롤백한다.

처리자는 `PRIVILEGED` 계정의 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다.

## 2. 공통 계약 참조

생성·인증·응답·오류의 공통 규칙은 [지역 API 명세서](region.md#2-공통-계약-참조)를 따른다.

## 3. 지역 생성

새 지역의 시스템 코드와 표시 이름을 생성한다. `ADM-05`가 요구하는 특권 변경 근거는 비개인 `reasonCode`와
필수 `evidenceReference`로 입력받아 감사 이력에 저장하며 공개 응답에는 포함하지 않는다.

### Request

```http
POST /platform-admin/regions
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/platform-admin/regions
```

#### Request Example

```http
POST /api/v1/platform-admin/regions HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "regionCode": "JEONJU",
  "name": "전주시",
  "reasonCode": "PILOT_REGION_ADDITION",
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

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "regionCode": "JEONJU",
  "name": "전주시",
  "reasonCode": "PILOT_REGION_ADDITION",
  "evidenceReference": "OPS-2026-0805-REGION-03"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `regionCode` | String | Y | 시스템 지역 코드. 앞뒤 공백 제거 후 1자 이상 50자 이하이며 `^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$`를 만족해야 한다. 제거 후 남은 내부 공백은 허용하지 않는다. 검증 후 `Locale.ROOT` 기준 대문자로 변환해 저장한다. |
| `name` | String | Y | 사용자에게 표시할 지역명. 앞뒤 공백 제거 후 1자 이상 100자 이하다. |
| `reasonCode` | String | Y | 비개인 지역 생성 사유 코드. 앞뒤 공백 제거 후 아래 허용 코드 중 하나여야 하며 `audit_event.reason_code`에 저장한다. |
| `evidenceReference` | String | Y | 내부 증빙 참조. 앞뒤 공백 제거 후 1자 이상 500자 이하여야 하며 `audit_event.evidence_reference`에 저장한다. 서버는 문자 형식·prefix·증빙 시스템·URL·이메일·비밀값·개인정보 포함 여부를 검사하지 않으므로 호출자는 개인정보·토큰·비밀값을 포함하지 않아야 한다. |

#### 허용 사유 코드

| `reasonCode` | 의미 |
| --- | --- |
| `PILOT_REGION_ADDITION` | P1 파일럿 운영 지역 추가 |
| `SERVICE_AREA_EXPANSION` | 서비스 제공 지역 확대 |
| `ADMINISTRATIVE_REORGANIZATION` | 행정구역 개편 반영 |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "지역 생성에 성공했습니다.",
  "data": {
    "regionId": "3",
    "regionCode": "JEONJU",
    "name": "전주시",
    "isPublic": false,
    "createdAt": "2026-08-05T04:30:00Z",
    "updatedAt": "2026-08-05T04:30:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 지역 생성 성공 메시지 |
| `data.regionId` | String | 생성된 지역 식별자 |
| `data.regionCode` | String | 생성된 지역의 대문자 정규형 시스템 코드 |
| `data.name` | String | 생성된 지역의 표시 이름 |
| `data.isPublic` | Boolean | 생성 직후 공개 여부. 항상 `false` |
| `data.createdAt` | String | 지역 생성 시각. UTC ISO 8601 일시 |
| `data.updatedAt` | String | 지역 최종 수정 시각. 생성 응답에서는 `createdAt`과 같다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_JSON` | 요청 본문이 없거나 JSON 문법이 잘못되어 역직렬화할 수 없다. 지역과 감사 이력은 생성되지 않으며 JSON 본문을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `regionCode`, `name`, `reasonCode`, `evidenceReference`가 JSON 문자열 타입이 아니다. 지역과 감사 이력은 생성되지 않으며 필드 타입을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | 필수값이 누락됐거나 `regionCode`·`name`이 형식·길이를, `reasonCode`가 허용 목록을, `evidenceReference`가 공백 제거 후 1~500자 길이를 만족하지 않는다. 지역과 감사 이력은 생성되지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 지역과 감사 이력은 생성되지 않으며 유효한 Access Token을 얻은 뒤 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 `PRIVILEGED` 계정의 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 갖지 않는다. 지역과 감사 이력은 생성되지 않으며 활성 고권한 배정을 얻기 전에는 재시도해도 성공하지 않는다. |
| `409` | `REGION_CODE_ALREADY_EXISTS` | 정규화한 `regionCode`가 `region(region_code)` 유일 제약과 충돌한다. 지역과 성공 감사 이력은 생성되지 않으며 같은 코드의 반복 요청은 성공하지 않는다. 기존 지역을 확인하거나 다른 코드를 사용해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 지역 생성 중 예상하지 못한 서버 오류가 발생해 트랜잭션이 롤백됐다. 일시 장애가 해소된 뒤 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REGION_CODE_ALREADY_EXISTS",
  "message": "이미 사용 중인 지역 코드입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `app_user.account_kind = PRIVILEGED`이고 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다.
2. `regionCode`는 앞뒤 공백을 제거하고 ASCII 형식과 길이를 검증한 뒤 `Locale.ROOT` 기준 대문자로 변환한다. 변환된 정규형을 저장·응답·유일성 검사의 기준으로 사용한다.
3. `name`, `reasonCode`, `evidenceReference`는 앞뒤 공백을 제거한 값으로 검증한다. `reasonCode`가 지역 생성 허용 목록에 없거나 `evidenceReference`가 1~500자가 아니면 `400 INVALID_INPUT`으로 거부한다. 서버는 `evidenceReference`의 문자 형식·출처·내용을 검사하지 않는다. `name`은 `region.name`, 사유와 증빙 참조는 `audit_event`에 저장한다.
4. 생성 직후 `isPublic`은 `false`로 고정한다. 공개 또는 운영 상태 변경은 이 API에서 처리하지 않는다.
5. 지역 생성과 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 처리한다.
6. 성공 감사 이벤트는 `region_id = 생성된 region_id`, `target_type = REGION`, `target_id = 생성된 region_id`, `previous_state = null`, `next_state = CREATED`, `result = SUCCESS`, 요청 `reason_code`·`evidence_reference`, `actor_kind = USER`, 실제 고권한 등급인 `actor_role`, 처리 시각과 `requestId`를 포함한다.
7. 호출자는 `evidenceReference`에 개인정보·토큰·비밀값을 포함하지 않아야 한다. 서버는 이를 자동 판별하지 않고 길이 검증을 통과한 값을 감사 이벤트에 저장한다. API 응답과 구조화 로그에는 `evidenceReference`를 포함하지 않는다. 활성 처리자는 `audit_event_actor_link`에 연결한다.
8. 정규화된 값의 `region(region_code)` 유일 제약 충돌은 `409 REGION_CODE_ALREADY_EXISTS`로 변환한다.
9. 대소문자가 달라도 같은 정규형으로 수렴하는 동시 생성 요청은 하나만 성공하고 나머지는 `409 REGION_CODE_ALREADY_EXISTS`를 반환한다.
10. 입력·인증·인가 단계에서 끝난 실패 요청은 실패 감사 이벤트를 만들지 않고 `requestId`, 결과 코드와 필요한 비개인 식별자만 구조화 로그로 남긴다.
