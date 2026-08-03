# JVM 공유 MySQL 테스트 컨테이너 전환

## 메타데이터

- 상태: 검증 중
- 개선 유형: 성능, 안정성, 유지보수성
- 범위: Issue #299의 MySQL 통합 테스트 15개와 `dev`에서 추가 확인한 3개
- 관련 요구사항: [FR-01~FR-11과 AC-01~AC-18](../local-stamp-platform-prd.md#12-테스트-및-출시-수용-기준)
- 관련 단계: 단계 1. MVP 구현·검증
- 기준 시각·시간대: 2026-08-03, Asia/Seoul
- Before revision: `ac74e5c5e15f2d54040a5a7c7ce5f3b34a89d283`
- After revision: 검증 후 기록
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

GitHub Actions 단일 측정에는 실행기 편차가 있으므로 절대 성능 보장으로 해석하지 않고, 컨테이너
시작 횟수와 함께 개선 여부를 판정한다.

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
- 측정 시간·반복 횟수: Before/After GitHub Actions 각 1회, 로컬 After 2회

## Before

- 명령·입력: `./gradlew --no-daemon clean build`
- 종료 코드: 0
- 정상 계약 검증: 전체 700개 테스트 성공, skipped·failure·error 0건

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 빌드 365초, 전체 job 390초, MySQL 시작 19회, MySQL 테스트 229.859초 | GitHub Actions run 30784432199 |

## 변경 내용

구현 후 기록한다.

## After

- 명령·입력: `./gradlew --no-daemon clean build`
- 종료 코드: 검증 후 기록
- 정상 계약 검증: 검증 후 기록

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 검증 전 |  |

## 비교와 판정

| 항목 | Before | After | 변화량 | 판정 |
| --- | --- | --- | --- | --- |
| MySQL 컨테이너 시작 횟수 | 19회 | 검증 전 | 계산 전 | 검증 중 |
| 전체 CI 실행시간 | 빌드 365초, job 390초 | 검증 전 | 계산 전 | 검증 중 |
| MySQL 테스트 시간 | 229.859초 | 검증 전 | 계산 전 | 검증 중 |

## 회귀·실패 경로 검증

검증 후 기록한다.

## 한계와 잔여 위험

- GitHub Actions Before/After는 각 1회 측정이므로 실행기 편차를 완전히 제거하지 못한다.

## 증거 링크

- 관련 코드·테스트: [`src/test/java`](../../src/test/java)
- 원시 결과·로그: [Before GitHub Actions run 30784432199](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30784432199)
- 관련 이슈·ADR: [Issue #299](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/299)
