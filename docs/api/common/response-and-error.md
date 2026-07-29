# 응답·오류

이 문서는 공통 HTTP 응답·오류 계약의 단일 출처다. 도메인 명세에는 API별 성공 상태, `data` 필드와 오류 코드만
작성하며, 이 문서에 정의되지 않은 최상위 필드, HTTP 상태, 공개 오류 코드나 메시지를 임의로 추가하지 않는다.

## 응답 적용 범위

| 응답 유형              | 계약                                |
|--------------------|-----------------------------------|
| 본문이 있는 JSON 성공 응답  | `ApiResponse<응답 DTO>`             |
| 본문이 있는 JSON 실패 응답  | `ApiResponse`의 실패 형식              |
| 본문이 필요한 데이터 없는 성공  | `ApiResponse<Void>`               |
| `204 No Content`   | 본문 없음                             |
| 파일·스트림 등 비 JSON 응답 | 해당 API가 명시적으로 정의한 경우에만 공통 래퍼에서 제외 |

- `ApiResponse<T>`의 `T`에는 API별 응답 DTO만 담는다. JPA 엔티티, 도메인 객체, Spring Data 타입과 영속성
  Projection을 직접 노출하지 않는다.
- 성공 응답은 Controller 또는 HTTP 입력 Adapter가 응답 DTO로 변환한 뒤 한 번만 조립한다.
- 실패 응답은 하나의 전역 예외 변환 경계에서 조립한다. Controller별 `try-catch`나 `@ExceptionHandler`로 같은
  오류 응답을 중복 조립하지 않는다.
- 도메인 객체, Service, Application Service, Repository와 Port는 `ApiResponse`, `ResponseEntity`,
  `HttpStatus`에 의존하거나 이를 반환하지 않는다.
- 성공 응답에 오류 상세를, 실패 응답에 성공 데이터를 함께 넣지 않는다.

## JSON 응답 구조

JSON 응답의 최상위 필드는 항상 `statusCode`, `code`, `message`, `data` 네 개이며 순서도 동일하게
직렬화한다. HTTP 상태와 `statusCode`는 항상 같은 값이어야 한다. `requestId`는 서버 로그에서만 사용하며
JSON 본문과 응답 헤더에는 넣지 않는다.

### 성공 응답

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "콘텐츠 조회에 성공했습니다.",
  "data": {}
}
```

### 오류 응답

```json
{
  "statusCode": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "data": null
}
```

| 항목          | 계약                                                            |
|-------------|---------------------------------------------------------------|
| HTTP 상태와 본문 | HTTP 상태와 `statusCode`를 같은 값으로 설정한다. 성공 여부·코드와 모순되면 안 된다.          |
| `null`      | `data`는 항상 포함한다. 데이터 없는 성공과 실패 응답에서는 `null`을 사용한다.                   |
| 컬렉션         | `null` 대신 빈 컬렉션을 반환한다.                                        |
| 데이터 없는 성공   | 본문이 필요하면 `ApiResponse<Void>`를, `204`를 정의하면 본문 없음을 사용한다.       |
| 생성 방식       | `success`, `emptySuccess`, `failure`처럼 의미가 드러나는 공통 팩터리를 사용한다. |
| 검증 오류 상세    | 현재는 필드명·원문 메시지·다건 상세를 노출하지 않고 `INVALID_INPUT`의 일반화된 메시지만 사용한다. |

## 오류 코드와 변환

| 항목         | 계약                                                                                                                                       |
|------------|------------------------------------------------------------------------------------------------------------------------------------------|
| 공개 코드      | 안정적인 기계 판독용 값으로 정의하고, 클라이언트는 `message` 대신 `code`로 분기한다.                                                                                  |
| 오류 코드 소유   | 공통·도메인 오류 코드, HTTP 상태와 공개 메시지는 모두 `global.error.ErrorCode` enum에 둔다.                                                                     |
| 오류 발생      | 도메인과 Service는 필요한 `ErrorCode`를 가진 `BusinessException`을 던지고, HTTP 변환은 `global.error` 경계에서 처리한다.                                           |
| 동일 정책 실패   | Controller, Scheduler, 이벤트 리스너 등 진입 경로와 무관하게 같은 오류 코드로 식별한다.                                                                             |
| 입력·바인딩 오류  | Bean Validation, 역직렬화, 타입 변환 등 실패 유형과 공개 오류 코드를 구분해 정의한다.                                                                                |
| 인증·인가 오류   | 인증 구현 시 Spring Security의 `AuthenticationEntryPoint`와 `AccessDeniedHandler`를 같은 오류 계약으로 직렬화한다. 기능 구현 전에는 기본 Spring Security 응답을 변경하지 않는다. |
| 예상하지 못한 오류 | 내부 원인은 서버 로그에만 남기고, 클라이언트에는 일반화한 서버 오류를 반환한다.                                                                                            |

API별 오류는 다음 표로 정의한다. 오류 조건에는 상태 변경·미변경 여부와 재시도 가능 여부를 함께 작성한다.

오류 코드는 다음과 같으며, 이후 도메인 오류도 같은 `ErrorCode` enum에 추가한다.

| HTTP Status | Code                    | Message                         | Description |
|-------------|-------------------------|---------------------------------|-------------|
| 400 | `INVALID_INPUT` | 요청 값이 올바르지 않습니다. | Bean Validation, 누락된 필수 요청 값, 범위를 벗어난 값 |
| 400 | `INVALID_JSON` | 요청 본문 형식이 올바르지 않습니다. | JSON 역직렬화 실패 |
| 400 | `INVALID_TYPE` | 요청 값의 형식이 올바르지 않습니다. | 경로·쿼리 파라미터 타입 변환 실패 |
| 401 | `INVALID_CREDENTIALS` | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 자격 증명 불일치 또는 로그인할 수 없는 계정 상태 |
| 401 | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않습니다. | 미인증, 만료·변조된 Access Token |
| 403 | `FORBIDDEN` | 접근 권한이 없습니다. | 역할·지역·소유권 검증 실패 |
| 404 | `NOT_FOUND` | 요청한 리소스를 찾을 수 없습니다. | 존재하지 않는 요청 경로 또는 공개가 허용된 대상 부재 |
| 405 | `METHOD_NOT_ALLOWED` | 허용되지 않은 HTTP 메서드입니다. | 지원하지 않는 HTTP 메서드 |
| 409 | `DUPLICATE_LOGIN_IDENTIFIER` | 이미 사용 중인 이메일입니다. | 정규화한 로그인 식별자가 이미 존재함 |
| 409 | `REFRESH_TOKEN_CONFLICT` | Refresh Token 갱신 요청이 충돌했습니다. | 같은 Refresh Token의 진행 중인 동시 갱신 요청 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 멱등 키가 다른 요청에 이미 사용되었습니다. | 같은 처리 주체·명령에서 이미 다른 요청에 사용한 멱등 키 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 동일한 요청을 처리 중입니다. | 같은 멱등 키의 최초 요청이 아직 처리 중인 상태 |
| 409 | `RESERVATION_HOLD_CONFLICT` | 예약 대기를 생성할 수 없는 상태입니다. | 콘텐츠·회차 상태, 시작 시각, 잔여 정원 또는 동시 상태 전이로 홀드를 생성할 수 없는 상태 |
| 409 | `RESERVATION_CONFIRM_CONFLICT` | 예약을 확정할 수 없는 상태입니다. | 홀드·콘텐츠·회차 상태 또는 동시 상태 전이로 예약을 확정할 수 없는 상태 |
| 409 | `RESERVATION_CANCEL_CONFLICT` | 예약을 취소할 수 없는 상태입니다. | 예약 상태, 회차 시작 시각 또는 체크인·노쇼 전이로 예약을 취소할 수 없는 상태 |
| 409 | `CHECK_IN_CONFLICT` | 체크인할 수 없는 상태입니다. | 예약·회차·체크인 창·사용자 연결 또는 동시 상태 전이로 새 체크인을 완료할 수 없는 상태 |
| 409 | `CONTENT_END_CONFLICT` | 콘텐츠를 종료할 수 없는 상태입니다. | 콘텐츠·회차 상태 또는 동시 상태 전이로 콘텐츠를 종료할 수 없는 상태 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류가 발생했습니다. | 예상하지 못한 예외 |
| 503 | `AUTH_SERVICE_UNAVAILABLE` | 인증 서비스를 일시적으로 사용할 수 없습니다. | Refresh Token 계열을 안전하게 발급·갱신·폐기할 수 없는 Redis 장애 |

| HTTP Status | Code           | Description                        |
|-------------|----------------|------------------------------------|
| `{4xx/5xx}` | `{ERROR_CODE}` | `{오류 조건, 상태 변경·미변경 여부, 재시도 가능 여부}` |

내부 예외명, SQL, stack trace, 역직렬화 원문, 비밀값과 개인정보는 응답에 포함하지 않는다. 검증 오류의 필드·메시지·정렬·다건
응답 형식이 필요하면 호환성 영향을 검토하고 이 문서와 채택 ADR을 먼저 갱신한다.
