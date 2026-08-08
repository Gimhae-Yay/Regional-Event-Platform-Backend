# CONTENT_SET 미션 저장 예외 경계

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | 빈 대상 콘텐츠의 `CONTENT_SET` 미션을 막는 JPA lifecycle 검증의 영속성 테스트가 잘못된 예외 타입을 기대해 실패한다. |
| 최초 확인 시각·시간대 | 2026-08-08 19:25 KST |
| 관련 요구사항·이슈 | #589, P1 지역 미션 `MSN-01` |
| revision·브랜치 | `17349213`, `feature/589-regional-mission-definition-persistence` |
| 환경·프로필 | Gradle 9.5.1, Java 21.0.7, H2 MySQL mode, `spring.jpa.hibernate.ddl-auto=validate` |

## 기대 결과와 실제 결과

### 기대 결과

`MissionRepository.saveAndFlush`로 대상 없는 `CONTENT_SET` 미션을 저장하면 JPA lifecycle 검증에 의해 저장이 거부되고, 회귀 테스트는 그 영속 경계의 예외를 확인해야 한다.

### 실제 결과

검증 자체는 실행됐지만 Spring Data JPA가 `@PrePersist`의 `IllegalStateException`을 `InvalidDataAccessApiUsageException`으로 변환했다. 테스트가 내부 예외 타입을 직접 기대해 실패했다.

## 재현 절차

### 선행 조건

- `Mission`에 `@PrePersist` 대상 콘텐츠 검증이 추가된 상태

### 명령·요청·입력

1. `./gradlew test --tests io.regionevent.regioneventbackend.domain.mission.repository.MissionRepositoryTest`를 실행한다.
2. 대상 없는 `CONTENT_SET` 미션을 `MissionRepository.saveAndFlush`로 저장하는 테스트 결과를 확인한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle test 종료 코드 1, HTTP 요청 없음

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- `MissionRepositoryTest`의 실패 예외는 `InvalidDataAccessApiUsageException`이며 메시지는 `CONTENT_SET requires at least one target content`다.
- 예외 체인은 `@PrePersist`에서 발생한 `IllegalStateException`을 포함한다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-08 19:25 KST | 관찰 | 미션 영속성 테스트 실행 | 빈 대상 `CONTENT_SET` 저장이 거부된다. | 저장이 거부됐지만 직접 예외 타입 기대가 실패 | 재현 |
| 2026-08-08 19:26 KST | 검증 | 실패 XML의 예외 체인 확인 | Spring Data JPA가 lifecycle 예외를 변환했다면 wrapper 예외가 나타난다. | `InvalidDataAccessApiUsageException` 확인 | 채택 |
| 2026-08-08 19:27 KST | 변경·검증 | repository 경계 예외와 원인 예외를 함께 검증 | 대상 미션 영속성 테스트가 통과한다. | 통과 | 해결 후보 |
| 2026-08-08 19:28 KST | 검증 | 전체 build 실행 | 전체 빌드와 테스트가 통과한다. | 통과 | 해결 |

## 가설과 검증

### 가설 1: lifecycle 검증이 실행되지 않았다

- 근거: 테스트가 실패했다.
- 참일 때의 예측: 저장 SQL이 실행되고 대상 없는 미션 행이 남는다.
- 반증 조건: 저장 전에 검증 메시지를 가진 예외가 발생한다.
- 검증 방법: 실패 예외 메시지와 타입을 확인한다.
- 결과: `CONTENT_SET requires at least one target content` 메시지를 가진 예외가 발생했다.
- 판정: 기각.

### 가설 2: Spring Data JPA가 lifecycle 예외를 repository 경계 예외로 변환했다

- 근거: 실제 예외 타입이 `InvalidDataAccessApiUsageException`이다.
- 참일 때의 예측: wrapper의 cause가 `IllegalStateException`이다.
- 반증 조건: 원인이 다른 영속성 또는 SQL 예외다.
- 검증 방법: 실패 예외 체인을 확인한다.
- 결과: wrapper가 lifecycle 검증 예외를 보존한다.
- 판정: 채택.

## 근본 원인

- 촉발 조건: repository의 `saveAndFlush` 경로로 JPA lifecycle callback 예외를 발생시켰다.
- 결함이 있는 코드·설정·데이터·계약: 테스트가 repository 경계에서 변환되는 Spring 예외 대신 내부 lifecycle 예외를 직접 기대했다.
- 증상으로 이어진 메커니즘: Spring Data JPA가 `@PrePersist` 예외를 `InvalidDataAccessApiUsageException`으로 변환해 반환한다.
- 기존 방어가 막지 못한 이유: 새 영속성 테스트가 repository 경계의 예외 변환을 고려하지 않았다.
- 결론의 증거: 실패한 테스트 XML의 실제 예외 타입·메시지와 cause 체인.

## 해결 또는 완화

- 선택한 방법: repository 저장 테스트는 Spring 예외 타입과 메시지, 원인 `IllegalStateException`을 함께 검증한다.
- 변경 파일: `Mission`, `MissionRepositoryTest`
- 정책·계약 변경 여부: 없음.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 대상 없는 `CONTENT_SET` 저장 테스트 | 내부 예외 타입 기대 실패 | 저장 거부와 예외 경계 검증 통과 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests io.regionevent.regioneventbackend.domain.mission.repository.MissionRepositoryTest` | 통과 | 대상 없는 `CONTENT_SET` 저장 거부와 기존 미션 영속성 검증 |
| `./gradlew build` | 통과 | 전체 회귀 검증 |

## 재발 방지와 문서 반영

Repository를 거치는 lifecycle 검증은 Spring Data JPA가 노출하는 예외 경계와 원인을 함께 검증한다.

## 잔여 위험과 후속 작업

없음.

## 관련 자료

- `src/main/java/io/regionevent/regioneventbackend/domain/mission/entity/Mission.java`
- `src/test/java/io/regionevent/regioneventbackend/domain/mission/repository/MissionRepositoryTest.java`
