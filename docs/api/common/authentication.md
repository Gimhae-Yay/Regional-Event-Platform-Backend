# 인증·인가

## 인증 전달 방식

| 항목        | 계약                                         |
|-----------|--------------------------------------------|
| 인증 헤더     | `Authorization: Bearer <accessToken>` |
| Access Token 응답 헤더 | 로그인·토큰 갱신 성공 응답은 `Authorization: Bearer <accessToken>` 헤더를 포함한다. |
| Refresh Token 전달 | 로그인·토큰 갱신 성공 응답은 `Set-Cookie: refreshToken=<refreshToken>; Max-Age=<refresh-token-ttl>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`를 포함한다. `Domain`은 생략해 호스트 전용 쿠키로 유지하며 Refresh Token은 JSON·`Authorization` 헤더에 넣지 않는다. |
| 인증 제외 API | `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| 토큰 만료·무효  | `401 Unauthorized`, `UNAUTHENTICATED`                         |

Refresh Token은 `Path=/api/v1/auth` 범위의 인증 API에서만 수신하며 보호 업무 API의 인증 수단으로 사용할 수 없다.
교차 사이트 요구로 `SameSite=Strict`를 변경하려면 CSRF 방어와 함께 후속 ADR과 인증 API 명세를 먼저 갱신한다.

## 인가 표기 규칙

각 API는 다음을 명시한다.

| 항목    | 작성 기준                           |
|-------|---------------------------------|
| 허용 역할 | 방문자, 운영자, 지역 관리자 또는 공개 API 여부   |
| 지역 경계 | 요청 대상과 인증 주체의 `region_id` 비교 조건 |
| 소유권   | 운영자와 콘텐츠·회차·예약의 연결 검증 조건        |
| 실패 결과 | 권한 없음과 대상 부재를 구분하는 공개 오류 코드     |
