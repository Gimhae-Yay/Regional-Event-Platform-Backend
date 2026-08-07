# 로그아웃 컨트롤러 통합 테스트 컴파일 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | 로그아웃 관련 Gradle 테스트가 테스트 컴파일 단계에서 실행되지 않는다. |
| 최초 확인 시각·시간대 | 2026-08-01 KST |
| 관련 요구사항·이슈 | [#100 로그아웃 API](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/100) |
| revision·브랜치 | `31dd9d5`, `feature/100-logout` |
| 환경·프로필 | Windows, Gradle Java 21 toolchain |

## 기대 결과와 실제 결과

### 기대 결과

`LogoutControllerIntegrationTest`를 포함한 로그아웃·Redis·보안 경로 테스트가 컴파일되고 실행된다.

### 실제 결과

`LogoutControllerIntegrationTest`의 중첩 설정 클래스에 붙인 `@TestConfiguration`을 해석하지 못해
`compileTestJava`가 종료 코드 1로 실패했다.

## 재현 절차

### 선행 조건

- `feature/100-logout`에서 로그아웃 Controller와 통합 테스트 변경이 존재한다.

### 명령·요청·입력

1. `./gradlew.bat test --tests "io.regionevent.regioneventbackend.domain.user.controller.LogoutControllerIntegrationTest" --tests "io.regionevent.regioneventbackend.infra.redis.RedisRefreshTokenStoreIntegrationTest" --tests "io.regionevent.regioneventbackend.global.config.SecurityConfigIntegrationTest"`를 실행한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle 종료 코드 1, `compileTestJava` 실패

## 수집한 증거

- 컴파일러는 `LogoutControllerIntegrationTest.java:105`에서 `TestConfiguration` 심볼을 찾지 못했다고 보고했다.
- 같은 패턴의 `LoginControllerIntegrationTest`는 `org.springframework.boot.test.context.TestConfiguration`을 import한다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-01 KST | 관찰 | 대상 Gradle 테스트 실행 | 테스트 컴파일 성공 | `TestConfiguration` 심볼 미해결 | 채택 |
| 2026-08-01 KST | 검증 | 로그인 통합 테스트의 import와 비교 | 동일 애너테이션 import 부재 확인 | import가 없다 | 채택 |

## 가설과 검증

### 가설 1: 테스트 애너테이션 import 누락

- 근거: 컴파일러 오류 위치와 기존 로그인 테스트의 import 차이
- 참일 때의 예측: `org.springframework.boot.test.context.TestConfiguration` import 추가 후 테스트 컴파일 성공
- 반증 조건: import 추가 뒤에도 같은 심볼 오류가 발생
- 검증 방법: import 추가 후 같은 Gradle 대상 테스트 재실행
- 결과: import 추가 뒤 대상 Gradle 테스트가 통과했다.
- 판정: 확인

## 근본 원인

- 촉발 조건: 로그아웃 통합 테스트의 테스트 전용 `RefreshTokenStore` Bean 등록
- 결함이 있는 코드·설정·데이터·계약: `LogoutControllerIntegrationTest`의 `TestConfiguration` import 누락
- 증상으로 이어진 메커니즘: Java 컴파일러가 애너테이션 타입을 해석하지 못해 테스트 소스 컴파일을 중단한다.
- 기존 방어가 막지 못한 이유: 새 테스트 파일의 컴파일 전 대상 테스트를 실행하지 않았다.
- 결론의 증거: 컴파일러 오류와 기존 로그인 테스트의 정상 import

## 해결 또는 완화

- 선택한 방법: 필요한 Spring Boot 테스트 애너테이션 import 하나를 추가한다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/domain/user/controller/LogoutControllerIntegrationTest.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | `TestConfiguration` 심볼 미해결 | 대상 테스트 빌드 성공 | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 대상 Gradle 테스트 | 통과 | 로그아웃 Controller·Redis·보안 경로 테스트 |
| `./gradlew.bat test` | 통과 | JUnit 결과 323건, 실패·오류·건너뜀 0건. 셸 64초 제한 뒤에도 Gradle 자식 프로세스가 완료한 결과를 확인했다. |
| `./gradlew.bat build` | 통과 | Gradle 배포본 다운로드 권한 오류 뒤 권한 승인 재실행에서 성공 |

## 재발 방지와 문서 반영

- 새 통합 테스트는 대상 Gradle 테스트로 테스트 소스 컴파일을 먼저 확인한다.

## 잔여 위험과 후속 작업

- 없음

## 관련 자료

- `src/test/java/io/regionevent/regioneventbackend/domain/user/controller/LoginControllerIntegrationTest.java`
