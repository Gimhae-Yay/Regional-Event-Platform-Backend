# ADR-0025: Spring Security 오류 응답 적용을 인증 구현 시점으로 미룬다

- 상태: 채택됨
- 기록 유형: 대체
- 기록일: 2026-07-29
- 결정일: 2026-07-29
- 관련 요구사항: [응답·오류 공통 계약](../api/common/response-and-error.md), [인증·인가 공통 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: [ADR-0024](0024-use-status-code-message-data-api-response.md)의 Spring Security 인증·인가 오류 직렬화 및 보안 통합 테스트 적용 시점

## 맥락

ADR-0024는 Spring Security의 인증·인가 오류도 `ApiResponse` 형식으로 직렬화하도록 정했다. 그러나 현재는 Access Token 검증,
인증 주체, 역할·지역 권한 정책과 인증 API가 구현되지 않았다. 이 상태에서 `SecurityFilterChain`,
`AuthenticationEntryPoint`, `AccessDeniedHandler`를 먼저 추가하면 기본 Basic/Form Login 동작을 임시로 고정하고,
실제 인증 구현과 무관한 보안 통합 테스트를 유지해야 한다.

사용자는 Spring Security 오류 응답 처리를 인증 구현 시점으로 미루도록 명시했다. `UNAUTHENTICATED`와 `FORBIDDEN`은
공개 오류 코드로 유지하되, 현재 단계에서는 Spring Security 경계에 적용하지 않는다.

## 결정 동인과 불변 조건

- 인증·인가 오류의 공통 JSON 응답은 실제 인증 방식과 권한 정책이 확정된 뒤 구현한다.
- 현재 공통 응답 범위에는 Controller와 전역 MVC 예외 처리기만 포함한다.
- 이미 정의한 `UNAUTHENTICATED`, `FORBIDDEN` 코드와 HTTP 상태·공개 메시지는 변경하지 않는다.
- 인증 구현 전에는 기본 Spring Security 설정과 응답을 임의로 대체하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 인증 구현 시 `AuthenticationEntryPoint`와 `AccessDeniedHandler`를 함께 적용한다. | 실제 토큰 검증·권한 정책과 같은 보안 필터 체인에서 공통 응답을 검증할 수 있다. | 인증 구현 전에는 보안 오류의 JSON 통일이 제공되지 않는다. | 낮음. 현재 추가한 보안 전용 설정·핸들러·테스트를 제거하면 된다. | 사용자가 선택했으며, 임시 보안 정책을 고정하지 않는다. |
| 2 | 공통 응답 작업에서 보안 오류 직렬화를 먼저 적용한다. | 401·403 응답 형식을 조기에 통일한다. | 아직 없는 인증 방식과 역할 정책을 가정해 Basic/Form Login 동작과 테스트를 고정할 수 있다. | 중간. 인증 구현 때 보안 설정과 테스트를 다시 조정해야 한다. | 인증 구현 범위를 앞당기므로 적합하지 않다. |

## 결정

현재 변경에서는 `SecurityFilterChain`, `AuthenticationEntryPoint`, `AccessDeniedHandler`와 보안 필터 체인 통합
테스트를 추가하지 않는다. Spring Security의 401·403 응답은 인증 구현 전까지 기본 동작을 유지한다.

`UNAUTHENTICATED`, `FORBIDDEN`은 향후 인증·인가 실패의 공개 계약으로 `ErrorCode`에 유지한다. 인증 구현 시에는
선택된 토큰 검증 방식과 권한 정책을 기준으로 두 보안 경계에서 `ApiResponse`를 직렬화하고, 미인증·권한 없음
통합 테스트를 함께 추가한다.

## 결과와 트레이드오프

### 기대 효과

- 인증 정책이 없는 단계에서 임시 보안 필터 체인을 도입하지 않는다.
- 실제 인증 구현과 보안 오류 응답 테스트를 하나의 변경으로 검증할 수 있다.

### 수용한 단점과 위험

- 인증 구현 전에는 Spring Security가 반환하는 401·403 응답이 공통 JSON 계약과 다를 수 있다.
- 공통 오류 코드가 정의돼 있어도 해당 보안 경계에서 아직 사용되지 않는다.

## 전환과 롤백

현재 추가된 보안 전용 설정·핸들러·테스트를 제거하고, 기본 Spring Security 동작으로 되돌린다. 데이터 이관이나
외부 API 호환 전환은 없다.

인증 구현을 시작하면 Access Token 검증, 인증 제외 경로, 역할·지역 권한 정책을 먼저 확정한 뒤
`AuthenticationEntryPoint`, `AccessDeniedHandler`, 보안 필터 체인 통합 테스트를 같은 작업에서 추가한다.

## 검증 방법

- 현재 코드에 보안 응답 전용 `SecurityFilterChain`, `AuthenticationEntryPoint`, `AccessDeniedHandler`가 없는지 확인한다.
- Controller와 전역 MVC 예외 처리기의 공통 응답 테스트가 계속 통과하는지 확인한다.
- 인증 구현 시 미인증 401과 권한 없음 403이 `ApiResponse` 계약을 따르고, 토큰·개인정보를 응답과 로그에 노출하지 않는지 통합 테스트로 검증한다.

## 대체 조건

- Access Token 검증과 인증 제외 경로가 구현된다.
- 역할·지역·소유권 인가 정책을 적용할 API가 구현된다.
- Spring Security의 기본 401·403 응답을 외부 클라이언트 계약으로 제공해야 하는 요구가 생긴다.
