# PR #333 파이프라인 테스트 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #333의 GitHub Actions `빌드 및 테스트` 체크가 실패해 병합이 차단됨 |
| 최초 확인 시각·시간대 | 2026-08-03 18:03 KST |
| 관련 요구사항·이슈 | PR #333, 이슈 #261, `CON-06`, `CON-09` |
| revision·브랜치 | `fa28be404cf35679f1da4432cd84ac8bfa9c563b`, `feature/261-content-suspension-api` |
| 환경·프로필 | GitHub Actions, Amazon Corretto 21.0.12, MySQL 8.0.42 Testcontainers, 기본 프로필 |

## 기대 결과와 실제 결과

### 기대 결과

`./gradlew clean test jacocoTestReport`가 성공하고 콘텐츠 운영 중단과 홀드 만료의 경합 테스트가 교착 없이 정원 1회 복구를 검증한다.

### 실제 결과

653개 테스트 중 `SuspendContentControllerMySqlIntegrationTest.중단과_홀드만료가_경합해도_교착없이_홀드를_한번만_종결한다` 1개가 실패했다. 테스트의 잠금 대기 확인 SQL이 `performance_schema.data_lock_waits` 조회 권한 거부로 실패했다.

## 재현 절차

### 선행 조건

- Docker 실행
- Java 21
- PR #333 head revision 체크아웃

### 명령·요청·입력

1. `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.content.controller.SuspendContentControllerMySqlIntegrationTest.중단과_홀드만료가_경합해도_교착없이_홀드를_한번만_종결한다'`

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: 1

## 수집한 증거

- GitHub Actions run `30799186934`, job `91639636388`
- 실패 예외: `java.sql.SQLSyntaxErrorException: SELECT command denied to user 'test' for table 'data_lock_waits'`
- 실패 위치: `SuspendContentControllerMySqlIntegrationTest.java:610`
- 업무 API 및 운영 코드 경로를 검증하는 나머지 테스트 652개는 통과했다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-03 18:03 KST | 관찰 | CI 테스트 리포트 확인 | 실패 테스트와 최하위 원인을 식별한다. | `performance_schema.data_lock_waits` 조회 권한 거부를 확인했다. | 완료 |
| 2026-08-03 18:03 KST | 가설 | 운영 코드가 아니라 테스트 관측 코드의 권한 가정이 잘못됐다. | 같은 업무 동작의 다른 테스트는 통과하고, 실패 스택은 테스트 전용 잠금 관측 메서드에만 있어야 한다. | CI에서 다른 652개 테스트가 통과했고 실패 스택이 테스트 내부 SQL에 한정됐다. | 채택 후보 |
| 2026-08-03 18:04 KST | 검증 | PR head에서 실패 테스트 단독 실행 | CI와 같은 권한 오류로 실패해야 한다. | 동일한 `SELECT command denied` 오류로 1회 실패했다. | 채택 |
| 2026-08-03 18:05 KST | 변경 | 잠금 관측 테이블 세 개의 조회 권한만 테스트 사용자에게 부여 | 관측 SQL과 기존 경합 결과 검증이 통과해야 한다. | 원래 실패 테스트가 통과했다. | 완료 |
| 2026-08-03 18:09 KST | 검증 | 전체 빌드 실행 | 다른 MySQL 테스트와 전체 회귀 테스트가 통과해야 한다. | `./gradlew build --no-build-cache --rerun-tasks`가 성공했다. | 완료 |

## 가설과 검증

### 가설 1: 테스트 전용 잠금 관측 SQL의 권한 가정 오류

- 근거: MySQL `test` 사용자가 `performance_schema.data_lock_waits`를 조회할 수 없어 실패했다.
- 참일 때의 예측: 동일 revision의 단일 테스트도 같은 권한 오류로 실패하며, 필요한 관측 권한을 테스트 환경에 제공하면 업무 결과 검증이 통과한다.
- 반증 조건: 권한을 제공한 뒤에도 운영 코드의 교착, 타임아웃 또는 상태·정원 불일치로 실패한다.
- 검증 방법: 원본 단일 테스트 재현 후 테스트 환경만 최소 수정하고 동일 테스트와 관련 회귀 테스트를 재실행한다.
- 결과: 수정 전 단일 테스트가 같은 권한 오류로 실패했고, 관측 권한만 부여한 뒤 원래 테스트·통합 테스트 클래스·전체 빌드가 통과했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 콘텐츠 운영 중단 트랜잭션이 회차 행을 잠근 동안 홀드 만료 트랜잭션의 잠금 대기를 확인하는 경합 테스트가 실행됐다.
- 결함이 있는 코드·설정·데이터·계약: 테스트 전용 `isTerminationSessionLockWaitingForSuspension`은 `performance_schema.data_lock_waits`, `data_locks`, `threads`를 조회하지만 Testcontainers의 기본 `test` 사용자에게 해당 조회 권한을 제공하지 않았다.
- 증상으로 이어진 메커니즘: 잠금 대기 여부를 검증하기 전에 관측 SQL이 MySQL 권한 검사에서 거부돼 `BadSqlGrammarException`으로 변환됐고 테스트가 실패했다.
- 기존 방어가 막지 못한 이유: 로컬 사전 검증 환경에서는 권한 조건이 확인되지 않았고, 테스트 자체에도 필요한 관측 권한을 준비하는 설정이 없었다.
- 결론의 증거: CI와 로컬에서 동일 권한 오류를 재현했고, 운영 코드는 바꾸지 않은 채 정확히 세 테이블의 조회 권한만 추가하자 원래 테스트와 전체 빌드가 통과했다.

## 해결 또는 완화

- 선택한 방법: 공유 MySQL 테스트 컨테이너에 잠금 관측 권한 부여 메서드를 추가하고, 해당 관측을 사용하는 통합 테스트가 컨텍스트 초기화 전에 호출하도록 했다.
- 변경 파일: `SharedMySqlTestContainer.java`, `SuspendContentControllerMySqlIntegrationTest.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | 1개 테스트 실패, 종료 코드 1 | 1개 테스트 통과, 종료 코드 0 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 원래 실패 테스트 단독 실행 | 성공 | 수정 후 동일 명령 |
| `SuspendContentControllerMySqlIntegrationTest` 전체 | 성공 | 7개 테스트 |
| `./gradlew build --no-build-cache --rerun-tasks` | 성공 | 전체 회귀·패키징 |

## 재발 방지와 문서 반영

잠금 관측이 필요한 테스트가 표준 Testcontainers 환경에서 스스로 최소 권한을 준비하도록 했다. 운영 데이터베이스 권한과 애플리케이션 코드는 변경하지 않았다.

## 잔여 위험과 후속 작업

관측 SQL은 고정된 MySQL 8.0.42의 `performance_schema` 구조에 의존한다. 이미지 버전을 변경할 때 테이블·컬럼 호환성을 다시 확인해야 한다.

## 관련 자료

- https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/pull/333
- https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30799186934
- https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/261
