# 인증·인가

## 인증 전달 방식

| 항목        | 계약                                         |
|-----------|--------------------------------------------|
| 인증 헤더     | `Authorization: Bearer <accessToken>` |
| Access Token 응답 헤더 | 로그인·토큰 갱신 성공 응답은 `Authorization: Bearer <accessToken>` 헤더를 포함할 수 있다. |
| 인증 제외 API | `GET /api/v1/regions`, `GET /api/v1/regions/{regionId}/home` |
| 토큰 만료·무효  | `401 Unauthorized`, `UNAUTHENTICATED`                         |

Access Token을 제외한 Refresh Token 전달 방식은 인증 API를 구현하기 전에 해당 도메인 API 명세에서
확정한다. 공통 `ApiResponse`의 Access Token 헤더 팩터리는 Refresh Token을 전달하지 않는다.
인증 제외 API를 추가하거나 제거할 때는 이 표와 해당 도메인 API 명세를 같은 변경에서 갱신한다.

## 인가 표기 규칙

각 API는 다음을 명시한다.

| 항목    | 작성 기준                           |
|-------|---------------------------------|
| 허용 역할 | 방문자, 운영자, 지역 관리자 또는 공개 API 여부   |
| 지역 경계 | 요청 대상과 인증 주체의 `region_id` 비교 조건 |
| 소유권   | 운영자와 콘텐츠·회차·예약의 연결 검증 조건        |
| 실패 결과 | 권한 없음과 대상 부재를 구분하는 공개 오류 코드     |
