# ADR-0110: 운영 Redis를 API Compose에서 분리한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-19
- 결정일: 2026-08-19
- 관련 요구사항: [기술 스택](../local-stamp-platform-tech-stack.md#기술-스택)의 캐시·제한·인증 폐기 및 배포
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: [ADR-0075](0075-distribute-canonical-compose-through-parameter-store.md)의 운영 EC2에서 `api`와 `auth-redis` 컨테이너를 함께 실행하는 범위

## 맥락

ADR-0075는 API와 인증 Redis를 하나의 EC2 Compose 스택으로 배포하도록 정했다. 이제 Redis는 Terraform이 API EC2와 같은 VPC의 독립 EC2에서 운영한다. API 컨테이너와 Redis의 기동·설정·저장소 수명주기를 함께 관리하면 Terraform이 소유한 Redis와 배포 저장소의 Compose 정의가 중복되어 운영 책임과 변경 기준이 다시 분산된다.

Redis는 여전히 공개 카탈로그 캐시와 Refresh Token 회전·폐기 상태를 제공하는 애플리케이션 런타임 의존성이다. 이 결정은 Redis 사용 정책이나 클라이언트 구현을 제거하지 않고, Redis 인프라의 관리 경계만 변경한다.

## 결정 동인과 불변 조건

- Redis EC2의 생성·네트워크·접근 제어·영속성·메모리 정책·기동·종료는 Terraform만 관리한다.
- 백엔드 저장소의 운영 Compose와 배포 워크플로는 Redis 컨테이너·볼륨·명령·Health 대기를 정의하거나 실행하지 않는다.
- API는 외부 런타임에서 주입한 `AUTH_REDIS_HOST`, `AUTH_REDIS_PORT`, `AUTH_REDIS_PASSWORD`로 독립 Redis에 연결하며, 연결 정보나 비밀값을 Git 이력에 저장하지 않는다.
- 독립 Redis는 App ASG에서 원격으로 접속하므로 Redis 기본 사용자에 `requirepass`를 설정한다. 비밀번호는 Terraform 입력으로 받은 뒤 Parameter Store `SecureString`에 저장하고, Redis EC2와 API EC2가 기동 시에만 복호화한다.
- Redis 연결 실패는 기존 인증·캐시 정책에 따른 애플리케이션 실패로 드러나야 하며, API가 로컬 Redis 컨테이너로 우회하면 안 된다.
- API 이미지와 API 전용 Compose 정의는 같은 배포 커밋에서 전달하고, API Health와 ALB 대상 Health가 모두 정상일 때만 배포를 성공으로 판정한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | Terraform 관리 독립 Redis EC2와 API 전용 Compose | 인프라 소유권과 Redis 운영 기준을 Terraform으로 단일화하고 API 배포는 API 컨테이너만 다룬다. 독립 Redis의 수명주기가 API 인스턴스 교체에 영향받지 않는다. | Terraform의 네트워크·런타임 변수 주입이 누락되면 API Health가 실패한다. Redis 장애는 여러 API 인스턴스에 영향을 줄 수 있다. | 중간. Terraform과 배포 구성을 함께 이전해야 하며, 기존 Redis 데이터는 인프라 절차에 따라 이관해야 한다. | 사용자 결정이며 현재 배포 책임 분리에 적합하다. |
| 2 | API와 Redis를 기존 Compose 스택에서 함께 운영 | 단일 저장소에서 API와 Redis 기동을 확인할 수 있다. | Terraform Redis와 관리 주체가 중복되고 API 교체가 Redis 컨테이너 수명주기에 결합된다. | 낮음. 현재 구성 유지다. | 새 인프라 경계에 부적합하다. |

## 결정

운영 Redis는 Terraform이 같은 VPC의 독립 EC2에서 관리한다. 백엔드 저장소의 운영 Compose는 API 서비스만 정의하고, 배포 워크플로는 API 전용 Compose 파일만 Parameter Store로 전달·검증한다. 기존 `auth-redis` 서비스, Redis 볼륨, Redis 서버 옵션과 Redis 컨테이너 Health 대기는 제거한다.

API는 Redis 클라이언트 역할을 유지한다. Terraform이 Redis EC2의 주소·포트·인증 비밀번호를 API EC2의 런타임 구성에 `AUTH_REDIS_HOST`, `AUTH_REDIS_PORT`, `AUTH_REDIS_PASSWORD`로 주입하며, 이 저장소는 해당 인프라 자원이나 값을 생성·관리하지 않는다. Redis 인스턴스 내부의 설정과 네트워크 검증은 Terraform 배포 범위에서 수행한다.

Redis 인증은 현재 단일 API 클라이언트에 필요한 최소 구성인 `requirepass`를 사용한다. 이 방식은 백엔드에 사용자명 설정을 추가하지 않으며, 비밀번호가 없는 원격 연결을 Redis protected mode에 의존해 우회하지 않는다. 이후 관리자·배치 등 서로 다른 Redis 권한이 필요한 클라이언트가 추가되면, 별도 ADR을 통해 ACL 사용자와 명령·키 패턴 권한을 설계한다.

## 결과와 트레이드오프

### 기대 효과

- Redis 인프라의 기준과 수명주기가 Terraform으로 일원화된다.
- API EC2 교체와 Redis 재기동·데이터 볼륨 수명주기가 분리된다.
- 백엔드 배포 검증은 API 컨테이너와 애플리케이션 Health에 집중한다.

### 수용한 단점과 위험

- API 배포만으로 Redis 인프라를 생성하거나 복구할 수 없다.
- Terraform의 보안 그룹, 주소·포트 주입 또는 Redis 가용성 오류는 API Health 실패로 나타나므로 인프라 관측과 배포 순서가 필요하다.
- 기존 Compose Redis의 데이터가 있다면 Terraform 전환 절차가 별도로 이관·폐기 여부를 판단해야 한다. 이 저장소는 Redis 데이터를 이관하지 않는다.

## 전환과 롤백

Terraform에서 독립 Redis EC2와 API EC2의 VPC 연결, 접근 제어, 영속성 및 API 런타임 변수 주입을 먼저 준비한다. Redis 연결과 API Health를 확인한 뒤 API 전용 Compose를 배포한다. 기존 Compose Redis는 새 API가 독립 Redis를 사용한다는 인프라 검증 뒤 Terraform 운영 절차에 따라 중지·폐기한다.

전환 중 API Health가 Redis 연결 오류로 실패하면 API 배포를 성공 처리하지 않고, Terraform의 Redis 가용성·네트워크·런타임 변수 주입을 수정한 뒤 재배포한다. 이전 Compose Redis로 되돌리는 경우에도 Terraform이 관리하는 Redis의 데이터나 설정을 자동 변경하지 않으며, 별도 인프라 변경으로만 복구한다.

## 검증 방법

- API 전용 Compose 구성 검증에서 Redis 서비스·볼륨·서버 옵션이 없고 `AUTH_REDIS_HOST`가 외부 런타임 변수로 필수인지 확인한다.
- 배포 워크플로의 원격 Compose 검증이 API 서비스 하나와 `GET /actuator/health`의 정상 응답을 확인하는지 검증한다.
- Terraform이 주입한 Redis 연결 정보로 API가 기동하고, Redis 연결 불가 시 Health 또는 인증 경로가 성공으로 표시되지 않는지 인프라 통합 환경에서 확인한다.
- 백엔드 저장소의 배포 정의·워크플로에 Redis 이미지, 컨테이너 이름, 볼륨, 서버 옵션이 남지 않는지 정적 검색으로 확인한다.

## 대체 조건

- Redis 기반 캐시와 Refresh Token 상태를 애플리케이션에서 완전히 제거하거나 다른 관리형 서비스로 이전한다.
- API가 컨테이너 오케스트레이터로 이전해 API 전용 Compose 전달을 더 이상 사용하지 않는다.
- Terraform 외의 운영 플랫폼이 Redis 인프라의 단일 관리 주체가 된다.
