# 인증·프로필 회원가입 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [ADR-0114](../../../adr/0114-support-cross-origin-browser-authentication-with-configured-allowlist.md), [ADR-0044](../../../adr/0044-use-delegating-bcrypt-password-encoder.md), [ADR-0055](../../../adr/0055-defer-business-information-encryption-until-after-operator-request.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 회원의 계정과 희망 역할을 생성하는 HTTP API 계약을 정의한다. 가입 요청은 이메일 기반 로그인 식별자,
비밀번호, 프로필 정보와 `requestedRole`을 받는다.

`VISITOR`를 선택하면 서버가 즉시 방문자 역할을 부여한다. `OPERATOR`를 선택하면 요청 지역과 사업자 정보를
포함한 `PENDING` 운영자 신청만 생성하며, 지역 관리자 승인 전에는 운영자 역할·담당 지역·콘텐츠 소유 관계를
부여하지 않는다. 가입 성공은 인증 세션을 만들지 않으며 Access Token과 Refresh Token은 별도 로그인 API에서 발급한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/signup` | `app_user`, `user_role_assignment`, `operator_application` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 `application/json; charset=UTF-8`을 사용한다. 응답에 시각 필드를 반환하지 않는다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API다. `Authorization` 헤더, 역할·지역·소유권 검증을 요구하지 않는다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `201 Created`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 회원가입

이 API는 이메일을 로그인 식별자로 하는 새 활성 회원을 생성한다. `VISITOR` 선택 시에는 방문자 역할을 함께
부여하고, `OPERATOR` 선택 시에는 `PENDING` 운영자 신청을 함께 생성한다. 정규화한 이메일이 이미 존재하거나
운영자 신청에 필요한 지역·사업자 정보가 유효하지 않으면 계정, 역할, 신청을 생성하지 않는다.

### Request

```http
POST /api/v1/auth/signup
```

#### Request Example

```http
POST /api/v1/auth/signup HTTP/1.1
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "email": "operator@example.com",
  "password": "LocalStamp!2026",
  "name": "홍길동",
  "phone": "01012345678",
  "requestedRole": "OPERATOR",
  "requestedRegionId": "1",
  "businessInformation": "상호명: 지역행사 주식회사, 사업자등록번호: 123-45-67890"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 공개 API이므로 전송하지 않는다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "email": "operator@example.com",
  "password": "LocalStamp!2026",
  "name": "홍길동",
  "phone": "01012345678",
  "requestedRole": "OPERATOR",
  "requestedRegionId": "1",
  "businessInformation": "상호명: 지역행사 주식회사, 사업자등록번호: 123-45-67890"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `email` | String | Y | 이메일 형식, 최대 254자, `null`·빈 문자열 불가. 앞뒤 공백을 제거하고 소문자로 정규화해 로그인 식별자 유일성을 검사·저장한다. |
| `password` | String | Y | 8~64자이면서 UTF-8 인코딩 기준 72바이트 이하다. 영문자·숫자·특수문자를 각각 하나 이상 포함해야 하며 `null`·빈 문자열·공백만으로 된 값은 허용하지 않는다. 서버는 해시만 보관하고 원문을 응답·로그에 남기지 않는다. |
| `name` | String | Y | 앞뒤 공백을 제거한 뒤 1~50자여야 한다. `null`·빈 문자열·공백만으로 된 값은 허용하지 않는다. |
| `phone` | String | Y | 숫자 10~11자리다. 하이픈은 입력 시 제거하고 숫자만 저장한다. `null`·빈 문자열은 허용하지 않는다. |
| `requestedRole` | String | Y | `VISITOR` 또는 `OPERATOR`만 허용한다. `REGION_ADMIN`은 가입 요청에서 선택할 수 없다. |
| `requestedRegionId` | String | 조건부 | `requestedRole`이 `OPERATOR`이면 양의 정수이며 공개 지역 식별자여야 한다. `VISITOR`이면 생략해야 한다. |
| `businessInformation` | String | 조건부 | `requestedRole`이 `OPERATOR`이면 앞뒤 공백을 제거한 1~2,000자여야 한다. 수동 사업자 검증에 사용하며 `VISITOR`이면 생략해야 한다. 재신청 구현 단계에서는 기존 평문 텍스트로 저장하고, 회원가입 응답·로그에는 포함하지 않는다. 암호화 전환은 [#266](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/266)에서 처리한다. |

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
  "message": "회원가입에 성공했습니다.",
  "data": {
    "userId": "1",
    "requestedRole": "OPERATOR",
    "assignedRole": null,
    "operatorApplicationStatus": "PENDING"
  }
}
```

`VISITOR` 선택 성공 시 `data.assignedRole`은 `VISITOR`이고 `data.operatorApplicationStatus`는 `null`이다.
`OPERATOR` 선택 성공 시 `data.assignedRole`은 `null`이고 `data.operatorApplicationStatus`는 `PENDING`이다.
응답에는 이메일, 전화번호, 비밀번호 해시, 사업자 정보, Access Token 또는 Refresh Token을 포함하지 않는다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `회원가입에 성공했습니다.`다. |
| `data.userId` | String | 새로 생성된 회원 식별자다. 양의 정수다. |
| `data.requestedRole` | String | 클라이언트가 선택한 `VISITOR` 또는 `OPERATOR`다. |
| `data.assignedRole` | String 또는 null | `VISITOR` 선택 시 즉시 부여된 `VISITOR`다. `OPERATOR` 선택 시 승인 전이므로 `null`이다. |
| `data.operatorApplicationStatus` | String 또는 null | `OPERATOR` 선택 시 생성된 운영자 신청 상태 `PENDING`이다. `VISITOR` 선택 시 `null`이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수 요청 값 누락, `email`, `password`, `name`, `phone`, `requestedRole`의 형식·범위 위반 또는 역할별 조건부 필드 규칙 위반이다. 회원·역할·신청은 생성되지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 회원·역할은 생성되지 않으며 본문을 수정한 뒤 재시도할 수 있다. |
| 404 | `NOT_FOUND` | `OPERATOR` 신청의 요청 지역이 없거나 공개되지 않았다. 회원·역할·신청은 생성되지 않으며 공개 지역을 선택한 뒤 재시도할 수 있다. |
| 409 | `DUPLICATE_LOGIN_IDENTIFIER` | 정규화한 이메일과 같은 로그인 식별자가 이미 존재한다. 회원·역할은 생성되지 않으며 다른 이메일로 재시도하거나 로그인 흐름을 사용한다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "DUPLICATE_LOGIN_IDENTIFIER",
  "message": "이미 사용 중인 이메일입니다.",
  "data": null
}
```

오류 응답에는 요청의 비밀번호 원문, 비밀번호 해시 또는 내부 예외 정보를 포함하지 않는다.
