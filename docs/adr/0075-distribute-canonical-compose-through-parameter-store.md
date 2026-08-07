# ADR-0075: 운영 Compose 정의를 Parameter Store로 전달한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-07
- 결정일: 2026-08-07
- 관련 요구사항: [기술 스택](../local-stamp-platform-tech-stack.md)의 배포, [이슈 #471](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/471)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#471 ECR 이미지 기반 EC2 Compose 배포 자동화 및 운영 검증](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/471)
- 대체 대상: 없음

## 맥락

백엔드 저장소의 `compose.auth-redis.yaml`과 Terraform EC2 user-data가 각각 API·Redis Compose 정의를 유지하고 있다.
두 파일은 같은 런타임을 표현하지만 서로 독립적으로 변경될 수 있어, 백엔드 PR에서 검증한 Compose 구성과 새 EC2가
실행한 구성이 달라질 수 있다.

MVP EC2는 API와 인증 Redis 컨테이너만 실행한다. RDS, S3, Parameter Store는 컨테이너가 아닌 외부 서비스이며,
비밀값은 Compose 정의가 아니라 EC2가 생성한 권한 제한 `.env` 파일로만 주입해야 한다.

## 결정 동인과 불변 조건

- 새 EC2는 배포 워크플로가 빌드한 같은 커밋 SHA의 API 이미지와 그 커밋의 Compose 정의를 실행해야 한다.
- EC2 Compose 서비스는 `api`, `auth-redis` 두 개로 고정한다.
- DB·JWT·QR 값은 SecureString Parameter Store에서 읽어 `.env`로만 전달하며, Compose 정의와 Git 이력에 비밀값을 넣지 않는다.
- Compose 전달 또는 기동·Health 검증이 실패하면 배포는 성공으로 기록되면 안 된다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 백엔드 워크플로가 기준 Compose 파일을 SSM String 파라미터에 기록하고, EC2 user-data가 이를 내려받아 실행한다 | 배포 이미지 태그와 Compose 정의를 같은 워크플로에서 기록하며, EC2에는 두 컨테이너만 유지한다. 기존 EC2의 SSM 읽기 경로를 재사용한다. | Standard Parameter의 4KB 제한을 넘으면 기록이 실패한다. 기준 파일 크기 검증과 추후 Advanced Parameter 전환 판단이 필요하다. | 낮음. user-data를 Terraform 템플릿 방식으로 되돌리고 해당 파라미터를 사용 중지한다. | 추천안이며 사용자가 채택했다. 현재 Compose는 1,973바이트다. |
| 2 | Terraform user-data 안의 별도 Compose 템플릿을 운영 기준으로 유지한다 | EC2 초기화만으로 필요한 파일이 완결된다. | 백엔드 저장소 Compose와 중복되어 드리프트를 막을 수 없다. | 낮음. 템플릿을 계속 수정하면 된다. | 단일 기준 요구에 부적합하다. |
| 3 | 배포 워크플로가 Compose 파일을 S3에 올리고 EC2가 객체를 내려받는다 | 큰 Compose 파일도 전달할 수 있고 버전별 객체를 보관할 수 있다. | 업로드 권한·객체 수명 관리·버전 선택을 추가로 설계해야 한다. | 중간. S3 정책과 배포 절차를 함께 되돌려야 한다. | 현재 4KB 미만인 MVP Compose에는 과하다. |

## 결정

백엔드 저장소의 `compose.auth-redis.yaml`을 운영 Compose의 단일 기준으로 둔다. 배포 워크플로는 API 이미지 태그를
기록한 뒤 같은 커밋의 Compose 파일을 `/regional-event/prod/COMPOSE_FILE` SSM String 파라미터에 기록한다.
Terraform은 이 파라미터를 선언하고, EC2 user-data는 이를 `/opt/regional-event/compose.yaml`로 저장한 뒤 기존
`.env`와 함께 Compose를 실행한다.

`COMPOSE_FILE`은 비밀값을 포함하지 않는다. 비밀값과 S3·RDS·JWT·QR 런타임 값은 기존 SecureString 조회와
`.env` 주입 경로를 유지한다.

## 결과와 트레이드오프

### 기대 효과

- 백엔드 Compose 검증 대상과 EC2 실행 대상이 같은 Git 커밋의 파일이 된다.
- EC2의 컨테이너 범위는 API와 인증 Redis로 유지된다.
- Compose 변경은 백엔드 PR과 배포 워크플로에서 함께 검증할 수 있다.

### 수용한 단점과 위험

- Parameter Store의 Standard Parameter 4KB 제한이 Compose 파일 확장을 제한한다.
- SSM 쓰기 실패는 새 EC2 교체 전에 배포를 중단해야 하며, 정상 이미지가 있어도 배포가 진행되지 않는다.
- 수동 EC2 교체 전에 Compose 파라미터와 이미지 태그를 함께 기록하지 않으면 이전 조합이 실행될 수 있다.

## 전환과 롤백

Terraform 적용으로 빈 `COMPOSE_FILE` 파라미터와 EC2 읽기 경로를 먼저 준비한다. 이후 배포 워크플로가 SHA 이미지
태그와 Compose 파일을 기록한 뒤 ASG 인스턴스를 교체한다. 첫 배포에서는 새 EC2의 Compose 서비스 목록과 Health를
확인한다.

SSM 파일 전달이 실패하면 ASG 교체를 시작하지 않는다. 전환을 되돌릴 때는 user-data의 Parameter Store 파일 조회를
제거하고 검증된 Terraform 템플릿을 다시 사용한다. 이 롤백은 비밀값 파라미터와 RDS·Redis 상태를 변경하지 않는다.

## 검증 방법

- 배포 전 Compose 파일 크기가 4KB 이하인지 검사하고, SSM 기록 실패 시 ASG 종료 단계로 진행하지 않는지 확인한다.
- 새 EC2의 `/opt/regional-event/compose.yaml` 서비스 목록이 `api`, `auth-redis`뿐인지 확인한다.
- 커밋 SHA 이미지, Compose 서비스 실행, Redis·RDS를 포함한 Health 정상 응답, ALB 대상 Health가 모두 성공일 때만 배포 성공으로 판정하는지 확인한다.
- 잘못된 Compose 파일, 이미지 태그, RDS 설정, Redis 기동 실패 각각이 배포 실패로 기록되는지 확인한다.

## 대체 조건

- 운영 Compose 파일이 4KB를 초과하거나 여러 파일·비밀 파일 전달이 필요해 Standard Parameter가 부족해진다.
- Compose 버전 보관·감사·다중 환경 분리가 필요해 S3 또는 전용 구성 배포 체계가 필요해진다.
- EC2 단일 인스턴스 Compose 배포에서 컨테이너 오케스트레이터로 전환한다.
