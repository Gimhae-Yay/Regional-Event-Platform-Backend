# JVM 공유 MySQL 테스트 컨테이너 전환

## 메타데이터

- 상태: 완료
- 개선 유형: 성능, 안정성, 유지보수성
- 범위: Issue #299의 MySQL 통합 테스트 15개와 `dev`에서 추가 확인한 3개
- 관련 요구사항: [FR-01~FR-11과 AC-01~AC-18](../local-stamp-platform-prd.md#12-테스트-및-출시-수용-기준)
- 관련 단계: 단계 1. MVP 구현·검증
- 기준 시각·시간대: 2026-08-03, Asia/Seoul
- Before revision: `ac74e5c5e15f2d54040a5a7c7ce5f3b34a89d283`
- After revision: `06083ec3baa4fa1b6ba8c54a6b74fad455fc3597`
- 작업 트리 상태: Before 측정 시작 시 변경 없음
- 환경: GitHub Actions `ubuntu-latest`, Amazon Corretto 21, MySQL 8.0.42, Gradle Wrapper

## 개선 계약

### 개선 목표와 현재 위험

각 MySQL 통합 테스트 클래스가 컨테이너와 datasource 설정을 중복 생성해 CI 전체 실행시간과
컨테이너 시작 비용이 증가한다. 모든 MySQL 통합 테스트를 JVM 단일 공유 컨테이너로 전환하되,
트랜잭션 롤백과 비트랜잭션 명시적 정리로 테스트 격리와 기존 검증 범위를 보존한다.

### 변경 전 정상 동작 근거

- 기준 revision의 GitHub Actions에서 `./gradlew --no-daemon clean build` 결과를 Before로 사용한다.
- 기존 동시성, 조건부 상태 전이, 유일 제약, 트랜잭션 원자성 테스트의 성공 여부를 계약으로 사용한다.

### 불변 조건

- 제품 API, 운영·개발 datasource, MySQL 설정과 Flyway migration을 변경하지 않는다.
- 기존 MySQL 테스트를 삭제하거나 비활성화하지 않는다.
- 트랜잭션 테스트는 기존 롤백 격리를 유지한다.
- 별도 스레드·트랜잭션 테스트는 FK 정합성을 보존하며 모든 fixture를 정리한다.
- `useAffectedRows=true`, Redis 컨테이너 수명주기와 Redis 데이터 정리를 유지한다.
- 실패 주입, 동시성 제어 객체, MySQL 세션·스키마 변경을 다음 테스트로 전파하지 않는다.

### 지표와 합격 기준

| 지표 | 수집 방법 | 합격 기준 | 반복 횟수·요약 방식 |
| --- | --- | --- | --- |
| MySQL 컨테이너 시작 횟수 | GitHub Actions 빌드 로그의 MySQL 시작 로그 집계 | 전체 빌드에서 1회 | Before/After 각 1회 |
| 전체 CI 실행시간 | GitHub Actions job 시작·종료 시각 | Before보다 단축 | Before/After 각 1회 |
| MySQL 테스트 시간 | Gradle JUnit XML의 대상 테스트 `time` 합계 | Before보다 단축 | Before/After 각 1회 |
| 격리·회귀 | `./gradlew --no-daemon clean build` 반복 및 전체 테스트 결과 | 2회 연속 성공, 실패 0건 | 로컬 After 2회와 GitHub Actions 1회 |

성능 비교의 사전 기준은 Before/After 각 1회이며, 추가 After 재실행은 안정성 검증에 사용한다.
단일 기준선에는 실행기 편차가 있으므로 절대 성능 보장으로 해석하지 않고, 컨테이너 시작 횟수와
함께 개선 여부를 판정한다.

### 제외 범위

- Redis 컨테이너 공통화
- 운영 datasource와 MySQL 설정 변경
- 테스트 삭제·비활성화와 병렬 실행 활성화
- 제품 동작, API, DB schema와 migration 변경

## 재현 조건

- fixture·seed·데이터 크기: 각 테스트가 생성하는 기존 fixture 유지
- 외부 의존성 상태: GitHub Actions Docker에서 MySQL 8.0.42와 기존 Redis 7.4-alpine 사용
- 동시성·요청 비율: 기존 테스트의 스레드 수와 latch 조건 유지
- 준비·warm-up: Gradle 캐시 설정 후 clean build, 별도 warm-up 없음
- 측정 시간·반복 횟수: Before GitHub Actions 1회, After GitHub Actions 2회

## Before

- 명령·입력: `./gradlew --no-daemon clean build`
- 종료 코드: 0
- 정상 계약 검증: 전체 700개 테스트 성공, skipped·failure·error 0건

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 빌드 365초, 전체 job 390초, MySQL 시작 19회, MySQL 테스트 229.859초 | GitHub Actions run 30784432199 |

## 변경 내용

- 대상 18개 테스트의 클래스별 MySQL 컨테이너와 중복 datasource 등록을 제거했다.
- 모든 대상 테스트가 `SharedMySqlTestContainer`의 JVM 공유 MySQL을 사용하도록 전환했다.
- 별도 스레드·트랜잭션 테스트는 `NonTransactionalMySqlTestSupport`의 테스트 전후 DB 정리를 사용한다.
- 기존 `@Transactional` 테스트의 롤백 격리를 유지하고 CHECK 제약을 트랜잭션 전후에 복원한다.
- `useAffectedRows=true`, Redis 수명주기·데이터 정리와 실패 주입 초기화를 유지했다.
- 공유 MySQL 연결 한도를 소진하지 않도록 테스트 datasource의 Hikari 최대 풀 크기를 4로 제한했다.

## After

- 명령·입력: `./gradlew --no-daemon clean build`
- 종료 코드: 0
- 정상 계약 검증: 두 실행 모두 전체 700개 테스트 성공, skipped·failure·error 0건

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 빌드 153초, 전체 job 171초, MySQL 시작 1회, MySQL 테스트 63.979초 | GitHub Actions run 30785254416 attempt 1 |
| 2 | 빌드 167초, 전체 job 188초, MySQL 시작 1회, MySQL 테스트 76.890초 | GitHub Actions run 30785254416 attempt 2 |

## 비교와 판정

| 항목 | Before | After | 변화량 | 판정 |
| --- | --- | --- | --- | --- |
| MySQL 컨테이너 시작 횟수 | 19회 | 1회 | 18회, 94.7% 감소 | 충족 |
| 전체 CI 빌드 시간 | 365초 | 153초·167초 | 212초(58.1%)·198초(54.2%) 감소 | 충족 |
| 전체 CI job 시간 | 390초 | 171초·188초 | 219초(56.2%)·202초(51.8%) 감소 | 충족 |
| MySQL 테스트 시간 | 229.859초 | 63.979초·76.890초 | 165.880초(72.2%)·152.969초(66.6%) 감소 | 충족 |

두 After 실행 모두 컨테이너 시작 횟수, 실행시간과 회귀 기준을 충족해 성능·안정성 개선으로 판정한다.

## 회귀·실패 경로 검증

- GitHub Actions에서 동일 SHA의 `./gradlew --no-daemon clean build`를 2회 연속 통과했다.
- 두 실행 모두 700개 테스트가 실행됐고 skipped·failure·error는 0건이다.
- 동시성, 조건부 상태 전이, 유일 제약, 트랜잭션 원자성 테스트를 삭제하거나 비활성화하지 않았다.
- 개별 클래스의 MySQL 컨테이너 선언은 공통 지원 클래스 외에 남지 않았다.
- `build.gradle`, 운영·개발 datasource, MySQL 설정과 Flyway migration은 변경하지 않았다.
- 최초 PR CI에서 컨텍스트별 Hikari 풀이 공유 MySQL 연결 한도를 소진하는 실패를 확인했고,
  테스트 전용 최대 풀 크기를 4로 제한한 뒤 동일 절차에서 재현되지 않았다.

## 한계와 잔여 위험

- Before 성능 기준선이 1회이므로 실행기 편차를 완전히 제거하지 못한다.
- 최초 계약의 로컬 After 2회는 Docker 데몬 부재로 MySQL 테스트가 skip되어 성능 근거로 쓰지 않았다.
  대신 동일 GitHub Actions run을 재실행해 Docker가 있는 같은 조건에서 2회 연속 성공을 확인했다.
- Before는 1회, After는 2회이므로 수치는 장기 성능 분포나 SLO가 아니라 이번 CI 병목 개선 근거다.

## 증거 링크

- 관련 코드·테스트: [`src/test/java`](../../src/test/java)
- 원시 결과·로그: [Before GitHub Actions run 30784432199](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30784432199)
- After 원시 결과·로그: [attempt 1](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30785254416/attempts/1), [attempt 2](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30785254416/attempts/2)
- 관련 이슈·ADR: [Issue #299](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/299)
