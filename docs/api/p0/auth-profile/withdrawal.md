# 인증·프로필 회원탈퇴 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한), [PRV-01](../../../p0/auth-profile.md#prv-01), [PRV-02](../../../p0/auth-profile.md#prv-02) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0111](../../../adr/0111-use-stateless-refresh-token.md), [ADR-0012](../../../adr/0012-retain-author-unlinked-reviews-and-visits-after-withdrawal.md), [ADR-0017](../../../adr/0017-serialize-withdrawal-with-conditional-user-state.md), [ADR-0027](../../../adr/0027-deliver-refresh-token-in-http-only-cookie.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 활성 회원이 자신의 계정과 직접 식별 정보를 파기하는 HTTP API 계약을 정의한다. P0 셀프 탈퇴는
부여된 `OPERATOR`·`REGION_ADMIN` 역할과 콘텐츠 소유 관계가 없는 회원에게만 허용한다. `PENDING` 운영자
신청은 운영자 역할이 아니므로 탈퇴 과정에서 취소하고 사업자 개인정보를 제거한다.

Stateless Refresh Token은 탈퇴 시 서버에서 폐기할 상태가 없다. Access Token은 개별 폐기하지 않지만, 회원 행이
파기된 뒤 보호 업무 API와 Access Token 재발급은 인증 주체를 찾을 수 없어 요청을 거부한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01, PRV-01, PRV-02 | `DELETE /api/v1/auth/delete` | `app_user`, 역할·소유 관계, `operator_application`, 예약·방문·후기 연결 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`을 사용하며 요청 본문은 없다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 유효한 Access Token이 필요하며, 인증 주체 본인만 탈퇴한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 회원탈퇴

서버는 인증 주체의 회원 행을 `ACTIVE → WITHDRAWING`으로 조건부 전환해 단 하나의 요청만 탈퇴 처리권을 얻도록 한다.
처리권을 얻은 요청은 하나의 MySQL 트랜잭션에서 `ACTIVE` 홀드를 `INVALIDATED`로 전환하고, 최초 성공 전이에서만
차감한 정원을 한 번 복구한다. 이어서 미체크인 `CONFIRMED` 예약을 `CANCELLED`로 전환하고 QR을 무효화하며,
`PENDING` 운영자 신청은 `CANCELLED`로 종결한다. 모든 `operator_application` 상태에서 신청자 연결과 사업자 정보를
제거하고, 과거 심사한 승인·반려 신청에서는 `inspected_user_id`만 제거한다. 심사자 연결 제거는 심사 상태·심사 시각·반려
사유를 변경하지 않으며, 대체용 가짜 사용자 행이나 `탈퇴한 사용자` FK를 만들지 않는다. 요청 지역·비개인 상태·처리 사유·시각만
유지한다. 회원·프로필·자격 증명·직접 식별 정보를 파기하고, 방문 기록의 회원 연결을 제거한다. 기존 공개 후기는 작성자 표시를
공통 `탈퇴한 사용자`로 바꾸되 콘텐츠·회차·평점·본문·상태는 보존한다.
법정 보존 대상 거래·운영 데이터는 회원 정보와 분리된 상태로 유지한다.

MySQL 탈퇴 종결에 실패하면 `WITHDRAWING` 전환을 포함한 변경을 모두 롤백한다. 계정은 `ACTIVE`로 남고 기존
Refresh Token은 원래 만료 시각까지 사용할 수 있으므로 사용자는 같은 인증 상태에서 탈퇴를 재시도할 수 있다.

### Request

```http
DELETE /api/v1/auth/delete
```

#### Request Example

```http
DELETE /api/v1/auth/delete HTTP/1.1
Authorization: Bearer {accessToken}
Cookie: refreshToken={refreshToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 탈퇴할 본인 회원을 식별하는 유효한 Access Token이다. |
| `Cookie` | N | `refreshToken=<refreshToken>`. 탈퇴 권한 판정에는 사용하지 않으며, 응답에서 같은 이름·경로의 쿠키를 만료시킨다. |
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

#### Response Headers

| Name | Required | Description |
| --- | --- | --- |
| `Set-Cookie` | Y | `refreshToken=; Max-Age=0; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`. `Domain`은 생략해 호스트 전용 쿠키로 유지한다. |

성공 응답에는 Access Token, Refresh Token 또는 탈퇴 전 회원 식별 정보를 포함하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "회원탈퇴에 성공했습니다.",
  "data": null
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `회원탈퇴에 성공했습니다.`다. |
| `data` | null | 추가 응답 데이터는 없다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않거나, 인증 주체가 더 이상 활성 회원이 아니다. 탈퇴 처리와 쿠키 변경을 하지 않으며 다시 로그인해야 한다. |
| 403 | `FORBIDDEN` | 인증 주체에게 운영자·지역 관리자 역할 또는 콘텐츠 소유 관계가 남아 셀프 탈퇴를 허용할 수 없다. 상태를 변경하지 않으며 P0 범위 밖의 별도 오프보딩이 필요하다. |
| 500 | `INTERNAL_SERVER_ERROR` | MySQL 탈퇴 종결에 실패했다. `WITHDRAWING` 전환을 포함한 변경은 롤백되고 쿠키는 변경하지 않으며, 같은 인증 상태에서 다시 시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

오류 응답에는 Access Token, Refresh Token, 탈퇴 전 개인정보 또는 내부 예외 정보를
포함하지 않는다.
