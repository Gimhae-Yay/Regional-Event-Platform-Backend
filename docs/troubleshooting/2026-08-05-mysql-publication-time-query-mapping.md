# MySQL 자동 공개 시각 쿼리 매핑 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | MySQL에서 자동 공개 처리 시각 조회가 예외로 실패해 콘텐츠 공개가 완료되지 않는다. |
| 최초 확인 시각·시간대 | 2026-08-05 17:40 KST |
| 관련 요구사항·이슈 | #380 승인 콘텐츠 자동 공개, PR #431 MySQL 쿼리 회귀 검증 |
| revision·브랜치 | `873c6d41`, `feature/380-approved-content-publication-scheduler` |
| 환경·프로필 | Java 21, 기본 프로필, Docker 29.5.3, Testcontainers MySQL 8.0.42 |

## 기대 결과와 실제 결과

### 기대 결과

자동 공개 후보 조회와 잠금 시점 재확인은 MySQL 현재 시각을 기준으로 동작하며, 자동 공개 성공 감사의
`occurredAt`에 사용할 MySQL 시각을 `Instant`로 조회한다.

### 실제 결과

`SELECT CURRENT_TIMESTAMP(6)` native 쿼리의 MySQL 결과는 `LocalDateTime`으로 매핑됐다. 저장소 메서드가
`Instant`를 선언해 런타임에 `ClassCastException`이 발생했다.

## 재현 절차

### 선행 조건

- Docker 실행 가능
- MySQL 8.0.42 Testcontainers 이미지 사용 가능

### 명령·요청·입력

1. `./gradlew test --tests io.regionevent.regioneventbackend.domain.content.repository.ContentRepositoryMySqlTest --console=plain`을 실행한다.
2. MySQL 현재 시각으로 과거·미래 공개 예정 콘텐츠를 생성하고 자동 공개 후보 조회와 잠금 대상 조회를 실행한다.

### 재현 결과

- 실행 횟수: 1회
- 성공 횟수: 0회
- 실패 횟수: 2회
- 종료 코드·HTTP 상태: 종료 코드 1, 두 테스트 모두 `ClassCastException`

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- MySQL Testcontainers에서 `ContentRepository.findCurrentDatabaseTime()` 호출 결과가
  `java.time.LocalDateTime cannot be cast to java.time.Instant`로 실패했다.
- `ContentRepository`는 `SELECT CURRENT_TIMESTAMP(6)` native 쿼리의 반환형을 `Instant`로 선언했다.
- 예약 도메인은 동일한 MySQL 시각 조회에 `UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))`의 `BigDecimal` 결과를
  `Instant`로 변환하는 방식을 이미 사용한다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-05 17:40 KST | 관찰 | MySQL Testcontainers 저장소 테스트 실행 | 후보·잠금 쿼리가 통과한다 | 시각 조회에서 두 테스트 모두 실패 | 채택 |
| 2026-08-05 17:41 KST | 가설 | Docker 또는 컨테이너 기동 실패 | MySQL 연결 실패가 발생한다 | MySQL 8.0.42 컨테이너와 Flyway migration이 정상 기동했다 | 기각 |
| 2026-08-05 17:42 KST | 가설 | native 시각 결과 타입과 저장소 반환형이 다르다 | `LocalDateTime → Instant` 캐스팅 예외가 발생한다 | 테스트 스택 트레이스와 저장소 선언이 일치한다 | 채택 |
| 2026-08-05 17:43 KST | 변경 | epoch seconds 기반 조회·변환으로 교체 | MySQL 통합 테스트와 단위 테스트가 통과한다 | 대상 테스트 통과 | 채택 |
| 2026-08-05 17:46 KST | 검증 | 전체 빌드 실행 | 전체 회귀가 통과한다 | `./gradlew cleanTest build --no-daemon` 통과 | 채택 |

## 가설과 검증

### 가설 1: MySQL native timestamp 결과가 `Instant`로 직접 매핑되지 않는다

- 근거: MySQL 통합 테스트에서 반환된 실제 타입이 `LocalDateTime`이다.
- 참일 때의 예측: `Instant` 반환형 저장소 메서드 호출에서 캐스팅 예외가 발생한다.
- 반증 조건: MySQL에서 동일 메서드가 `Instant`를 반환한다.
- 검증 방법: Testcontainers MySQL에서 저장소 메서드를 호출한다.
- 결과: 두 자동 공개 쿼리 회귀 테스트가 같은 캐스팅 예외로 실패했다.
- 판정: 채택.

## 근본 원인

- 촉발 조건: MySQL에서 자동 공개 처리 시각을 조회한다.
- 결함이 있는 코드·설정·데이터·계약: `ContentRepository.findCurrentDatabaseTime()`이 native
  `CURRENT_TIMESTAMP(6)` 결과를 `Instant`로 직접 선언했다.
- 증상으로 이어진 메커니즘: MySQL JDBC/Hibernate가 결과를 `LocalDateTime`으로 전달하고 Spring Data 프록시가
  이를 `Instant`로 캐스팅하면서 예외가 발생한다.
- 기존 방어가 막지 못한 이유: 자동 공개 저장소 테스트가 H2 기반이어서 MySQL 결과 타입 매핑 차이를 검증하지 못했다.
- 결론의 증거: MySQL Testcontainers 재현 테스트의 `ClassCastException`과 동일 예약 도메인의 epoch seconds 변환 구현.

## 해결 또는 완화

- 선택한 방법: MySQL 시각 조회를 `UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))`의 `BigDecimal`으로 받고,
  `ContentService`에서 초·나노초 단위의 `Instant`로 변환한다.
- 변경 파일:
  - `src/main/java/io/regionevent/regioneventbackend/domain/content/repository/ContentRepository.java`
  - `src/main/java/io/regionevent/regioneventbackend/domain/content/service/ContentService.java`
  - `src/test/java/io/regionevent/regioneventbackend/domain/content/repository/ContentRepositoryMySqlTest.java`
  - `src/test/java/io/regionevent/regioneventbackend/domain/content/service/ContentServiceTest.java`
- 정책·계약 변경 여부: 없음. MySQL 현재 시각을 기준으로 처리한다는 기존 계약을 실제 MySQL에서 보장한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| MySQL 자동 공개 저장소 테스트 | 2개 테스트 모두 `ClassCastException` | 후보 필터·잠금 재확인 모두 통과 | 통과 |
| MySQL 시각의 `Instant` 변환 | 직접 매핑 실패 | epoch seconds를 초·나노초로 변환 | 통과 |
| 전체 회귀 | 미실행 | `./gradlew cleanTest build --no-daemon` 성공 | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests io.regionevent.regioneventbackend.domain.content.repository.ContentRepositoryMySqlTest --console=plain` | 성공 | Testcontainers MySQL 8.0.42에서 후보 필터·잠금 재확인 검증 |
| `./gradlew test --tests io.regionevent.regioneventbackend.domain.content.service.ContentServiceTest --tests io.regionevent.regioneventbackend.domain.content.repository.ContentRepositoryMySqlTest --console=plain` | 성공 | MySQL 시각 변환 단위·통합 검증 |
| `./gradlew cleanTest build --no-daemon --console=plain` | 성공 | 전체 회귀 |

## 재발 방지와 문서 반영

- MySQL 함수 또는 잠금 동작에 의존하는 새 저장소 쿼리는 H2 테스트와 별도로 MySQL Testcontainers 회귀 테스트를 둔다.

## 잔여 위험과 후속 작업

- 실제 다중 인스턴스 간 자동 공개 경합 검증은 #380에서 분리한 후속 범위다.

## 관련 자료

- PR #431
- 이슈 #380
- `docs/api/p0/content-catalog/publish-approved-contents.md`
