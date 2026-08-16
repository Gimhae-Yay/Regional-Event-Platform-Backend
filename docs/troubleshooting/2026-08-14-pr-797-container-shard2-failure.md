# PR #797 Container shard 2 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 원인 확인 |
| 영향 | PR #797의 Testcontainers 검증이 실패해 병합 가능 여부를 판단할 수 없다. |
| 최초 확인 시각·시간대 | 2026-08-14 KST |
| 관련 요구사항·이슈 | #781, PR #797 |
| revision·브랜치 | `8563625d`, `fix/781-serialize-region-admin-protection` |
| 환경·프로필 | GitHub Actions Container shard 2, 로컬 macOS/기본 Gradle 설정 |

## 기대 결과와 실제 결과

### 기대 결과

`containerTestShard2`가 MySQL Testcontainers 기반 테스트를 모두 통과한다.

### 실제 결과

PR #797의 GitHub Actions Container shard 2가 `UserRoleAssignmentRepositoryMySqlTest`에서 `TransactionRequiredException`으로 실패했다.

## 재현 절차

### 선행 조건

- PR #797의 head revision `8563625d`
- GitHub Actions 실행 로그 접근 권한

### 명령·요청·입력

1. PR #797의 실패한 GitHub Actions check와 로그를 조회한다.
2. 실패한 테스트를 `containerTestShard2`로 실행한다.

### 재현 결과

- 실행 횟수: CI 1회, 로컬 수정 후 1회
- 성공 횟수: 없음
- 실패 횟수: CI 1회
- 종료 코드·HTTP 상태: CI 종료 코드 1, 로컬 수정 후 종료 코드 0

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- [실패 job 로그](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/31778436412/job/94698772030): `UserRoleAssignmentRepositoryMySqlTest.MySQL에서_회수된_배정은_활성_배정_유일성에_포함하지_않는다`가 `InvalidDataAccessApiUsageException`으로 실패했고 원인은 `TransactionRequiredException`이다.
- 실패 revision `8563625d`에서 shard 2는 75건 중 1건 실패했다.
- 해당 테스트 클래스는 클래스 수준에서 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 사용하며, 새 `PESSIMISTIC_WRITE` repository 조회는 테스트 메서드에서 트랜잭션 없이 호출됐다.
- 로컬 Docker CLI는 응답했지만 Testcontainers가 `~/.testcontainers.properties`의 `/var/run/docker.sock` 및 Docker Desktop socket을 모두 연결하지 못해 대상 테스트 2건을 skip했다. 따라서 로컬에서는 실제 MySQL 회귀 검증을 수행하지 못했다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-14 KST | 관찰 | PR #797의 Container shard 2 실패를 확인 | GitHub Actions 로그에서 실패한 테스트와 예외를 특정한다 | 75건 중 1건 실패, `TransactionRequiredException` 확인 | 채택 |
| 2026-08-14 KST | 가설 | 새 잠금 조회가 트랜잭션 없이 실행됐다 | 테스트 클래스의 전파 설정과 실패 행을 대조한다 | 클래스 수준 `NOT_SUPPORTED`, 변경된 조회에 `PESSIMISTIC_WRITE` 확인 | 채택 |
| 2026-08-14 KST | 변경 | 조회를 `TransactionTemplate` 안에서 실행 | 동일 MySQL Testcontainers 테스트가 실행된다 | 로컬 Testcontainers 환경 탐지 실패로 대상 2건 skip | 미검증 |

## 가설과 검증

### 가설 1: 새 MySQL 동시성 테스트 또는 관련 테스트가 CI 환경에서 실패한다

- 근거: 실패 check가 Testcontainers shard 2이고, 새 조회에 비관 잠금이 추가됐다.
- 참일 때의 예측: GitHub Actions 로그에 대상 테스트와 `TransactionRequiredException`이 표시되고, 해당 조회가 트랜잭션 밖에서 호출된다.
- 반증 조건: 실패 원인이 테스트 실행 전 환경·인프라 단계에 있거나 조회가 트랜잭션 안에서 실행된다.
- 검증 방법: `gh run view 31778436412 --job 94698772030 --log-failed`와 테스트·repository 코드를 대조한다.
- 결과: 실패 로그는 테스트 85행의 `TransactionRequiredException`을 보였고, 클래스 수준 `NOT_SUPPORTED` 때문에 새 잠금 조회가 트랜잭션 밖에서 실행됐다.
- 판정: 채택

## 근본 원인

- 촉발 조건: `UserRoleAssignmentRepositoryMySqlTest`가 새 `findActiveRegionAdminsForUpdate` 조회를 실행한다.
- 결함이 있는 코드·설정·데이터·계약: 비관 잠금 조회의 호출을 테스트 클래스의 비트랜잭션 경계 밖에 둔 테스트 코드.
- 증상으로 이어진 메커니즘: JPA는 `PESSIMISTIC_WRITE` 잠금을 획득하려면 활성 트랜잭션을 요구하므로 Spring이 `TransactionRequiredException`을 `InvalidDataAccessApiUsageException`으로 변환한다.
- 기존 방어가 막지 못한 이유: 로컬에서는 당시 Docker를 사용할 수 없어 해당 Testcontainers 테스트가 skip됐고, CI shard 2에서 처음 실행됐다.
- 결론의 증거: 실패 job 로그와 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`·`@Lock(PESSIMISTIC_WRITE)` 코드 대조. 수정의 실제 MySQL 회귀 검증은 GitHub Actions 재실행이 필요하다.

## 해결 또는 완화

- 선택한 방법: 테스트 fixture 저장 트랜잭션과 분리해, 잠금 조회만 기존 `TransactionTemplate` 안에서 실행한다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/domain/user/repository/UserRoleAssignmentRepositoryMySqlTest.java`
- 정책·계약 변경 여부: 없음. 프로덕션 역할 변경 정책과 API 계약은 변경하지 않는다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | CI shard 2에서 75건 중 1건 실패 | 로컬 Testcontainers가 Docker를 감지하지 못해 대상 2건 skip | 미검증 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew --no-daemon clean containerTestShard2 --tests 'io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepositoryMySqlTest'` | 종료 코드 0, 테스트 2건 skip | 로컬 Testcontainers가 Docker socket을 감지하지 못함 |
| `./gradlew --no-daemon ciFastCheck` | 성공 | 애플리케이션 패키징 및 Testcontainers 기반이 아닌 `fastTest` |

## 재발 방지와 문서 반영

- 비관 잠금 repository 조회를 직접 호출하는 테스트는 해당 조회만 명시적 트랜잭션에서 실행한다.

## 잔여 위험과 후속 작업

- GitHub Actions에서 새 revision의 shard 2 재실행이 실제 MySQL 회귀 검증으로 남아 있다.

## 관련 자료

- [PR #797](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/pull/797)
- [Issue #781](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/781)
