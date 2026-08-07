# 로그아웃 Redis 통합 테스트 CI 컴파일 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #247의 Redis Refresh Token 통합 테스트가 실패해 CI가 완료되지 않는다. |
| 최초 확인 시각·시간대 | 2026-08-01 KST |
| 관련 요구사항·이슈 | [#100 로그아웃 API](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/100) |
| revision·브랜치 | `1ab8aa4`, `feature/100-logout` |
| 환경·프로필 | GitHub Actions, Java 21, Gradle 9.5.1 |

## 기대 결과와 실제 결과

### 기대 결과

최신 `dev`와 병합한 로그아웃 브랜치가 `./gradlew --no-daemon clean build`를 통과한다.

### 실제 결과

처음에는 `completeRotation` 반환값에 `isTrue()`·`isFalse()`를 호출해 `compileTestJava`가 실패했다. 최신 `dev`
병합 뒤 enum 단언으로 바꾼 뒤에는, 로그아웃이 회전 표지를 삭제한 경우의 결과값을 `INVALID`로 기대해 테스트가 실패했다.
실제 결과인 `CONFLICT`로 수정해 해결했다.

## 재현 절차

### 선행 조건

- `origin/dev` 최신 커밋을 `feature/100-logout`에 병합한다.

### 명령·요청·입력

1. `./gradlew.bat test --tests "io.regionevent.regioneventbackend.infra.redis.RedisRefreshTokenStoreIntegrationTest"`를 실행한다.
2. `./gradlew.bat build`를 실행한다.

### 재현 결과

- 실행 횟수: CI 2회, Docker 기반 로컬 2회
- 성공 횟수: 1
- 실패 횟수: 3
- 종료 코드·HTTP 상태: CI Gradle 종료 코드 1, 수정 뒤 로컬 Gradle 종료 코드 0

## 수집한 증거

- [CI 실행 30698817466](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30698817466)는
  `completeRotation`의 반환 타입을 `RotationCompletionResult`로 보고한다.
- [CI 실행 30699268040](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30699268040)는
  회전 표지 삭제 뒤 `revokeFamily_removesCurrentRotationMarkerAndUserIndex`의 enum 단언이 실패했음을 보고한다.
- 병합 전 현재 브랜치는 `origin/dev` 최신 커밋 `f2a4af3`을 포함하지 않았다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-01 KST | 관찰 | CI 실패 로그 확인 | 오류 위치·반환 타입 확인 | 두 Boolean 단언에서 컴파일 오류 | 채택 |
| 2026-08-01 KST | 검증 | 최신 `dev` 병합 후 대상 테스트 실행 | enum 결과에 맞춘 단언 필요 여부 확인 | `INVALID` 단언이 CI에서 실패 | 기각 |
| 2026-08-01 KST | 재현 | Docker 기반 Redis 통합 테스트 실행 | 회전 표지 삭제 뒤 실제 결과 확인 | 13건 중 1건 실패 | 채택 |
| 2026-08-01 KST | 회귀 검증 | `CONFLICT` 단언과 CI와 같은 `clean build` 실행 | 전체 컴파일·테스트·산출물 성공 | 테스트 365건, 실패·오류·건너뜀 0건과 JAR 산출물 확인 | 채택 |

## 가설과 검증

### 가설 1: `dev`의 반환 타입·결과값 변경을 로그아웃 테스트가 반영하지 못했다

- 근거: CI 컴파일러가 `RotationCompletionResult` 타입을 보고했으며, 병합 전 브랜치는 최신 `dev`를 포함하지 않았다.
- 참일 때의 예측: `dev` 병합 후 실제 Redis 상태 전이에 맞춘 enum 값 단언을 하면 대상 테스트가 통과한다.
- 반증 조건: Docker 기반 Redis 테스트에서 `CONFLICT` 단언이 실패한다.
- 검증 방법: 병합 뒤 반환 타입 정의와 호출부를 확인하고 대상 Gradle 테스트를 실행한다.
- 결과: `completeRotation`은 `RotationCompletionResult`를 반환했고, 회전 표지 삭제 뒤에는 `CONFLICT`를 반환했다. `CONFLICT` 단언 뒤 Docker 기반 대상 테스트와 전체 build가 통과했다.
- 판정: 확인

## 근본 원인

- 촉발 조건: `completeRotation`의 반환 타입이 Boolean에서 `RotationCompletionResult`로 변경됐다.
- 결함이 있는 코드·설정·데이터·계약: 로그아웃 브랜치의 Redis 통합 테스트 두 곳이 이전 Boolean 계약을 단언했고, 하나는 회전 표지 삭제 뒤 결과를 `INVALID`로 잘못 기대했다.
- 증상으로 이어진 메커니즘: AssertJ의 enum 비교 assertion에는 `isTrue()`·`isFalse()`가 없어 첫 CI의 `compileTestJava`가 중단됐다. 이후 `revokeFamily`가 회전 표지를 삭제하면 완료 Lua 스크립트는 `CONFLICT`를 반환하는데 `INVALID`를 단언해 테스트가 실패했다.
- 기존 방어가 막지 못한 이유: Docker Desktop이 꺼진 로컬 환경에서는 Testcontainers 테스트 13건이 모두 건너뛰었다.
- 결론의 증거: 두 CI 실패 로그, Docker 기반 대상 테스트 재현, `COMPLETE_ROTATION_SCRIPT`의 회전 표지 비교, 수정 뒤 대상·전체 Gradle 검증

## 해결 또는 완화

- 선택한 방법: 두 Boolean 단언을 enum 값 단언으로 교체하고, 회전 표지가 삭제된 경우는 `CONFLICT`를 기대한다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/infra/redis/RedisRefreshTokenStoreIntegrationTest.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| Redis 회전 완료 | 회전 표지 삭제 뒤 `INVALID` 기대 | `CONFLICT` 기대 | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 대상 Redis 통합 테스트 | 통과 | Docker 기반 13건, 실패·오류·건너뜀 0건 |
| `./gradlew.bat --no-daemon clean build` | 통과 | Docker 기반 테스트 365건, 실패·오류·건너뜀 0건과 JAR 산출물 확인 |
| `./gradlew.bat --no-daemon build` | 통과 | 최종 Gradle 종료 코드 0 |

## 재발 방지와 문서 반영

- 최신 `dev` 병합 뒤 테스트 대상 메서드의 현재 반환 계약과 Redis 상태 전이를 기준으로 단언한다.

## 잔여 위험과 후속 작업

- GitHub Actions CI 재실행 결과를 확인한다.

## 관련 자료

- `src/test/java/io/regionevent/regioneventbackend/infra/redis/RedisRefreshTokenStoreIntegrationTest.java`
