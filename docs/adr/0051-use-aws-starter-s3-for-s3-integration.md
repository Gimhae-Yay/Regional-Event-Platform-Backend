# ADR-0051: S3 연동은 Spring Cloud AWS S3 starter 기반으로 구현

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-07-31
- 결정일: 2026-07-31
- 관련 요구사항: [PRD](../local-stamp-platform-prd.md)의 `FR-03`, `FR-14`, `AUTH-01`, `CON-02`, `CON-05`
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#164](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/164)
- 대체 대상: 없음

## 맥락

ADR-0016은 대표 이미지를 비공개 S3에 저장하고 서버가 짧은 유효기간의 서명 URL을 발급하는 정책을 정했다.
ADR-0041은 사전 업로드된 대표 이미지 객체를 운영자와 지역에 귀속하고, 콘텐츠·수정본 연결 직전에 S3 실제
객체의 크기와 체크섬을 다시 검증하는 정책을 정했다.

이제 구현 단계에서 S3 접근 의존성을 정해야 한다. 직접 AWS SDK 모듈을 애플리케이션 의존성으로 선언하면
presigned URL 발급, 객체 HEAD 검증, 삭제 구현이 특정 SDK 구성 방식에 강하게 묶인다. 프로젝트에서는 S3
연동을 Spring Boot 구성과 인프라 어댑터 안에 격리하기 위해 Spring Cloud AWS S3 starter 사용을 기준으로 한다.

## 결정 동인과 불변 조건

- API 계약은 `POST /operator/uploads/presigned-url`의 요청·응답 구조를 바꾸지 않아야 한다.
- S3 버킷 비공개, 서버 발급 presigned URL, 연결 직전 HEAD 검증, 즉시 삭제와 멱등 재시도 정책은 유지해야 한다.
- 애플리케이션 서비스는 S3 SDK 타입에 직접 의존하지 않고 도메인·유스케이스 계약과 인프라 어댑터 사이를 분리해야 한다.
- 실제 의존성은 프로젝트의 Gradle 설정에서 한 곳에 선언하고, S3 접근 코드는 인프라 계층에 격리해야 한다.
- S3 starter 의존성은 Gradle이 해석할 수 있는 정확한 좌표로 선언해야 한다.
- Spring Cloud AWS 버전은 Spring Boot 버전과의 공식 호환성 확인 뒤 한 곳에서 관리해야 한다.
- Spring Cloud AWS 4.1.0은 2026-07-22 릴리스되었고, Spring Cloud 2025.1.x는 2025.1.2부터
  Spring Boot 4.1.x와 호환된다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | Spring Cloud AWS S3 starter 기반 S3 어댑터 | Spring Boot 설정과 S3 연동 구성을 일관되게 가져가고, 애플리케이션 서비스가 직접 AWS SDK 의존성을 갖지 않는다. | presigned URL, HEAD, 삭제에 필요한 기능을 어댑터에서 명확히 감싸야 한다. | 낮음. 인프라 어댑터와 Gradle 의존성 조정으로 전환할 수 있다. | P0 이미지 업로드 범위에 가장 단순하고 유지보수성이 좋다. |
| 2 | AWS SDK S3 모듈 직접 의존 | SDK 문서를 그대로 따라 구현하기 쉽고 세부 제어가 명확하다. | SDK 타입과 설정이 서비스 코드로 퍼질 위험이 있고, Spring Boot 설정과 중복 구성이 생길 수 있다. | 중간. 이미 퍼진 SDK 타입을 어댑터 뒤로 되돌려야 한다. | 현재 프로젝트의 계층 분리 원칙에는 덜 적합하다. |
| 3 | S3 REST API와 서명 로직 직접 구현 | 외부 라이브러리 의존을 줄일 수 있다. | 서명, 헤더, 체크섬, 리전 처리 오류 위험이 크고 보안 검증 비용이 높다. | 높음. 검증되지 않은 서명 구현을 교체해야 한다. | P0에는 부적합하다. |

## 결정

대표 이미지 S3 연동은 Spring Cloud AWS S3 starter 기반으로 구현한다. Gradle 의존성은 직접 AWS SDK S3
모듈 대신 `io.awspring.cloud:spring-cloud-aws-starter-s3`를 사용한다.

버전은 `build.gradle` 한 곳에서 Spring Cloud AWS BOM으로 관리한다. starter 의존성에는 개별 버전을 적지 않는다.
현재 프로젝트의 Spring Boot `4.1.0` 기준 Spring Cloud AWS 버전은 `4.1.0`으로 확정한다.

```gradle
ext {
    springCloudAwsVersion = '4.1.0'
}

dependencies {
    implementation platform("io.awspring.cloud:spring-cloud-aws-dependencies:${springCloudAwsVersion}")
    implementation 'io.awspring.cloud:spring-cloud-aws-starter-s3'
}
```

Spring Cloud AWS 4.1.0의 BOM은 `io.awspring.cloud:spring-cloud-aws-dependencies:4.1.0`이며,
`io.awspring.cloud:spring-cloud-aws-starter-s3`는 이 BOM으로 버전을 해석한다. #185에서 Gradle
의존성 해석과 테스트로 재현성을 검증한다.

공식 확인 근거는 다음과 같다.

- Spring Cloud 호환성 표: `2025.1.x`는 `2025.1.2`부터 Spring Boot `4.1.x`를 지원한다.
- Spring Cloud AWS 4.1.0 릴리스 노트: 2026-07-22에 `4.1.0`이 릴리스되었다.
- Spring Cloud AWS 4.1.0 Reference Docs: BOM과 `spring-cloud-aws-starter-s3` artifact를 제공한다.
- Spring Cloud AWS 4.1.0 BOM은 Spring Cloud `5.0.2` 계열을 사용하며, Gradle 의존성 해석과
  `./gradlew build`로 프로젝트 적용 가능성을 검증했다.

presigned PUT URL 발급, S3 객체 HEAD 검증, 객체 삭제는 인프라 계층의 S3 어댑터가 담당한다. 애플리케이션
서비스와 유스케이스는 업로드 URL 발급, 객체 메타데이터 확인, 삭제 요청 같은 프로젝트 내부 인터페이스에만
의존한다. API 응답의 `uploadUrl`, `uploadHeaders`, `expiresAt` 형식은 기존 명세를 유지한다.

## 결과와 트레이드오프

### 기대 효과

- S3 설정과 클라이언트 구성을 Spring Boot 기반으로 일관되게 관리한다.
- 서비스 계층에 AWS SDK 타입이 새지 않아 테스트와 교체 비용을 줄인다.
- #164 이후 콘텐츠 생성·수정본 생성·수정 흐름에서도 같은 이미지 연결 검증 어댑터를 재사용할 수 있다.

### 수용한 단점과 위험

- starter가 제공하는 구성 방식에 맞춰 presigned URL 발급과 HEAD 검증 어댑터를 작성해야 한다.
- AWS SDK 세부 기능이 필요해질 경우에도 먼저 인프라 어댑터의 계약을 확장해야 한다.

## 전환과 롤백

기존 직접 AWS SDK S3 의존성이 있다면 Gradle에서 제거하고 Spring Cloud AWS BOM과
`io.awspring.cloud:spring-cloud-aws-starter-s3`로 교체한다.
S3 관련 구현은 인프라 어댑터에만 남기고 서비스 계층의 SDK 타입 의존을 제거한다.

문제가 발생하면 API 계약은 유지한 채 인프라 어댑터 내부 구현만 직접 AWS SDK 기반으로 되돌릴 수 있다.
이 경우에도 서비스 계층에 SDK 타입을 노출하지 않는 원칙은 유지한다.

## 검증 방법

- 빌드 의존성에 직접 AWS SDK S3 모듈이 남아 있지 않고 Spring Cloud AWS BOM과
  `io.awspring.cloud:spring-cloud-aws-starter-s3`가 사용되는지 확인한다.
- Spring Cloud AWS `4.1.0`이 프로젝트의 Spring Boot `4.1.0`과 호환되는지 공식 문서로 확인하고,
  Gradle 의존성 해석과 `./gradlew test`가 성공하는지 검증한다.
- 대표 이미지 업로드 URL 발급 시 응답의 `uploadUrl`, `uploadHeaders`, `expiresAt`이 API 명세와 같은지 검증한다.
- 연결 직전 S3 HEAD 결과의 체크섬과 `ContentLength` 불일치가 연결 실패로 처리되는지 검증한다.
- S3 삭제 실패 시 기존 삭제 재시도 상태가 유지되고 서비스 계층에 SDK 예외가 노출되지 않는지 검증한다.

## 대체 조건

- Spring Cloud AWS S3 starter가 presigned URL 발급, HEAD 검증, 삭제 요구를 안정적으로 지원하지 못한다.
- S3 외의 객체 저장소나 CDN 연동이 P0 범위를 넘어 공식 요구사항으로 확정된다.
- 운영 환경에서 starter 자동 구성보다 직접 SDK 구성이 장애 대응과 관측에 더 적합하다는 근거가 확인된다.
