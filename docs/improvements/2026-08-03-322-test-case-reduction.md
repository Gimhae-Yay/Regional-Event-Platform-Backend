# 테스트 케이스 축소와 회귀 계약 보존

## 메타데이터

- 상태: 완료
- 개선 유형: 유지보수성, 안정성
- 범위: 중복 테스트 제거, 순수 단위 시나리오의 계약 테스트 통합, JaCoCo 측정 자동화
- 관련 요구사항: [PRD 기능 요구사항과 테스트 수용 기준](../local-stamp-platform-prd.md#7-기능-요구사항)의 `FR-01`~`FR-14`, `AC-01`~`AC-18`
- 관련 단계: 단계 1. MVP 구현·검증
- 기준 시각·시간대: 2026-08-03 14:50~15:04 KST
- Before revision: `f43dfb962b0edca2dbc5a4fa4db183a783aaf080`
- 측정 도구 revision: `3e2717b`의 JaCoCo 설정. 테스트 코드는 Before revision과 동일하다.
- After revision: `c1cb0fd`
- 작업 트리 상태: Before는 JaCoCo 설정만 적용, After는 커밋된 테스트 변경 기준
- 환경: macOS 26.5.2 arm64, OpenJDK 21.0.7, Gradle 9.5.1, Spring Boot 4.1.0,
  MySQL `8.0.42`, Redis `7.4-alpine`, JaCoCo 0.8.14, Testcontainers

## 개선 계약

### 개선 목표와 현재 위험

기준 테스트 715개에는 동일 코드 경로와 결과를 실제 MySQL 테스트와 중복 검증하는 3개 테스트와,
입력만 달리해 같은 계약을 반복 실행하는 순수 단위 테스트가 포함돼 있었다. 테스트 개수를 600개
이하로 줄이되 assertion, 입력 조합과 실행 분기를 유지하고, API·Repository·MySQL·Redis 테스트의
격리 경계는 바꾸지 않는다.

단순히 JUnit 실행 수만 줄이기 위해 고유 시나리오를 삭제하거나 테스트를 비활성화하면 핵심 회귀를
놓칠 수 있다. 따라서 제거는 더 강한 실제 MySQL 검증이 존재하는 완전 중복 3개로 제한하고,
나머지는 `assertAll`이 모든 기존 시나리오를 실행하는 계약 테스트로 통합한다.

### 변경 전 정상 동작 근거

- 기준 revision에서 `./gradlew --no-daemon clean build`가 성공했다.
- 테스트 715개 모두 성공했고 failure, error, skipped는 각각 0이었다.
- JaCoCo 기준 라인 4,591/4,896, 브랜치 1,035/1,445를 실행했다.
- 실제 MySQL·Redis 컨테이너를 사용한 핵심 테스트가 포함됐다.

### 불변 조건

- 제품 코드, 공개 API, DB schema, 운영·개발 설정을 변경하지 않는다.
- 기존 단위 테스트의 입력, assertion과 코드 분기 실행을 모두 유지한다.
- 각 계약 시나리오는 새 테스트 클래스 인스턴스에서 실행해 기존 JUnit 기본 격리를 보존한다.
- Controller와 Repository 테스트는 요청·트랜잭션별 격리가 필요한 기존 JUnit 케이스를 유지한다.
- MySQL·Redis 실제 동작, 동시성·락, 트랜잭션 원자성·롤백, DB 제약, 인증·인가,
  멱등성·재시도 테스트를 유지한다.
- 실패한 실행도 다음 시나리오 실행을 막지 않도록 `assertAll`을 사용한다.

### 지표와 합격 기준

| 지표 | 수집 방법 | 합격 기준 | 반복 횟수·요약 방식 |
| --- | --- | --- | --- |
| 전체 테스트 수 | Gradle XML·HTML report | 600개 이하 | 전체 빌드마다 합계 |
| 테스트 결과 | XML의 failures, errors, skipped | 모두 0 | Before 1회, After 2회 |
| 라인 커버리지 | JaCoCo XML `LINE` counter | covered 감소 없음 | Before와 After exact counter 비교 |
| 브랜치 커버리지 | JaCoCo XML `BRANCH` counter | covered 감소 없음 | Before와 After exact counter 비교 |
| 격리 안정성 | 동일한 `clean build` 반복 | 2회 연속 성공 | 개별 실행시간과 결과 기록 |
| 빌드 실행시간 | `/usr/bin/time -p`의 real | 수치 기록, 성능 개선으로 판정하지 않음 | Before 1회, After 2회 개별값 |

### 제외 범위

- 제품 코드, API, migration, 운영·개발 설정 변경
- 새 테스트 라이브러리, 테스트 비활성화, 무작위 실행 도입
- Controller·Repository 테스트의 광범위한 통합
- 테스트 수 감소를 위한 고유 회귀 계약 삭제

## 재현 조건

- fixture·seed·데이터 크기: 저장소 테스트 fixture와 migration을 그대로 사용한다.
- 외부 의존성 상태: 로컬 Docker에서 MySQL `8.0.42`와 Redis `7.4-alpine`을 Testcontainers로 실행한다.
- 동시성·요청 비율: 기존 테스트에 선언된 스레드·요청 수를 변경하지 않는다.
- 준비·warm-up: 매 반복에서 Gradle `clean`과 단일-use daemon을 사용한다.
- 측정 시간·반복 횟수: Before 1회, After 2회. 각 반복은 독립 `clean build`다.
- 명령: `/usr/bin/time -p ./gradlew --no-daemon clean build`

## Before

- 명령·입력: `/usr/bin/time -p ./gradlew --no-daemon clean build`
- 종료 코드: 0
- 정상 계약 검증: MySQL·Redis 포함 전체 테스트 성공, JaCoCo report 생성

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 715 tests, failure 0, error 0, skipped 0, 136.43초 | XML suite 119개, 테스트 소스 118개 |

- 라인: covered 4,591, missed 305, total 4,896, 93.7704%
- 브랜치: covered 1,035, missed 410, total 1,445, 71.6263%

## 변경 내용

### 완전 중복 제거와 유지 대상

| 제거한 테스트 | 유지 대상 테스트 | 보존 계약 |
| --- | --- | --- |
| `OperatorApplicationControllerIntegrationTest.reapply_concurrently_createsOnePendingApplicationAndReturnsPendingConflict` | `OperatorApplicationControllerMySqlIntegrationTest.reapplyConcurrently_createsOnePendingApplicationAndReturnsPendingConflict` | 재신청 동시 요청 중 한 건만 생성되고 나머지는 pending 충돌 |
| `VisitReviewControllerIntegrationTest.createReview_concurrentRequestsCreateOnlyOneReview` | `CreateVisitReviewUseCaseMySqlIntegrationTest`의 동일 방문 동시 생성 테스트 | 실제 MySQL unique 제약에서 방문당 후기 한 건 |
| `ContentSessionControllerIntegrationTest.회차_예약정보_조회_MySQL_현재_시각이_시작_시각과_같거나_지난_경우_예약_불가를_반환한다` | `ContentSessionReservationInfoMySqlIntegrationTest.회차_예약정보_조회_MySQL_현재_시각과_시작_시각이_같으면_예약_불가를_반환한다` | MySQL 세션 시각을 고정한 시작 시각 경계 |

### 단위 계약 통합 대응표

각 행의 After 테스트는 새 클래스 인스턴스에서 기존 메서드를 모두 호출한다. 파라미터화 테스트는
기존 enum·상태 조합과 오류 코드 목록을 내부 `assertAll` 실행 목록으로 유지한다.

| 테스트 클래스 | Before | After | 보존한 검증 요구사항 |
| --- | ---: | ---: | --- |
| `ApiResponseTest` | 20 | 1 | 성공 응답과 공개 오류 코드 14종, 헤더, 생성자 불변식 |
| `ContentRevisionTest` | 10 | 1 | 승인·반려와 종결 상태 3종 조합, 심사 정보 불변식 |
| `ContentRevisionReviewTypePolicyTest` | 28 | 1 | 유효 조합 2개와 나머지 모든 상태 조합의 정합성 거부 |
| `JwtAccessTokenServiceTest` | 11 | 1 | 발급, 만료, profile, issuer·audience, 서명·키 회전 |
| `QrTokenServiceTest` | 10 | 1 | QR 발급, TTL, 버전·형식·키·서명·만료 실패 |
| `S3ImageStorageClientTest` | 8 | 1 | presign·metadata·delete 요청과 SDK 실패 변환 |
| `RegionAdminAuthorizationServiceTest` | 8 | 1 | 사용자·상태·역할·지역 경계와 정상 인가 |
| `ContentSessionTest` | 8 | 1 | 생성·승인·반려·완료·취소·정원 복구·상태 거부 |
| `RefreshTokenServiceTest` | 6 | 1 | 회전, 충돌, 무효 토큰, 보상 취소, 저장소 장애 |
| `ContentTest` | 6 | 1 | 승인·반려 상태 전이와 soft-delete 보호 |
| `AuditEventCommandTest` | 6 | 1 | 성공·실패 감사 필수값과 미확정 값 허용 범위 |
| `GetPendingContentRevisionsUseCaseTest` | 5 | 1 | 정렬·빈 목록·입력 거부·이미지 발급 전 정합성 |
| `GetOriginalContentReviewDetailUseCaseTest` | 4 | 1 | 원본 상세, 지역 권한, 비노출, 이미지 지역 연결 |
| `GetContentRevisionReviewDetailUseCaseTest` | 4 | 1 | 수정본 상세, 지역 권한, 이미지·상태 정합성 |
| `JwtRefreshTokenServiceTest` | 4 | 1 | refresh profile, 기간, 이전 키 거부 |
| `RefreshAccessTokenControllerTest` | 4 | 1 | 정상 회전, 누락, 회전 충돌, Redis 장애 응답 |
| 합계 | 142 | 16 | 기존 입력·assertion·분기 실행 유지 |

### API와 Repository 경계

| 구분 | 판단 | 결과 |
| --- | --- | --- |
| API 공통 인증·입력 오류 | 동일 endpoint 안에서도 status·error code와 부수효과가 달라 개별 케이스 유지 | Controller 파라미터화와 endpoint 고유 테스트 유지 |
| API endpoint 고유 계약 | 응답 필드, 상태와 저장 결과가 endpoint별로 다름 | 개별 통합 테스트 유지 |
| Repository 매핑 | enum·연관관계·정렬·쿼리 결과를 검증 | 기존 테스트와 트랜잭션 롤백 유지 |
| DB 제약 | unique·CHECK·FK와 MySQL native SQL 결과를 검증 | 파라미터 호출과 테스트별 트랜잭션 격리 유지 |

## After

- 명령·입력: `/usr/bin/time -p ./gradlew --no-daemon clean build`
- 종료 코드: 두 반복 모두 0
- 정상 계약 검증: 두 반복 모두 MySQL·Redis 포함 전체 테스트와 JaCoCo report 생성 성공

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 586 tests, failure 0, error 0, skipped 0, 137.63초 | 목표 600개 이하 충족 |
| 2 | 586 tests, failure 0, error 0, skipped 0, 142.12초 | 순서·공유 상태 오염 없음 |

- 라인: covered 4,591, missed 305, total 4,896, 93.7704%
- 브랜치: covered 1,035, missed 410, total 1,445, 71.6263%

## 비교와 판정

| 항목 | Before | After | 변화량 | 판정 |
| --- | --- | --- | --- | --- |
| 테스트 수 | 715 | 586 | -129, -18.0% | 목표 충족 |
| 라인 covered | 4,591 | 4,591 | 0 | 비감소 |
| 브랜치 covered | 1,035 | 1,035 | 0 | 비감소 |
| 실패·오류·스킵 | 0·0·0 | 0·0·0 | 0 | 정상 |
| 전체 빌드 | 1/1 성공 | 2/2 성공 | 반복 성공 | 격리 유지 |
| 실행시간 | 136.43초 | 137.63초, 142.12초 | +1.20초, +5.69초 | 성능 개선으로 판정하지 않음 |

테스트 수와 중복 유지비용을 줄이면서 라인·브랜치 실행 수와 핵심 회귀 계약을 그대로 유지했으므로
유지보수성 개선으로 판정한다. 로컬 실행시간은 개선되지 않았으므로 성능 개선으로 표현하지 않는다.

## 회귀·실패 경로 검증

- 통합한 16개 단위 계약과 3개 MySQL 유지 대상을 선별 실행해 성공했다.
- 전체 빌드 2회에서 MySQL·Redis·동시성·트랜잭션·DB 제약·보안·멱등성 테스트가 모두 실행됐다.
- Controller와 Repository 파라미터화 테스트 8개는 테스트별 격리와 실패 위치를 보존하기 위해 유지했다.
- JaCoCo line·branch의 covered와 missed exact counter가 Before와 After에서 동일하다.
- `git diff --check`를 통과했다.

## 한계와 잔여 위험

- 실행시간은 로컬 macOS 환경의 wall-clock이며 GitHub Actions runner의 실제 CI 시간은 PR 검사에서 별도로 확인한다.
- 계약 테스트 하나 안의 여러 실패는 `assertAll`로 모두 보고되지만 Gradle의 최상위 테스트 수에는 1개로 집계된다.
- JaCoCo는 커버한 분기의 의미적 assertion 강도를 증명하지 않으므로 기존 assertion을 삭제하지 않았다.

## 증거 링크

- 관련 설정: [`build.gradle`](../../build.gradle)
- 중복 제거: [`OperatorApplicationControllerIntegrationTest`](../../src/test/java/io/regionevent/regioneventbackend/domain/operator/controller/OperatorApplicationControllerIntegrationTest.java),
  [`VisitReviewControllerIntegrationTest`](../../src/test/java/io/regionevent/regioneventbackend/domain/review/controller/VisitReviewControllerIntegrationTest.java),
  [`ContentSessionControllerIntegrationTest`](../../src/test/java/io/regionevent/regioneventbackend/domain/content/controller/ContentSessionControllerIntegrationTest.java)
- 유지 대상: [`OperatorApplicationControllerMySqlIntegrationTest`](../../src/test/java/io/regionevent/regioneventbackend/domain/operator/controller/OperatorApplicationControllerMySqlIntegrationTest.java),
  [`CreateVisitReviewUseCaseMySqlIntegrationTest`](../../src/test/java/io/regionevent/regioneventbackend/domain/review/service/CreateVisitReviewUseCaseMySqlIntegrationTest.java),
  [`ContentSessionReservationInfoMySqlIntegrationTest`](../../src/test/java/io/regionevent/regioneventbackend/domain/content/controller/ContentSessionReservationInfoMySqlIntegrationTest.java)
- 원시 결과·로그: `build/test-results/test`, `build/reports/tests/test`, `build/reports/jacoco/test`
  (동일 명령으로 재생성되는 build 산출물이며 커밋하지 않는다.)
- 관련 이슈·PR: GitHub #322, 기준 PR #319
