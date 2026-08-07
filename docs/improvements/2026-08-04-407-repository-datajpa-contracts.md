# Repository DataJpaTest 계약 선별 정리

## 메타데이터

- 상태: 로컬 검증 완료, CI 확인 대기
- 개선 유형: 유지보수성, 안정성
- 범위: Repository·mapping·migration `@DataJpaTest` 19개의 DB 계약을 유지하면서, 영속성 컨텍스트가 필요 없는 객체 생성·상태 전이 검증을 단위 테스트로 이전한다.
- 관련 요구사항: [P0 명세의 테스트 및 출시 수용 기준](../p0-spec.md#9-테스트-및-출시-수용-기준)의 `AC-01`~`AC-18`, [내부 API 명세서](../api/api-specification.md), GitHub #407
- 관련 단계: 단계 1. MVP 구현·검증
- 기준 시각·시간대: 2026-08-04 KST
- Before revision: `10eaf661af2597fae4d2b18c6a9289e9209c0f64`
- After revision: `test/407-prune-repository-datajpa-contracts` 작업 트리
- 작업 트리 상태: 기준선 수집 전 변경 없음
- 환경: macOS arm64, OpenJDK 21, Gradle 9.5.1, Spring Boot 4.1.0, JaCoCo 0.8.14

## 개선 계약

### 개선 목표와 현재 위험

Repository 테스트에 순수 생성자 검증과 상태 전이 검증이 섞여 있으면 각 검증이 JPA 컨텍스트와 스키마 준비 비용을 함께 부담한다. DB 쿼리·projection·정렬·제약·지연 로딩·Flyway 계약은 그대로 유지하고, DB를 사용하지 않는 검증만 동일 assertion으로 단위 테스트에 둔다.

### 변경 전 정상 동작 근거

- `./gradlew --no-daemon clean build`가 성공했다.
- 대상은 Repository 테스트 18개와 `InitialP0SchemaMigrationTest` 1개다.
- 정적 `@Test`와 `@ParameterizedTest`를 DB 계약, mapping, query·projection, 제약, 순수 객체 검증으로 분류했다.

### 불변 조건

- 제품 코드, 공개 API, migration, Gradle·운영 설정을 변경하지 않는다.
- 조건부 update, 정렬, soft delete, fetch join, projection, 유일·CHECK·FK·복합 관계 제약, enum mapping, 지연 로딩, Flyway migration과 인덱스 검증을 유지한다.
- MySQL cleaner와 MySQL 서비스 테스트를 변경하지 않는다.
- 이전한 단위 테스트는 이전 전과 같은 입력과 assertion을 유지하며, 대형 통합 시나리오로 합치지 않는다.
- `@DataJpaTest` 클래스 수를 증가시키지 않는다.

### 지표와 합격 기준

| 지표 | 수집 방법 | 합격 기준 | 반복 횟수·요약 방식 |
| --- | --- | --- | --- |
| 전체 테스트 결과 | Gradle XML report | failure·error 0 | Before 1회, After 1회 |
| DB 계약 | 대상 테스트와 전체 build | 기존 DB 계약 모두 실행 | After 1회 |
| 라인 커버리지 | JaCoCo XML `LINE` counter | 90% 이상, Before 대비 covered 비감소 | Before·After exact counter 비교 |
| 브랜치 커버리지 | JaCoCo XML `BRANCH` counter | Before 대비 covered 비감소 | Before·After exact counter 비교 |
| JPA Context 수 | 대상 `@DataJpaTest` 클래스 수 | 증가 없음 | Before·After 파일 기준 비교 |

### 제외 범위

- 제품 코드·DB schema·migration·테스트 JVM 설정 변경
- 고유 DB 계약 삭제, 테스트 비활성화, MySQL cleaner와 MySQL 서비스 테스트 변경
- Docker가 필요한 테스트 실행 환경의 설치·설정 변경

## 재현 조건

- fixture·seed·데이터 크기: 대상 테스트가 현재 정의한 fixture와 Flyway schema를 그대로 사용한다.
- 외부 의존성 상태: 현재 작업 환경에는 Docker CLI가 없어 Testcontainers 의존 테스트가 Gradle에서 skip된다. Docker가 있는 CI에서 skip 없는 전체 검증을 확인한다.
- 동시성·요청 비율: 기존 테스트에 선언된 값을 변경하지 않는다.
- 준비·warm-up: 각 측정 전에 `clean`, 단일-use Gradle daemon을 사용한다.
- 측정 시간·반복 횟수: Before 1회, After 1회. 동일 명령으로 비교한다.

## Before

- 명령·입력: `/usr/bin/time -p ./gradlew --no-daemon clean build`
- 종료 코드: 0
- 정상 계약 검증: build와 JaCoCo report 생성 성공

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 893 tests, failure 0, error 0, skipped 111, 69.78초 | Docker CLI 부재로 Testcontainers 의존 테스트가 skip됨 |

- 라인: covered 6,538, missed 722, total 7,260, 90.0551%
- 브랜치: covered 1,441, missed 639, total 2,080, 69.2788%

## 변경 내용

Repository 테스트에서 DB를 사용하지 않는 생성자·상태 전이 검증 21개를 단위 테스트로 옮겼다. DB 저장·조회, query, projection, 정렬, 지연 로딩, 제약과 Flyway 검증은 대상 Repository·migration 테스트에 남겼다.

| 기존 Repository 테스트 | 이전한 순수 검증 | 단위 테스트 |
| --- | --- | --- |
| `AuditEventRepositoryTest` | enum 목록 2개 | `AuditEventCatalogTest` |
| `AppUserRepositoryTest` | null·blank·상태 입력값 3개 | `AppUserTest` |
| `OperatorApplicationRepositoryTest` | 승인·반려 상태 입력값 1개 | `OperatorApplicationTest` |
| `ImageObjectRepositoryTest` | 연결 상태 전이 3개, 생성자 입력값 6개 | `ImageObjectTest` |
| `CapacityHoldRepositoryTest` | 수량·상태 종결값 1개, 지역 일치 1개 | `CapacityHoldTest` |
| `ReservationRepositoryTest` | 지역·회차 일치 1개, 상태별 취소·만료값 1개 | `ReservationTest` |
| `ContentSessionRepositoryTest` | 시간 순서·정원 입력값 1개 | `ContentSessionTest` |
| `ContentRevisionRepositoryTest` | 심사·철회 상태 입력값 1개 | `ContentRevisionTest` |

| DB 계약 분류 | 유지한 대상 |
| --- | --- |
| 저장·조회, enum·지연 로딩 mapping | AuditEvent, ContentLog, Content, ContentRevision, ContentSession, IdempotencyRecord, ImageObject, OperatorApplication, Region, CapacityHold, Reservation, Review, AppUser, UserRegionRole, Visit |
| 조건부 update·soft delete·query·정렬·fetch join·projection | Content, ContentRevision, ContentSession, MyContent, PublicContent, UserRegionRole |
| 유일·CHECK·FK·복합 관계 제약 | Content, ContentRevision, SessionRevision, IdempotencyRecord, ImageObject, OperatorApplication, CapacityHold, Reservation, Review, AppUser, UserRegionRole, Visit |
| Flyway 빈 DB schema·인덱스 | `InitialP0SchemaMigrationTest` |

MySQL cleaner와 MySQL 서비스 테스트, 제품 코드, migration은 변경하지 않았다. 대상 Repository·migration 테스트 파일 수는 19개로 동일하고 `@DataJpaTest`를 추가하지 않았다.

## After

- 명령·입력: `/usr/bin/time -p ./gradlew --no-daemon clean build`
- 종료 코드: 0
- 정상 계약 검증: build와 JaCoCo report 생성 성공

| 반복 | 관찰값 | 비고 |
| --- | --- | --- |
| 1 | 892 tests, failure 0, error 0, skipped 111 | Docker CLI 부재로 Testcontainers 의존 테스트가 skip됨 |

- 라인: covered 6,538, missed 722, total 7,260, 90.0551%
- 브랜치: covered 1,441, missed 639, total 2,080, 69.2788%

## 비교와 판정

| 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 전체 결과 | 893 tests, 실패·오류 0 | 892 tests, 실패·오류 0 | 통과 |
| 대상 Repository·migration 테스트 파일 | 19개 | 19개 | 통과 |
| 라인 covered | 6,538 | 6,538 | 통과 |
| 브랜치 covered | 1,441 | 1,441 | 통과 |
| Testcontainers skip | 111 | 111 | CI 확인 필요 |

테스트 수가 1개 줄어든 이유는 `ContentRevisionRepositoryTest`의 독립 테스트 하나를 기존 `ContentRevisionTest`의 단위 계약 묶음에 같은 assertion으로 합쳤기 때문이다. DB 계약은 제거하지 않았다.

## 회귀·실패 경로 검증

- 이동한 16개 단위·Repository 테스트를 대상으로 한 Gradle 테스트가 통과했다.
- 전체 clean build가 통과했다.
- 테스트 이동 중 발견한 생성자 인자 순서·정적 import 컴파일 오류는 수정하고 같은 명령으로 다시 확인했다. 상세 기록은 [트러블슈팅 기록](../troubleshooting/2026-08-04-407-test-compilation.md)에 남겼다.

## 한계와 잔여 위험

- 현재 로컬 환경에서는 Docker CLI가 없어 Testcontainers 기반 테스트 111개의 skip 없는 실행을 재현할 수 없다. PR CI에서 확인한다.
- Before 브랜치 커버리지가 90% 미만이다. 이번 변경 후 covered 수는 감소하지 않았으며, 실제 품질 게이트 결과는 PR CI에서 확인한다.

## 증거 링크

- 관련 테스트: [`src/test/java`](../../src/test/java)
- 원시 결과·로그: `build/test-results/test`, `build/reports/tests/test`, `build/reports/jacoco/test`
- 관련 이슈·ADR: GitHub #407, #389, 기존 개선 기록 [#322](2026-08-03-322-test-case-reduction.md)
