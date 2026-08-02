# 인증·프로필 운영자 권한 신청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-01, AUTH-02 |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0036](../../../adr/0036-expose-business-information-only-in-protected-review-detail.md), [ADR-0055](../../../adr/0055-defer-business-information-encryption-until-after-operator-request.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이전에 `REJECTED`된 운영자 신청이 있는 활성 회원이 운영자 역할을 다시 신청한다. 서버는 요청 지역과 사업자 정보를
가진 `PENDING` 신청을 새로 만들며, 지역 관리자가 승인하기 전까지 `OPERATOR` 역할과 담당 지역을 부여하지 않는다.

최초 운영자 신청은 회원가입에서 `requestedRole`을 `OPERATOR`로 선택해 생성한다. 반려된 신청만 새 행으로 다시
신청할 수 있다. `PENDING` 신청이 이미 있거나 이미 `OPERATOR` 역할이 부여된 회원은 새 신청을 만들 수 없다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01, AUTH-02 | `POST /api/v1/operator/operator-requests` | `app_user`, `user_role_assignment`, `operator_application`, `region` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고 요청·응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 유효한 Access Token과 활성 회원 상태가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 생성된 신청의 비민감 메타데이터를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 운영자 권한 신청

이전에 `REJECTED`된 신청이 있는 활성 회원이 공개 지역을 선택하고 사업자 정보를 제출해 운영자 권한 심사를 다시
요청한다. 최초 신청은 회원가입에서 처리한다. 재신청 구현 단계에서는 기존 평문 저장을 사용하되 이 신청 응답과 로그에 포함하지 않으며,
담당 지역 관리자 전용 심사용 상세 조회에서만 원문을 제공한다. 암호화 전환은 [#266](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/266)에서 처리한다.

### Request

```http
POST /api/v1/operator/operator-requests
```

#### Request Example

```http
POST /api/v1/operator/operator-requests HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "requestedRegionId": 1,
  "businessInformation": "상호명 지역행사 주식회사, 사업자등록번호 123-45-67890"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "requestedRegionId": 1,
  "businessInformation": "상호명 지역행사 주식회사, 사업자등록번호 123-45-67890"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `requestedRegionId` | Long | Y | 양의 정수이며 공개 지역 식별자여야 한다. |
| `businessInformation` | String | Y | 앞뒤 공백을 제거한 1~2,000자 텍스트다. `null`, 빈 문자열, 공백만으로 된 값은 허용하지 않으며 재신청 구현 단계에서는 기존 평문 텍스트로 저장한다. 이 신청 응답·로그에는 포함하지 않고, 담당 지역 관리자 전용 심사용 상세 조회에서만 원문을 제공한다. 암호화 전환은 [#266](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/266)에서 처리한다. |

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
  "message": "운영자 권한 신청에 성공했습니다.",
  "data": {
    "operatorApplicationId": 21,
    "requestedRegionId": 1,
    "status": "PENDING"
  }
}
```

성공 시에만 신청 행을 생성한다. 이 응답은 `businessInformation`, 신청자의 연락처, 심사자 정보 또는
`OPERATOR` 역할을 포함하지 않는다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `운영자 권한 신청에 성공했습니다.` |
| `data.operatorApplicationId` | Long | 새로 생성한 운영자 신청 식별자. 양의 정수이다. |
| `data.requestedRegionId` | Long | 신청한 공개 지역 식별자 |
| `data.status` | String | 생성 직후 상태인 `PENDING` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수값 누락, 요청 지역 식별자 범위 위반 또는 사업자 정보 형식·길이 위반이다. 신청은 생성되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 신청은 생성되지 않으며 본문을 수정해 다시 요청할 수 있다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 만료·변조되었다. 신청은 생성되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| 403 | `FORBIDDEN` | 활성 회원이 아니거나 이미 `OPERATOR` 또는 `REGION_ADMIN` 역할이 부여된 회원이다. 신청은 생성되지 않는다. |
| 404 | `NOT_FOUND` | 요청 지역이 없거나 공개 지역이 아니다. 신청은 생성되지 않으며 공개 지역을 선택해 다시 요청할 수 있다. |
| 409 | `OPERATOR_APPLICATION_PENDING` | 해당 회원의 `PENDING` 운영자 신청이 이미 있다. 신청은 생성되지 않으며 기존 신청이 `REJECTED`로 종결된 뒤 새로 신청할 수 있다. |
| 409 | `OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED` | 이전 `REJECTED` 운영자 신청이 없다. 최초 신청은 회원가입에서 `requestedRole`을 `OPERATOR`로 선택해야 한다. 신청은 생성되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "OPERATOR_APPLICATION_PENDING",
  "message": "처리 중인 운영자 권한 신청이 있습니다.",
  "data": null
}
```
