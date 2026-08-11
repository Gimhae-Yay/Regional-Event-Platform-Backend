# #690 Testcontainers MySQL tmpfs 적용의 순차 전체 테스트 비교

## 메타데이터

- 상태: 검증 중
- 개선 유형: 성능, 유지보수성
- 범위: `SharedMySqlTestContainer`가 만드는 테스트 전용 MySQL 데이터 디렉터리
- 관련 요구사항: [P0 테스트 및 출시 수용 기준](../p0-spec.md#9-테스트-및-출시-수용-기준)
- 관련 단계: 단계 1. MVP 구현·검증
- 기준 시각·시간대: 2026-08-11 KST
- Before revision: `829524359d5a968a2000379bcfd236653dc23d28`
- After revision: 측정 후 기록
- 작업 트리 상태: ADR-0093 커밋 `829524359d5a968a2000379bcfd236653dc23d28` 위에 조사·개선 기록이 untracked인 상태. 코드·테스트 구성은 Before 동안 변경하지 않았다.
- 환경: macOS arm64, OpenJDK 21.0.7, Gradle 9.5.1, Docker Server 29.5.2, Docker 메모리 7.75 GiB, Docker CPU 4, MySQL 8.0.42, Gradle daemon 미사용

## 개선 계약

### 개선 목표와 현재 위험

테스트 데이터가 종료 뒤 필요하지 않은 공유 Testcontainers MySQL의 `/var/lib/mysql`을 제한된 tmpfs로 옮겨,
순차 전체 테스트에서 `MySqlDatabaseCleaner`의 반복 `TRUNCATE`가 로컬 Docker writable layer I/O에 의존하는
위험을 줄인다. 이 기록은 #687의 동시 Gradle 실행 잠금 대기를 해결했다고 주장하지 않는다.

### 변경 전 정상 동작 근거

Before에서는 Docker가 실행된 상태에서 전체 테스트가 종료 코드 0으로 끝나고, MySQL 통합 테스트의 migration·정리·격리가
유지되어야 한다. 정상 종료하지 못한 실행은 improvement가 아니라 troubleshooting 증거로만 기록한다.

### 불변 조건

- 테스트 컨테이너만 변경하고 제품 MySQL·운영 Docker·공개 API·DB migration·cleaner 알고리즘은 변경하지 않는다.
- `MySqlDatabaseCleanerIntegrationTest`의 fixture 정리와 Flyway 이력 보존을 유지한다.
- Docker 메모리 부족, swap, OOM 또는 tmpfs 공간 부족이 발생한 실행은 성능 성공으로 판정하지 않는다.

### 지표와 합격 기준

| 지표 | 수집 방법 | 합격 기준 | 반복 횟수·요약 방식 |
| --- | --- | --- | --- |
| 순차 전체 테스트 경과 시간 | `/usr/bin/time -lp ./gradlew --no-daemon clean test`의 `real` 값 | After 중앙값이 Before 중앙값보다 작고 두 실행 모두 종료 코드 0 | Before 2회, After 2회; 각 조건의 중앙값은 두 값의 산술 평균 |
| 테스트 데이터 격리 | MySQL cleaner 통합 테스트와 전체 테스트 결과 | After 두 실행에서 실패·skip 없이 통과 | After 2회와 후속 전체 회귀 |
| 메모리 안정성 | Gradle·Docker 출력과 `docker stats --no-stream` | OOM, tmpfs 공간 부족, swap 경고 없음 | 모든 측정 실행에서 확인 |

### 제외 범위

- 같은 작업 트리 Gradle 동시 실행 정책·잠금 대기 제한·`MySqlDatabaseCleaner` 알고리즘
- 제품 MySQL과 운영 Docker의 데이터 내구성·저장소 설정
- 테스트 데이터셋 또는 CI shard 정책 변경

## 재현 조건

- fixture·seed·데이터 크기: 전체 현재 테스트 suite가 생성하는 기본 fixture, 외부 시드 없음
- 외부 의존성 상태: 로컬 Docker daemon 실행, Testcontainers가 MySQL 8.0.42를 생성, 외부 API 호출 없음
- 동시성·요청 비율: Gradle 명령 한 번만 실행하는 순차 조건
- 준비·warm-up: 각 반복에서 `clean`으로 테스트 결과를 제거하고, 명령 전 별도 Gradle daemon 실행을 사용하지 않음
- 측정 시간·반복 횟수: Before 2회, After 2회; `/usr/bin/time -lp`의 벽시계 `real` 값

## Before

- 명령·입력: `/usr/bin/time -lp ./gradlew --no-daemon clean test`
- 종료 코드: 0, 0
- 정상 계약 검증: 각 실행 1,710 tests 통과, skip/failure/error 0; `/usr/bin/time`의 swaps 0회

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 694.74초 | Docker writable layer, tmpfs 미적용; user 472.63초, sys 59.19초, 최대 RSS 2,674,163,712 bytes, swaps 0회 |
| 2 | 693.82초 | Docker writable layer, tmpfs 미적용; user 489.58초, sys 55.13초, 최대 RSS 2,666,905,600 bytes, swaps 0회 |

## 변경 내용

ADR-0093을 따른 테스트 전용 tmpfs 구성 및 이를 고정하는 테스트를 적용할 예정이다.

## After

- 명령·입력: `/usr/bin/time -lp ./gradlew --no-daemon clean test`
- 종료 코드: 측정 예정
- 정상 계약 검증: 측정 예정

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 측정 예정 | tmpfs 적용 후 |
| 2 | 측정 예정 | tmpfs 적용 후 |

## 비교와 판정

| 항목 | Before | After | 변화량 | 판정 |
| --- | --- | --- | --- | --- |
| 순차 전체 테스트 경과 시간 중앙값 | 694.28초 | 측정 예정 | 측정 예정 | 대기 |
| MySQL 테스트 데이터 격리 | 1,710 tests 통과 | 측정 예정 | 해당 없음 | 대기 |
| 메모리 안정성 | swaps 0회, OOM 없음 | 측정 예정 | 해당 없음 | 대기 |

## 회귀·실패 경로 검증

- `MySqlDatabaseCleanerIntegrationTest`, 전체 `test`, 전체 `build` 결과를 After에 기록한다.
- tmpfs 공간 부족, Docker 메모리 부족, swap, OOM, 컨테이너 초기화 실패는 모두 troubleshooting 기록으로 연결한다.

## 한계와 잔여 위험

- 두 번의 순차 실행은 로컬 성능 변동을 완전히 제거하지 않는다.
- 이 비교는 Docker Desktop 메모리 7.75 GiB·4 CPU 환경에서만 유효하며 CI·다른 호스트의 절대 시간으로 일반화하지 않는다.
- #687의 동시 Gradle 실행 잠금 대기는 이 범위에서 재현하지 않으며 별도 위험으로 남는다.

## 증거 링크

- 관련 코드·테스트: [SharedMySqlTestContainer](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainer.java), [MySqlDatabaseCleanerIntegrationTest](../../src/test/java/io/regionevent/regioneventbackend/support/mysql/MySqlDatabaseCleanerIntegrationTest.java)
- 원시 결과·로그: 이 문서의 Before/After 표와 [조사 기록](../troubleshooting/2026-08-11-687-mysql-cleaner-disk-io.md)
- 관련 이슈·ADR: [#687](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/687), [#690](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/690), [ADR-0093](../adr/0093-use-tmpfs-for-testcontainers-mysql-data.md)
