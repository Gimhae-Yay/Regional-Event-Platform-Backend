# ADR-0105: Access Token을 JSON 응답 본문으로 전달

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-17
- 결정일: 2026-08-17
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [ADR-0005](0005-use-jwt-access-and-rotating-refresh-tokens.md#결정), [ADR-0023](0023-manage-refresh-token-revocation-in-redis.md#결정)
- 관련 단계: 단계 0. 정책·설계 확정
- 관련 이슈: [#889](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/889)
- 대체 대상: [ADR-0027](0027-deliver-refresh-token-in-http-only-cookie.md)의 로그인·토큰 갱신 성공 응답 Access Token 전달 위치 결정만 대체한다. Refresh Token의 `HttpOnly` 쿠키 전달 결정은 유지한다.

## 맥락

ADR-0027은 로그인과 토큰 갱신 성공 응답에서 Access Token을 `Authorization` 응답 헤더로 전달하도록 정했다.
이제 클라이언트가 두 API의 성공 결과를 공통 JSON 응답 본문에서 처리하도록, Access Token 전달 위치를
`data.accessToken`으로 통일한다.

이 변경은 장기 자격 증명인 Refresh Token의 노출 경계를 완화하는 요구가 아니다. Refresh Token은 기존처럼
JavaScript가 읽을 수 없는 `HttpOnly` 쿠키로만 전달하고, 보호 업무 API는 Access Token을 `Authorization`
요청 헤더로만 수신한다.

## 결정 동인과 불변 조건

- 로그인과 토큰 갱신 성공 응답은 같은 Access Token 전달 계약을 제공해야 한다.
- Refresh Token 원문은 JSON 응답, 일반 응답 헤더, 로그, DB 또는 클라이언트 JavaScript에 노출하지 않는다.
- 보호 업무 API는 Refresh Token을 인증 수단으로 허용하지 않고 Access Token만 사용한다.
- Refresh Token의 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, 호스트 전용 `Domain` 생략과
  계열 수명·회전·폐기·재사용 탐지는 유지한다.
- 오류 응답에는 Access Token, Refresh Token, `jti`, `family_id` 또는 내부 예외 정보를 포함하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 추천안: 로그인·토큰 갱신의 Access Token을 `data.accessToken` JSON 본문으로, Refresh Token은 기존 `HttpOnly` 쿠키로 전달 | 두 성공 API의 토큰 수신 방식을 공통 응답 본문으로 통일하면서 Refresh Token의 JavaScript 비노출 경계를 유지한다. | Access Token이 응답 본문을 읽는 클라이언트 코드에 노출되므로 클라이언트의 XSS 방어와 보관 정책에 의존한다. 기존 응답 헤더를 읽는 클라이언트는 함께 전환해야 한다. | 중간. 두 API의 응답 DTO·Controller·계약 테스트와 클라이언트를 함께 되돌려야 한다. | 채택. 사용자가 선택한 JSON 본문 수신 계약과 Refresh Token 보안 경계를 함께 만족한다. |
| 2 | Access Token을 기존 `Authorization` 응답 헤더로 유지 | Access Token이 응답 본문에 포함되지 않고 기존 클라이언트 호환성을 유지한다. | 선택된 공통 JSON 응답 본문 계약을 제공하지 못한다. | 낮음. 현행 구현을 유지한다. | 현재 요구에 부적합하다. |
| 3 | Access Token과 Refresh Token을 모두 JSON 본문으로 전달 | 두 토큰의 수신 형식이 단순해진다. | Refresh Token이 JavaScript와 클라이언트 저장소에 노출돼 장기 자격 증명의 XSS 노출 범위를 넓힌다. | 중간. 쿠키 기반 갱신·폐기 경계를 다시 설계해야 한다. | 현재 보안 요구에 부적합하다. |

## 결정

로그인(`POST /api/v1/auth/login`)과 토큰 갱신(`POST /api/v1/auth/refresh`)의 성공 응답은 Access Token을
`data.accessToken` 문자열로 반환한다. 두 성공 응답에 `Authorization: Bearer <accessToken>` 응답 헤더를
넣지 않는다.

Refresh Token은 기존과 같은 `Set-Cookie` 헤더로만 반환한다.

```http
Set-Cookie: refreshToken=<refreshToken>; Max-Age=<refresh-token-ttl>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict
```

쿠키의 `Domain` 속성은 생략한다. Refresh Token은 JSON 본문, `Authorization` 헤더 또는 다른 일반 응답 헤더에
넣지 않는다. 일반 보호 API 요청은 기존과 같이 `Authorization: Bearer <accessToken>`을 사용한다.

## 결과와 트레이드오프

### 기대 효과

- 로그인과 토큰 갱신 성공 결과에서 Access Token을 같은 `data.accessToken` 경로로 처리할 수 있다.
- Refresh Token의 쿠키 전용 전달, 회전과 폐기 경계를 유지한다.
- 성공 응답 헤더와 본문에 Access Token을 중복 전달하지 않아 API 계약이 하나로 명확해진다.

### 수용한 단점과 위험

- Access Token이 JSON 본문을 읽는 클라이언트 코드에 노출되므로 XSS 방어와 저장소 선택은 클라이언트 구현의 책임이 된다.
- 기존 `Authorization` 응답 헤더를 읽는 클라이언트는 두 API 구현 배포와 함께 `data.accessToken`으로 전환해야 한다.
- 이 결정은 Refresh Token을 JSON 본문으로 옮기지 않으므로, 쿠키를 지원하지 않는 교차 사이트·비브라우저 갱신 요구는 해결하지 않는다.

## 전환과 롤백

문서 계약을 먼저 변경한 뒤 로그인과 토큰 갱신 구현 Task에서 각각 응답 DTO·Controller·계약 테스트를 바꾼다.
서버는 전환 중 Access Token을 헤더와 본문에 동시에 전달하지 않는다. 클라이언트는 두 API의 새 계약과 함께
배포한다. Refresh Token 쿠키, Redis 상태나 데이터 이관은 필요하지 않다.

롤백이 필요하면 두 API의 문서와 구현을 같은 배포 단위에서 이전 `Authorization` 응답 헤더 계약으로 되돌린다.
Refresh Token 쿠키를 만료시키거나 계열을 폐기하지 않는다. 채택된 이 결정을 다시 바꾸려면 이 ADR을 수정하지 않고
후속 ADR로 대체한다.

## 검증 방법

- 로그인 성공 응답이 `data.accessToken`을 포함하고 `Authorization` 응답 헤더를 포함하지 않는지 검증한다.
- 토큰 갱신 성공 응답이 `data.accessToken`을 포함하고 `Authorization` 응답 헤더를 포함하지 않는지 검증한다.
- 두 성공 응답의 Refresh Token 쿠키가 기존 보안 속성·경로·수명 규칙을 유지하는지 검증한다.
- 로그인·토큰 갱신의 오류 응답과 로그에 두 토큰 원문, `jti`, `family_id`가 없는지 검증한다.
- 보호 업무 API가 계속 요청 `Authorization: Bearer <accessToken>`만 인증 수단으로 사용하는지 검증한다.

## 대체 조건

- Access Token의 JSON 본문 노출이 합의된 클라이언트 보안 기준을 충족하지 못한다는 보안 검토 결과가 나온다.
- 기존 헤더 기반 클라이언트와의 무중단 호환 계층이 필수라는 요구가 채택된다.
- Refresh Token을 지원해야 하는 교차 사이트 또는 비브라우저 클라이언트 요구가 생겨 쿠키·CSRF 경계를 다시 설계해야 한다.
