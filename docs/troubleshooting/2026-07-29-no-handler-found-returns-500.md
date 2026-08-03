# 미등록 경로가 404 대신 500으로 응답하는 문제

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | 미등록 경로 요청이 공통 응답 계약의 `404 NOT_FOUND` 대신 `500 INTERNAL_SERVER_ERROR`로 응답한다. |
| 최초 확인 시각·시간대 | 2026-07-29 15:26 KST |
| 관련 요구사항·이슈 | PR #61 리뷰의 알려진 HTTP 오류 매핑 요청, [응답·오류 공통 계약](../api/common/response-and-error.md) |
| revision·브랜치 | `ac09840380ac72139f6f61589a0bf43d31c18ebf` · `feature/global-api-response` |
| 환경·프로필 | Windows, Java 21, Gradle 9.5.1, 기본 테스트 프로필 |

## 기대 결과와 실제 결과

### 기대 결과

미등록 경로 요청은 HTTP 404와 `statusCode: 404`, `code: NOT_FOUND`, `data: null`의 공통 JSON 응답을 반환한다.

### 실제 결과

`GlobalExceptionHandlerTest`의 미등록 경로 요청이 HTTP 500으로 응답했다. 테스트 로그에는
`NoHandlerFoundException`이 catch-all 처리기에 도달한 사실이 기록됐다.

## 재현 절차

### 실행 조건

현재 브랜치에 PR #61 리뷰 반영 중인 변경이 있는 상태에서 실행했다.

### 명령·요청·입력

1. `.\gradlew.bat test`
2. `GlobalExceptionHandlerTest.handleNoResourceFoundException_returnsNotFoundResponse`
3. `GET /test/missing-resource`

### 재현 결과

- 실행 횟수: 1회
- 성공 횟수: 17개
- 실패 횟수: 1개
- 종료 코드·HTTP 상태: Gradle 종료 코드 1, HTTP 500

## 수집한 증거

- `build/test-results/test/TEST-io.regionevent.regioneventbackend.global.error.GlobalExceptionHandlerTest.xml`에 기대 상태 404와 실제 상태 500이 기록됐다.
- 같은 출력에서 `org.springframework.web.servlet.NoHandlerFoundException`이 `handleUnexpectedException`에 전달된 스택 트레이스를 확인했다.
- 현재 예외 처리기는 `NoResourceFoundException`만 `NOT_FOUND`로 변환한다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 관찰·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-29 15:26 KST | 관찰 | 전체 테스트 실행 | 미등록 경로 검증만 HTTP 500으로 실패 | 재현됨 | 원인 조사 |
| 2026-07-29 15:27 KST | 가설 | `NoResourceFoundException` 외의 미등록 경로 예외가 발생한다 | 테스트 출력에서 `NoHandlerFoundException` 확인 | 가설 지지 | 원인 확인 |
| 2026-07-29 15:31 KST | 검증 | 두 예외를 `NOT_FOUND`로 매핑한 뒤 단일 테스트 실행 | `GlobalExceptionHandlerTest` 7개 통과 | 재현 해소 | 해결 |

## 가설과 검증

### 가설 1: `NoHandlerFoundException`이 `NOT_FOUND` 변환 대상에서 빠졌다

- 근거: 테스트 출력의 실제 예외 타입과 현재 `@ExceptionHandler` 대상이 다르다.
- 참이라면 기대 결과: 두 예외를 같은 `NOT_FOUND` 변환 대상으로 등록하면 미등록 경로가 404 공통 응답을 반환한다.
- 반증 조건: 등록 뒤에도 catch-all 처리기가 실행되거나 HTTP 500이 반환된다.
- 검증 방법: 두 예외를 등록한 뒤 단일 테스트와 전체 테스트를 재실행한다.
- 결과: `NoHandlerFoundException`과 `NoResourceFoundException`을 같은 `NOT_FOUND` 처리기에 등록한 뒤 단일 테스트 7개가 통과했다.
- 판정: 해결

## 근본 원인

- 촉발 조건: `DispatcherServlet`이 매핑되지 않은 경로를 `NoHandlerFoundException`으로 전달한다.
- 결함이 있는 코드·설정·계약: `GlobalExceptionHandler`가 `NoResourceFoundException`만 `NOT_FOUND`로 매핑한다.
- 증상으로 이어진 메커니즘: 매핑되지 않은 `NoHandlerFoundException`이 `Exception.class` 처리기로 전달돼 500 오류 응답으로 변환된다.
- 기존 방어가 막지 못한 이유: 정적 리소스 경로에서 발생하는 예외만 고려했고, 일반 핸들러 미매핑 예외를 포함하지 않았다.
- 결론과 증거: 테스트 스택 트레이스와 현재 매핑 목록을 대조해 원인을 확인했다.

## 해결 또는 완화

- 선택한 방법: `NoHandlerFoundException`과 `NoResourceFoundException`을 모두 `NOT_FOUND`로 개별 매핑한다.
- 변경 파일: `GlobalExceptionHandler.java`, `GlobalExceptionHandlerTest.java`
- 정책·계약 변경 여부: 없음. 기존 공통 응답 계약의 `404 NOT_FOUND`를 구현 경계까지 적용한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 미등록 경로 공통 응답 | HTTP 500, `INTERNAL_SERVER_ERROR` | HTTP 404, `NOT_FOUND` | 통과 |

## 추가 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `.\gradlew.bat test --tests 'io.regionevent.regioneventbackend.global.error.GlobalExceptionHandlerTest'` | 통과 | 7개 테스트 |
| `.\gradlew.bat test` | 통과 | 전체 18개 테스트 |
| `.\gradlew.bat build` | 통과 | 전체 빌드 |

## 재발 방지와 문서 반영

- `NoHandlerFoundException`과 `NoResourceFoundException`의 404 공통 응답을 MVC 테스트로 유지한다.
- 공개 오류 코드 `NOT_FOUND`는 [응답·오류 공통 계약](../api/common/response-and-error.md)에 정의한다.

## 잔여 위험과 후속 작업

- 없음.

## 관련 자료

- `build/test-results/test/TEST-io.regionevent.regioneventbackend.global.error.GlobalExceptionHandlerTest.xml`
