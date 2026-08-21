# 로컬스탬프 백엔드

지역 행사와 체험을 탐색하고 예약·결제한 뒤 현장에서 체크인해 방문 리워드를 받을 수 있는 도메인 규칙과 데이터 정합성을 관리하는 Spring Boot API 서버입니다.


[조직 소개](https://github.com/Gimhae-Yay) ·
[프론트엔드](https://github.com/Gimhae-Yay/local-stamp-front) ·
[API 명세](docs/api/api-specification.md) ·
[ERD](docs/erd.md)

---

## 기술 스택

| 구분 | 기술 | 역할                    |
|---|---|-----------------------|
| Language | Java 21 | 서버 애플리케이션 개발          |
| Framework | Spring Boot, Spring MVC | HTTP API와 애플리케이션 실행   |
| Security | Spring Security, JWT | 인증·인가와 요청 경계 보호       |
| Persistence | Spring Data JPA, MySQL, Flyway | 업무 데이터 저장 및 스키마 이력 관리 |
| Cache | Redis | 인증·캐시 등 휘발성 데이터 처리    |
| Payment | PortOne Server SDK | 결제 검증과 웹훅 처리          |
| Storage | AWS S3 | 이미지 파일 저장             |
| Test | JUnit, Testcontainers, JaCoCo | 단위·통합 테스트 및 커버리지 검증   |
| Operations | Actuator | 헬스·메트릭 엔드포인트 제공       |

---

## 빠른 시작

### 요구 사항

- JDK 21
- Docker Desktop: Testcontainers 테스트 또는 로컬 의존 서비스 실행 시 필요
- Git

### 빠른 검증

- Windows PowerShell: `.\gradlew.bat fastTest`
- macOS·Linux: `./gradlew fastTest`

`fastTest`는 Testcontainers 기반이 아닌 빠른 테스트를 실행합니다.

### 애플리케이션 실행

로컬 실행에 필요한 데이터베이스, Redis, 비밀값을 설정한 후 실행합니다.

- Windows PowerShell: `.\gradlew.bat bootRun`
- macOS·Linux: `./gradlew bootRun`

실행 후 헬스 엔드포인트: `GET /actuator/health`

> 로컬 실행에 필요한 데이터베이스, Redis, 환경변수 설정은 이 `README.md`의 `환경변수` 및 `빠른 시작` 절을 기준으로 합니다.
---

## 환경변수

환경변수의 실제 값과 비밀값은 저장소에 기록하지 않습니다.

| 분류 | 예시 변수 | 설명                |
|---|---|-------------------|
| Redis | `AUTH_REDIS_HOST`, `AUTH_REDIS_PORT`, `AUTH_REDIS_PASSWORD` | Redis 연결 설정       |
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | MySQL 연결 설정       |
| JWT | `JWT_ACCESS_ACTIVE_KEY`, `JWT_REFRESH_ACTIVE_KEY` | 액세스·리프레시 토큰 서명 키  |
| QR | `QR_ACTIVE_KEY`, `QR_TOKEN_TTL` | QR 토큰 서명 키와 유효 시간 |
| Payment | `PORTONE_API_SECRET`, `PORTONE_WEBHOOK_SECRET` | 결제 및 웹훅 검증 비밀값    |
| Storage | `STORAGE_S3_BUCKET_NAME`, `STORAGE_S3_REGION` | 이미지 저장소 설정        |

- `.env`, 배포 환경변수, Secret Manager에만 값을 설정합니다.
- API Secret, 웹훅 Secret, JWT·QR 서명 키는 README·Issue·PR 본문·로그에 남기지 않습니다.
- 결제 fake 모드와 성능 fixture는 운영 환경에서 활성화하지 않습니다.

---

## 아키텍처

- Controller는 HTTP 요청 검증과 응답 변환을 담당합니다.
- UseCase·Service는 인증·인가, 도메인 규칙, 상태 전이, 트랜잭션 경계를 담당합니다.
- Repository는 영속성 조회·저장을 담당합니다.
- 외부 결제·저장소 연동 결과는 도메인 상태 전이와 분리해 검증합니다.

> 상세 패키지 구조와 의존성 규칙은 [아키텍처 문서](docs/ARCHITECTURE.md)를 따릅니다.

---

## 핵심 도메인 흐름

### 예약과 결제

![](https://github.com/user-attachments/assets/5a36d20f-ef09-41af-9c6d-ee3465b217d1)


### 운영 자동화

![](https://github.com/user-attachments/assets/24781de9-6bb8-4f43-9aad-0a9963e0c1c4)

- 행사 공개와 종료, 예약 홀드 만료, 쿠폰 발급처럼 정해진 시점에 처리해야 하는 작업을 자동화합니다.
- 여러 서버에서 동시에 실행되더라도 유효한 상태 전이는 한 번만 적용되도록 하여 중복 처리를 막습니다.
- 운영자가 반복해서 확인하거나 수동으로 처리하지 않아도 서비스 상태가 일정하게 유지하도록 구성합니다.

---

## API 문서와 데이터 모델

| 종류             | 링크                                                  |
|----------------|-----------------------------------------------------|
| 요청·응답·상태 코드·오류 코드 | [API 명세](docs/api/api-specification.md)             |
| 엔티티·테이블·인덱스·제약 | [ERD](docs/erd.md)                                  |
| 제품 정책과 도메인 규칙  | [P0 명세](docs/p0-spec.md) · [P1 명세](docs/p1-spec.md) |
| 기술 선택과 변경 이유   | [ADR](docs/adr/)                                    |

---

## 테스트와 검증

| 목적 | 명령 | 비고                          |
|---|---|-----------------------------|
| 빠른 테스트 | `./gradlew fastTest` | Testcontainers를 사용하지 않는 테스트 |
| 전체 테스트 | `./gradlew test` | 같은 작업 트리에서 동시에 실행하지 않음      |
| CI 빠른 검사 | `./gradlew ciFastCheck` | 애플리케이션 패키징 및 빠른 테스트         |
| 컨테이너 테스트 | `./gradlew containerTestShard1` / `containerTestShard2` | Docker 필요                   |
| HTTP 시나리오 | `http/README.md` 참고 | API 계약 기반 시나리오              |
| 성능 시나리오 | `performance/k6/README.md` 참고 | 전용 환경과 fixture 조건 확인 필요     |

> 테스트 통과는 해당 범위의 자동 검증 결과일뿐, 운영 배포 성공이나 전체 성능을 보장하지 않습니다.

---