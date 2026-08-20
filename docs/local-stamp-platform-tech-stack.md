# 기술 스택 


| 영역      | 기술 스택                                          | 적용 목적                                            |
|---------|------------------------------------------------|--------------------------------------------------|
| Backend | Java 21, Spring Boot 4.1, Gradle               | REST API와 도메인 중심 백엔드                             |
| API/검증  | Spring MVC, Bean Validation                    | API 명세·입력 검증                                     |
| DB      | MySQL 8, Spring Data JPA/Hibernate, Flyway     | 예약·결제·방문·쿠폰의 트랜잭션 및 스키마 이력                       |
| 복합 조회   | QueryDSL, Pageable                             | 지역·유형·공개 상태·예약 가능 여부 기반 검색/페이징                   |
| 인증/인가   | Spring Security, JWT Access/Stateless Refresh Token | 방문자·운영자·지역 관리자 역할 및 지역 경계 강제                     |
| 동시성     | MySQL 조건부 업데이트 + `@Version`                    | 회차 정원 초과 예약 방지. 결제 중에는 예약 홀드와 만료 처리를 둠           |
| 캐시/제한 | Terraform이 같은 VPC의 독립 EC2에서 운영하는 Redis | 지역 홈·공개 콘텐츠 캐시와 로그인·결제·QR API 요청 제한 |
| 결제      | PortOne 연동 어댑터                                 | 결제 승인·웹훅·취소 처리를 결제사와 분리하고 멱등 처리                  |
| QR 인증   | ZXing, HMAC-SHA256 서명 토큰                       | 사용자별 1회성 예약 QR 발급 및 위·변조/중복 체크인 방지               |
| 이미지     | Amazon S3, Spring Cloud AWS S3 starter 4.1.0 (`io.awspring.cloud:spring-cloud-aws-starter-s3`) | 콘텐츠 대표 이미지 저장, presigned URL 발급과 객체 검증·삭제 연동 |
| 비동기     | 후속 단계: Transactional Outbox + Spring Scheduler | 알림·분석 외부 전달 도입 시 거래와 분리. 현재 P0에서는 구현하지 않음 |
| 테스트     | JUnit 5, Mockito, Testcontainers, k6           | 예약 동시성·웹훅·중복 QR·권한·부하 검증                         |
| 관측      | Actuator, Grafana, 구조화 JSON 로그                 | QR 실패율, 결제 웹훅 지연, 상태 전이, 오류율 관찰                  |
| 배포      | API 전용 Docker Compose, Terraform, GitHub Actions, AWS EC2/RDS/S3 | API 컨테이너 배포 자동화. Redis EC2의 생성·설정·수명주기는 Terraform이 관리 |

핵심 설계 기준은 다음입니다.

- Redis는 **캐시·요청 제한**에 쓰고, Stateless Refresh Token의 유효성은 Redis 상태에 의존하지 않는다. 예약 정원 차감의 최종 정합성은 **MySQL 트랜잭션**이 책임집니다.
- Redis 인스턴스의 생성·설정·기동·중지는 Terraform이 담당한다. 백엔드 저장소는 Redis 클라이언트로서 외부 런타임 연결 정보만 받아 사용하며 Redis 컨테이너를 배포하지 않는다.
- `지역 홈·콘텐츠 목록·상세`만 캐시하며, `잔여 정원·가격·예약 가능 여부`는 캐시하지 않습니다.
- QR·결제 웹훅·체크인은 모두 멱등 키 또는 처리 이력으로 중복 요청에 같은 결과를 반환해야 합니다.
- AI, RAG, SSE 채팅, Kafka는 문서상 **후속 단계**입니다. MVP 기본 스택에 넣지 않는 것이 맞습니다.
