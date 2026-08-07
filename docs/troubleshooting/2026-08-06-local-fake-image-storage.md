# 로컬 공개 콘텐츠 조회 시 이미지 저장소 비활성화로 500 응답

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | 로컬 k6 공개 콘텐츠 목록·상세 시나리오가 HTTP 500으로 실패한다. |
| 최초 확인 시각·시간대 | 2026-08-06, Asia/Seoul |
| 관련 요구사항·이슈 | 공개 콘텐츠의 대표 이미지 조회 URL 제공, ADR-0016 |
| revision·브랜치 | `28ff1dd2`, `test/k6-flow-scenarios` |
| 환경·프로필 | Java 21.0.7, 별도 Spring 프로필 없음, 로컬 MySQL·Redis |

## 기대 결과와 실제 결과

### 기대 결과

시드 데이터의 공개 콘텐츠 목록·상세·회차 목록 API가 성공하고 k6 공개 콘텐츠 시나리오가 임계값을 충족한다.

### 실제 결과

회차 목록 API는 HTTP 200이지만 대표 이미지 조회 URL을 포함하는 공개 콘텐츠 목록·상세 API는 HTTP 500이다.

## 재현 절차

### 선행 조건

- 로컬 애플리케이션이 `http://localhost:8080`에서 실행 중이다.
- `performance/k6/seed/k6-local.seed.sql`의 고정 시드 데이터가 로컬 MySQL에 반영돼 있다.
- `storage.s3.enabled`와 별도 이미지 저장소 대체 구현이 활성화되지 않았다.

### 명령·요청·입력

1. `GET /api/v1/contents?regionId=900001&contentType=EVENT_EXPERIENCE&reservationAvailable=true`를 호출한다.
2. `GET /api/v1/contents/900001`을 호출한다.
3. `GET /api/v1/contents/900001/sessions`를 호출한다.

### 재현 결과

- 실행 횟수: 각 API 1회
- 성공 횟수: 1회
- 실패 횟수: 2회
- 종료 코드·HTTP 상태: 목록 500, 상세 500, 회차 목록 200

## 수집한 증거

- `ImageStorageConfig`는 S3가 활성화되지 않으면 `DisabledImageStorageClient`를 등록한다.
- `DisabledImageStorageClient.createPresignedGetUrl()`은 `ImageStorageException`을 던진다.
- `RepresentativeImageViewUrlService`는 해당 예외를 `INTERNAL_SERVER_ERROR`로 변환한다.
- 시드 이미지 객체는 `ACTIVE`이며 공개 콘텐츠에 연결돼 있으므로 DB 연결 누락이 원인이 아니다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-06 09:47 KST | 관찰 | 대표 이미지 URL이 필요한 API만 실패한다. | 회차 API는 성공하고 목록·상세만 실패해야 한다. | 200과 500으로 구분돼 재현됐다. | 채택 |
| 2026-08-06 09:47 KST | 검증 | 이미지 저장소 비활성 구현이 500을 유발한다. | 조회 URL 생성 시 저장소 예외가 발생해야 한다. | 코드 경로로 확인했다. | 채택 |
| 2026-08-06 09:51 KST | 변경 | 명시적으로 활성화하는 fake 저장소 어댑터를 추가했다. | 동일 데이터의 목록·상세·회차 API가 모두 성공해야 한다. | 세 API 모두 HTTP 200을 반환했다. | 채택 |
| 2026-08-06 09:51 KST | 검증 | 공개 콘텐츠 k6 시나리오를 10초간 실행했다. | 시스템 실패 없이 모든 임계값을 충족해야 한다. | 516회 반복, 체크 3,096건 통과, 임계값 PASS. | 채택 |

## 가설과 검증

### 가설 1: 이미지 저장소 비활성 구현이 공개 조회를 실패시킨다

- 근거: 공개 목록·상세는 대표 이미지 URL을 생성하지만 회차 목록은 생성하지 않는다.
- 참일 때의 예측: S3 없이 성공하는 대체 게이트웨이를 활성화하면 같은 데이터로 목록·상세가 200이 된다.
- 반증 조건: 대체 게이트웨이 활성화 뒤에도 같은 저장소 예외로 500이 발생한다.
- 검증 방법: 명시적 설정으로만 활성화되는 로컬 fake 구현을 추가하고 같은 API와 k6를 재실행한다.
- 결과: fake 활성화 전 목록·상세는 500이었고, 활성화 후 목록·상세·회차가 모두 200이었다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 대표 이미지가 연결된 공개 콘텐츠를 S3 비활성 환경에서 조회한다.
- 결함이 있는 코드·설정·데이터·계약: 로컬 성능 테스트 환경에 조회 URL을 제공할 이미지 저장소 어댑터가 없다.
- 증상으로 이어진 메커니즘: 비활성 게이트웨이 예외가 서비스에서 내부 서버 오류로 변환된다.
- 기존 방어가 막지 못한 이유: 비활성 구현은 저장소 사용 시 즉시 실패하도록 설계됐으며 로컬 대체 구현은 없었다.
- 결론의 증거: 공개 이미지 조회 경로와 비활성 게이트웨이의 예외 변환 코드, API별 상태 차이.

## 해결 또는 완화

- 선택한 방법: 명시적 설정으로만 활성화되는 로컬 fake 이미지 저장소 어댑터를 추가한다.
- 변경 파일: `FakeImageStorageClient.java`, `ImageStorageConfig.java`, `application.yaml`, `ImageStorageConfigTest.java`
- 정책·계약 변경 여부: 없음. 운영 S3와 공개 API 계약은 유지한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | 목록 500, 상세 500, 회차 목록 200 | 목록 200, 상세 200, 회차 목록 200 | 통과 |
| 공개 콘텐츠 k6 | 임계값 실패 | 516회 반복, 체크 3,096건 성공, HTTP 실패율 0%, p95 10.53ms | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew test --tests "io.regionevent.regioneventbackend.infra.storage.ImageStorageConfigTest"` | 통과 | 최초 실행은 테스트 import 누락으로 컴파일 실패 후 수정 |
| 이미지 저장소·대표 이미지 URL 관련 테스트 | 통과 | S3, fake, 비활성 구성과 서비스 회귀 검증 |
| 공개 콘텐츠 k6 시나리오 | 통과 | 고정 시드 ID, VU 1, 10초 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 재발 방지와 문서 반영

명시적 설정 조건과 구성 테스트로 운영 S3 설정과 로컬 fake 설정의 경계를 검증한다.

## 잔여 위험과 후속 작업

fake URL은 로컬 성능 테스트에서 API 응답 생성을 위한 값이며 실제 이미지 바이트 전송이나 S3 서명 검증은 수행하지 않는다.

## 관련 자료

- `docs/adr/0016-use-private-s3-presigned-urls-and-immediate-image-deletion.md`
- `performance/k6/results/public-content-readonly-fake-storage-001.md`
