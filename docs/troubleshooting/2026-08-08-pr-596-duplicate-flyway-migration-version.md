# PR #596 Flyway 마이그레이션 버전 중복

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | GitHub Actions CI가 Flyway 초기화 전에 중단돼 1,254개 테스트 중 651개가 연쇄 실패했다. |
| 최초 확인 시각·시간대 | 2026-08-08 17:32 KST |
| 관련 요구사항·이슈 | PR #596, #542 |
| revision·브랜치 | `947d65f7`, `feature/542-platform-admin-authorization-persistence` |
| 환경·프로필 | GitHub Actions, Amazon Corretto JDK 21, `./gradlew --no-daemon clean build` |

## 기대 결과와 실제 결과

### 기대 결과

PR 병합 기준의 Flyway 마이그레이션은 버전마다 하나여야 하며, 전체 테스트 컨텍스트가 초기화돼야 한다.

### 실제 결과

CI 실행에서 Flyway `CompositeMigrationResolver`가 실패했고, 이후 Spring 컨텍스트를 사용하는 테스트가 연쇄 실패했다.

## 재현 절차

### 선행 조건

- 기준 브랜치 `dev`에 역할 배정 이력 마이그레이션 `V17`이 존재한다.
- PR #596에 전체관리자 인가 마이그레이션 `V17`이 존재한다.

### 명령·요청·입력

1. PR #596의 GitHub Actions CI를 실행한다.
2. `./gradlew --no-daemon clean build` 단계의 Flyway 초기화 결과를 확인한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle 종료 코드 1

## 수집한 증거

- [CI 실행](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/31248625967)에서 `CompositeMigrationResolver` 예외 뒤 651개 테스트가 실패했다.
- `dev`에는 `V17__migrate_user_role_assignment_to_history.sql`이, PR 브랜치에는 `V17__add_platform_admin_authorization_persistence.sql`이 있다.
- 두 마이그레이션을 함께 해석하면 동일한 Flyway 버전 `17`이 중복된다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-08 17:32 KST | 관찰 | GitHub Actions 실패 로그 확인 | 최초 실패 원인을 확인한다. | Flyway `CompositeMigrationResolver` 예외를 확인했다. | 채택 |
| 2026-08-08 17:35 KST | 검증 | PR·`dev` 마이그레이션 버전 비교 | 버전 중복이면 `V17` 파일이 두 개다. | 역할 이력용과 전체관리자용 `V17`을 각각 확인했다. | 채택 |
| 2026-08-08 18:00 KST | 변경 | 최신 `dev` 병합과 전체관리자 마이그레이션 `V18` 이동 | 마이그레이션 버전이 순차적으로 하나씩 존재한다. | `V16`, 역할 이력 `V17`, 전체관리자 인가 `V18`을 확인했다. | 채택 |
| 2026-08-08 18:02 KST | 검증 | CI와 같은 `clean build` 실행 | Flyway 초기화와 전체 테스트가 성공한다. | 271개 테스트 결과 파일에서 실패·오류가 없었다. | 채택 |

## 근본 원인

- 촉발 조건: PR 병합 기준으로 최신 `dev`와 PR #596의 마이그레이션을 함께 로드했다.
- 결함이 있는 코드·설정·데이터·계약: 서로 다른 두 마이그레이션에 `V17` 버전을 사용했다.
- 증상으로 이어진 메커니즘: Flyway가 중복 버전을 해석할 수 없어 애플리케이션 컨텍스트 초기화 전에 중단됐다.
- 기존 방어가 막지 못한 이유: PR 브랜치가 최신 `dev`의 역할 이력 마이그레이션보다 앞선 기준에서 작성됐다.
- 결론의 증거: CI 로그, PR 브랜치와 `dev`의 마이그레이션 파일 목록.

## 해결 또는 완화

- 선택한 방법: 최신 `dev`를 병합하고 전체관리자 인가 마이그레이션을 `V18`로 이동했다.
- 변경 파일: `V18__add_platform_admin_authorization_persistence.sql`, `InitialP0SchemaMigrationTest`
- 정책·계약 변경 여부: 없음.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| PR 병합 기준 CI | Flyway 버전 중복으로 실패 | `V17`, `V18`이 각각 하나씩 적용된다. | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew --no-daemon clean build` | 통과 | 271개 테스트 결과 파일에 실패·오류 없음 |

## 잔여 위험과 후속 작업

- 원격 CI 재실행 결과는 푸시 후 확인한다.

## 관련 자료

- [PR #596](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/pull/596)
- [실패한 CI 실행](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/31248625967)
