# dev 병합 뒤 플랫폼 관리자 스키마 누락

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #597이 최신 `dev`와 병합될 때 P0 스키마 검증이 실패하고, 플랫폼 관리자 영속 코드·마이그레이션이 PR에서 삭제되는 diff가 발생한다. |
| 최초 확인 시각·시간대 | 2026-08-08 18:47 KST |
| 관련 요구사항·이슈 | PR #597, #589, P1 전체관리자 영속 기반 |
| revision·브랜치 | `c6f48be5`, `feature/589-regional-mission-definition-persistence` |
| 환경·프로필 | Gradle 9.5.1, Java 21, H2 MySQL mode, `spring.jpa.hibernate.ddl-auto=validate` |

## 기대 결과와 실제 결과

### 기대 결과

최신 `dev`를 병합한 PR #597은 V1부터 V19까지의 기존 마이그레이션과 미션 마이그레이션을 함께 적용하고, 플랫폼 관리자·미션·스탬프북 스키마를 모두 검증해야 한다.

### 실제 결과

`InitialP0SchemaMigrationTest`가 `PLATFORM_ADMIN_ASSIGNMENT` 테이블 부재로 실패했다. 현재 브랜치와 `origin/dev`의 diff에서도 플랫폼 관리자 엔티티·마이그레이션·테스트가 삭제로 나타났다.

## 재현 절차

### 선행 조건

- `feature/589-regional-mission-definition-persistence` 브랜치
- 최신 `origin/dev`를 병합해 `InitialP0SchemaMigrationTest` 충돌을 해소한 상태

### 명령·요청·입력

1. `./gradlew build`를 실행한다.
2. `InitialP0SchemaMigrationTest` 결과를 확인한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle build 종료 코드 1, HTTP 요청 없음

## 수집한 증거

비밀값, 개인정보, JWT·QR 원문과 결제 키를 포함하지 않는다.

- `InitialP0SchemaMigrationTest`는 `PLATFORM_ADMIN_ASSIGNMENT`를 기대하지만 생성된 H2 스키마에는 해당 테이블이 없다.
- `git show --name-status 0c823e3c`는 이전 `dev` 병합을 되돌리면서 플랫폼 관리자 엔티티·V18 마이그레이션·테스트를 삭제한 사실을 보인다.
- `git diff --name-status origin/dev -- ...`는 현재 PR 브랜치가 최신 `dev` 기준으로 같은 플랫폼 관리자 파일을 삭제하는 diff를 보인다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-08 18:47 KST | 관찰 | `./gradlew build` 실행 | 병합 결과의 전체 스키마 검증이 통과한다. | `PLATFORM_ADMIN_ASSIGNMENT` 부재로 실패 | 재현 |
| 2026-08-08 18:49 KST | 검증 | 병합 되돌리기 커밋과 `origin/dev` diff 확인 | 되돌린 파일이 현재 PR에서 삭제로 남아 있으면 원인이다. | 플랫폼 관리자 코드·V18 migration·테스트의 삭제를 확인 | 채택 |
| 2026-08-08 18:50 KST | 변경 | 최신 `dev`의 플랫폼 관리자 기반을 복원하고 미션 migration을 V20으로 이동 | V18~V20을 모두 적용한 스키마 검증이 통과한다. | 대상 테스트 통과 | 해결 후보 |
| 2026-08-08 18:51 KST | 검증 | 병합 결과 전체 build 실행 | 전체 빌드와 테스트가 통과한다. | 통과 | 해결 |

## 가설과 검증

### 가설 1: 충돌 해소 시 플랫폼 관리자 기대값만 잘못 추가됐다

- 근거: 최초 실패는 테이블 기대값 불일치다.
- 참일 때의 예측: 최신 `dev`에는 플랫폼 관리자 스키마가 존재하지 않는다.
- 반증 조건: 최신 `dev`에 플랫폼 관리자 V18 마이그레이션과 엔티티가 존재한다.
- 검증 방법: `origin/dev`의 V18 마이그레이션과 플랫폼 관리자 파일을 확인한다.
- 결과: V18 플랫폼 관리자 마이그레이션과 관련 코드·테스트가 모두 존재한다.
- 판정: 기각.

### 가설 2: 이전 `dev` 병합을 되돌린 커밋이 최신 `dev` 파일을 PR에서 삭제했다

- 근거: `0c823e3c`가 플랫폼 관리자 파일과 V18 마이그레이션 삭제를 포함한다.
- 참일 때의 예측: 최신 `dev`와의 diff에서 같은 파일이 삭제로 나타난다.
- 반증 조건: 해당 파일이 최신 `dev`에도 없거나 현재 diff에서 삭제가 아니다.
- 검증 방법: `git diff --name-status origin/dev`로 비교한다.
- 결과: 플랫폼 관리자 파일과 V18 migration이 삭제로 나타났다.
- 판정: 채택.

## 근본 원인

- 촉발 조건: 이전 `dev` 병합을 되돌린 `0c823e3c` 이후 최신 `dev`를 다시 병합했다.
- 결함이 있는 코드·설정·데이터·계약: 되돌리기 커밋이 최신 `dev`의 플랫폼 관리자 영속 기반을 삭제 상태로 유지했고, 병합 자동 해소는 그 삭제를 복구하지 않았다.
- 증상으로 이어진 메커니즘: 테스트 기대값은 최신 `dev`의 플랫폼 관리자 스키마를 포함하지만, 적용할 V18 migration이 브랜치에 없으므로 H2 스키마에 테이블이 생성되지 않았다.
- 기존 방어가 막지 못한 이유: PR 병합 가능 상태만 확인했고 병합 결과 전체 build를 실행하지 않았다.
- 결론의 증거: 실패한 테스트, 되돌리기 커밋의 파일 목록, 최신 `dev`와의 삭제 diff가 일치한다.

## 해결 또는 완화

- 선택한 방법: 진행 중인 병합에서 최신 `dev`의 플랫폼 관리자 파일을 복원하고, 미션 마이그레이션을 기존 V18·V19 뒤의 V20으로 이동했다.
- 변경 파일: V18 플랫폼 관리자 migration, V19 스탬프북 migration, V20 미션 migration, `InitialP0SchemaMigrationTest`
- 정책·계약 변경 여부: 없음. 기존 마이그레이션 순서를 보존한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | `PLATFORM_ADMIN_ASSIGNMENT` 부재로 실패 | V1~V20 스키마 검증 통과 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests io.regionevent.regioneventbackend.global.config.InitialP0SchemaMigrationTest` | 통과 | 원래 실패한 스키마 검증 |
| `./gradlew build` | 통과 | 병합 결과 전체 검증 |

## 재발 방지와 문서 반영

PR 충돌 해소 뒤에는 최신 base branch와의 병합 결과에서 전체 build를 실행한다.

## 잔여 위험과 후속 작업

없음.

## 관련 자료

- `0c823e3c`
- `src/main/resources/db/migration/V18__add_platform_admin_authorization_persistence.sql`
- `src/test/java/io/regionevent/regioneventbackend/global/config/InitialP0SchemaMigrationTest.java`
