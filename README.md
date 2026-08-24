# 로컬스탬프 백엔드

지역 행사와 체험을 탐색하고 예약·결제한 뒤, 현장에서 체크인해 방문 리워드를 받을 수 있도록 
도메인 규칙과 데이터 정합성을 관리하는 Spring Boot API 서버입니다.


[조직 소개](https://github.com/Gimhae-Yay) ·
[프론트엔드](https://github.com/Gimhae-Yay/local-stamp-front) ·
[API 명세](docs/api/api-specification.md) ·
[ERD P0](docs/erd.md) · [ERD P1](docs/p1-erd.md)

---

## 기술 스택

| 구분          | 기술                             | 역할                    |
| ----------- | ------------------------------ | --------------------- |
| Language    | Java 21                        | 서버 애플리케이션 개발          |
| Framework   | Spring Boot, Spring MVC        | HTTP API 제공 및 애플리케이션 실행 |
| Security    | Spring Security, JWT           | 인증·인가와 요청 경계 보호       |
| Persistence | Spring Data JPA, MySQL, Flyway | 업무 데이터 저장 및 스키마 이력 관리 |
| Cache       | Redis                          | 인증·캐시 등 휘발성 데이터 처리    |
| Payment     | PortOne Server SDK             | 결제 검증 및 웹훅 처리          |
| Storage     | AWS S3                         | 이미지 파일 저장             |
| Test        | JUnit, Testcontainers, JaCoCo  | 단위·통합 테스트 및 커버리지 검증   |
| Operations  | Actuator                       | 헬스·메트릭 엔드포인트 제공       |

---

## 빠른 시작

### 요구 사항

- JDK 21
- Docker Desktop: Testcontainers 테스트 또는 로컬 의존 서비스 실행 시 필요
- Git

### 빠른 검증

- Windows PowerShell: `.\gradlew.bat fastTest`
- macOS·Linux: `./gradlew fastTest`

`fastTest`는 Testcontainers를 사용하지 않는 빠른 테스트를 실행합니다.

### 애플리케이션 실행

로컬 실행에 필요한 데이터베이스, Redis, 비밀값을 설정한 뒤 실행합니다.

- Windows PowerShell: `.\gradlew.bat bootRun`
- macOS·Linux: `./gradlew bootRun`

실행 후 헬스 엔드포인트: `GET /actuator/health`

> 로컬 실행에 필요한 데이터베이스, Redis, 환경변수 설정은 이 `README.md`의 `환경변수` 및 `빠른 시작` 절을 따릅니다.

---

## 환경변수

환경변수의 실제 값과 비밀값은 저장소에 기록하지 않습니다.

| 분류       | 예시 변수                                                                               | 설명                |
| -------- | ----------------------------------------------------------------------------------- | ----------------- |
| Redis    | `AUTH_REDIS_HOST`, `AUTH_REDIS_PORT`, `AUTH_REDIS_PASSWORD`                         | Redis 연결 설정       |
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | MySQL 연결 설정       |
| JWT      | `JWT_ACCESS_ACTIVE_KEY`, `JWT_REFRESH_ACTIVE_KEY`                                   | 액세스·리프레시 토큰 서명 키  |
| QR       | `QR_ACTIVE_KEY`, `QR_TOKEN_TTL`                                                     | QR 토큰 서명 키와 유효 시간 |
| Payment  | `PORTONE_API_SECRET`, `PORTONE_WEBHOOK_SECRET`                                      | 결제 및 웹훅 검증 비밀값    |
| Storage  | `STORAGE_S3_BUCKET_NAME`, `STORAGE_S3_REGION`                                       | 이미지 저장소 설정        |

- `.env`, 배포 환경변수, Secret Manager에만 값을 설정합니다.
- API Secret, 웹훅 Secret, JWT·QR 서명 키는 README·Issue·PR 본문·로그에 남기지 않습니다.
- 결제 fake 모드와 성능 fixture는 운영 환경에서 활성화하지 않습니다.

---

## 아키텍처

- Controller는 HTTP 요청을 검증하고 응답으로 변환합니다.
- UseCase·Service는 인증·인가, 도메인 규칙, 상태 전이, 트랜잭션 경계를 담당합니다.
- Repository는 영속성 조회와 저장을 담당합니다.
- 외부 결제·저장소 연동 결과는 도메인 상태 전이와 분리해 검증합니다.

> 상세 패키지 구조와 의존성 규칙은 [아키텍처 문서](docs/ARCHITECTURE.md)를 따릅니다.

---

## 핵심 도메인 흐름

### 예약과 결제

![](https://github.com/user-attachments/assets/5a36d20f-ef09-41af-9c6d-ee3465b217d1)


### 운영 자동화

![](https://github.com/user-attachments/assets/24781de9-6bb8-4f43-9aad-0a9963e0c1c4)

- 행사 공개와 종료, 예약 홀드 만료, 쿠폰 발급처럼 정해진 시점에 처리해야 하는 작업을 자동화합니다.
- 여러 서버에서 동시에 실행되더라도 유효한 상태 전이는 한 번만 적용해 중복 처리를 막습니다.
- 운영자가 반복해서 확인하거나 수동으로 처리하지 않아도 서비스 상태가 일정하게 유지되도록 구성합니다.

---

## API 문서와 데이터 모델

| 종류             | 링크                                                  |
|----------------|-----------------------------------------------------|
| 요청·응답·상태 코드·오류 코드 | [API 명세](docs/api/api-specification.md)             |
| 엔티티·테이블·인덱스·제약 | [ERD P0](docs/erd.md) · [ERD P1](docs/p1-erd.md)            |
| 제품 정책과 도메인 규칙  | [P0 명세](docs/p0-spec.md) · [P1 명세](docs/p1-spec.md) |
| 기술 선택과 변경 이유   | [ADR](docs/adr/)                                    |

---

## 테스트와 검증

| 목적        | 명령                                                      | 비고                          |
| --------- | ------------------------------------------------------- | --------------------------- |
| 빠른 테스트    | `./gradlew fastTest`                                    | Testcontainers를 사용하지 않는 테스트 |
| 전체 테스트    | `./gradlew test`                                        | 같은 작업 트리에서 동시에 실행하지 않음      |
| CI 빠른 검사  | `./gradlew ciFastCheck`                                 | 애플리케이션 패키징 및 빠른 테스트         |
| 컨테이너 테스트  | `./gradlew containerTestShard1` / `containerTestShard2` | Docker 필요                   |
| HTTP 시나리오 | [http/README.md](http/README.md) 참고                     | API 계약 기반 시나리오              |
| 성능 시나리오   | [performance/k6/README.md](performance/k6/README.md) 참고 | 전용 환경과 fixture 조건 확인 필요     |

> 테스트 통과는 해당 범위의 자동 검증 결과일 뿐, 운영 배포 성공이나 전체 성능을 보장하지 않습니다.

---

## 의사결정 문서

의사결정에 대한 문서는 [ADR](docs/adr/)에 있습니다.
아래는 핵심 결정에 대한 내용입니다.

- [Stateless Refresh Token 전환](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-Stateless-Refresh-Token-%EC%A0%84%ED%99%98)
- [무료 예약의 정원 정합성·영속 멱등성](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-%EB%AC%B4%EB%A3%8C-%EC%98%88%EC%95%BD%EC%9D%98-%EC%A0%95%EC%9B%90-%EC%A0%95%ED%95%A9%EC%84%B1%C2%B7%EC%98%81%EC%86%8D-%EB%A9%B1%EB%93%B1%EC%84%B1)
- [멱등성은 저장된 레코드로](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-%EB%A9%B1%EB%93%B1%EC%84%B1%EC%9D%80-%EC%A0%80%EC%9E%A5%EB%90%9C-%EB%A0%88%EC%BD%94%EB%93%9C%EB%A1%9C)
- [체크인 성공을 보존하기 위한 미션 진행도 트랜잭션 분리](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-%EC%B2%B4%ED%81%AC%EC%9D%B8-%EC%84%B1%EA%B3%B5%EC%9D%84-%EB%B3%B4%EC%A1%B4%ED%95%98%EA%B8%B0-%EC%9C%84%ED%95%9C-%EB%AF%B8%EC%85%98-%EC%A7%84%ED%96%89%EB%8F%84-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EB%B6%84%EB%A6%AC)
- [다중 인스턴스 스케줄러를 한 번의 유효한 상태 전이로 수렴시키기](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-%EB%8B%A4%EC%A4%91-%EC%9D%B8%EC%8A%A4%ED%84%B4%EC%8A%A4-%EC%8A%A4%EC%BC%80%EC%A4%84%EB%9F%AC%EB%A5%BC-%ED%95%9C-%EB%B2%88%EC%9D%98-%EC%9C%A0%ED%9A%A8%ED%95%9C-%EC%83%81%ED%83%9C-%EC%A0%84%EC%9D%B4%EB%A1%9C-%EC%88%98%EB%A0%B4%EC%8B%9C%ED%82%A4%EA%B8%B0)
- [단기 HMAC QR 토큰](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-%EB%8B%A8%EA%B8%B0-HMAC-QR-%ED%86%A0%ED%81%B0)
- [Testcontainers CI 샤딩](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%EC%9D%98%EC%82%AC-%EA%B2%B0%EC%A0%95%5D-Testcontainers-CI-%EC%83%A4%EB%94%A9)

---

## 트러블슈팅 문서

프로젝트를 진행하면서 겪은 문제점에 대한 트러블슈팅 문서입니다.

- [잠금 대기로 과거 시각이 미션 자동 종료에 반영되던 정합성 문제 해결](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%5D-%EC%9E%A0%EA%B8%88-%EB%8C%80%EA%B8%B0%EB%A1%9C-%EA%B3%BC%EA%B1%B0-%EC%8B%9C%EA%B0%81%EC%9D%B4-%EB%AF%B8%EC%85%98-%EC%9E%90%EB%8F%99-%EC%A2%85%EB%A3%8C%EC%97%90-%EB%B0%98%EC%98%81%EB%90%98%EB%8D%98-%EC%A0%95%ED%95%A9%EC%84%B1-%EB%AC%B8%EC%A0%9C-%ED%95%B4%EA%B2%B0)
- [트랜잭션 잠금 순서 통일을 통한 동시성 Deadlock 위험 개선](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%5D-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98-%EC%9E%A0%EA%B8%88-%EC%88%9C%EC%84%9C-%ED%86%B5%EC%9D%BC%EC%9D%84-%ED%86%B5%ED%95%9C-%EB%8F%99%EC%8B%9C%EC%84%B1-Deadlock-%EC%9C%84%ED%97%98-%EA%B0%9C%EC%84%A0)
- [결제 생성의 시간 경계에서 만료된 홀드·쿠폰이 사용되던 정합성 문제 해결](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%5D-%EA%B2%B0%EC%A0%9C-%EC%83%9D%EC%84%B1%EC%9D%98-%EC%8B%9C%EA%B0%84-%EA%B2%BD%EA%B3%84%EC%97%90%EC%84%9C-%EB%A7%8C%EB%A3%8C%EB%90%9C-%ED%99%80%EB%93%9C%C2%B7%EC%BF%A0%ED%8F%B0%EC%9D%B4-%EC%82%AC%EC%9A%A9%EB%90%98%EB%8D%98-%EC%A0%95%ED%95%A9%EC%84%B1-%EB%AC%B8%EC%A0%9C-%ED%95%B4%EA%B2%B0)
- [존재하지 않는 결제 웹훅이 외부 API 장애로 500을 반환하던 문제 해결](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%5D-%EC%A1%B4%EC%9E%AC%ED%95%98%EC%A7%80-%EC%95%8A%EB%8A%94-%EA%B2%B0%EC%A0%9C-%EC%9B%B9%ED%9B%85%EC%9D%B4-%EC%99%B8%EB%B6%80-API-%EC%9E%A5%EC%95%A0%EB%A1%9C-500%EC%9D%84-%EB%B0%98%ED%99%98%ED%95%98%EB%8D%98-%EB%AC%B8%EC%A0%9C-%ED%95%B4%EA%B2%B0)
- [DB 정리 단계에서 멈추던 통합 테스트의 Fail‐Fast 개선](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%5D-DB-%EC%A0%95%EB%A6%AC-%EB%8B%A8%EA%B3%84%EC%97%90%EC%84%9C-%EB%A9%88%EC%B6%94%EB%8D%98-%ED%86%B5%ED%95%A9-%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%9D%98-Fail%E2%80%90Fast-%EA%B0%9C%EC%84%A0)
- [JVM 공유 MySQL Testcontainers 전환을 통한 CI 빌드 성능 개선](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/wiki/%5B%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85%5D-JVM-%EA%B3%B5%EC%9C%A0-MySQL-Testcontainers-%EC%A0%84%ED%99%98%EC%9D%84-%ED%86%B5%ED%95%9C-CI-%EB%B9%8C%EB%93%9C-%EC%84%B1%EB%8A%A5-%EA%B0%9C%EC%84%A0)

