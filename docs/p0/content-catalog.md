# 지역·콘텐츠 카탈로그

| 항목 | 내용 |
| --- | --- |
| 상위 명세 | [로컬스탬프 P0 명세](../p0-spec.md) |
| 소유 범위 | 지역 탐색, 콘텐츠·회차 등록, 승인, 자동 공개, 수정, 중단, 철회, 종료, 삭제, 회차 운영 상태 |
| API 명세 | [콘텐츠 카탈로그 API](../api/p0/content-catalog/), [콘텐츠 API](../api/p0/content/), [지역 콘텐츠 API](../api/p0/region-content/) |
| 데이터 모델 | [ERD](../erd.md) |

> 이 문서는 지역·콘텐츠·회차 정책의 단일 기준이며, 현재 P0 명세와 채택 ADR을 반영한 구현 계약이다.

## FR-02. 지역 선택과 콘텐츠 탐색

### 참고 문서

| 문서 | 적용 범위 |
| --- | --- |
| [P0 명세](../p0-spec.md#7-기능-요구사항과-소유-문서) | `FR-02` 범위와 공개 콘텐츠 탐색 기준 |
| [ADR-0020](../adr/0020-merge-event-experience-details-into-content-and-revision.md#결정) | 행사·체험 필드를 콘텐츠와 수정본에 통합하는 P0 모델 |
| [정원 홀드·무료 예약](reservation.md#rsv-04) | 상세 화면에 표시할 P0 무료 예약 취소 정책 |
| [인증·프로필](auth-profile.md#prv-02) | 탈퇴 회원 후기를 공통 표시로 노출하는 기준 |

### 기능 범위

- 김해시·동해시 공개 지역 선택과 지역별 홈을 제공한다.
- 행사·체험 콘텐츠 목록, 상세와 유형·예약 가능 여부 필터를 제공한다.
- 예약 가능 여부는 콘텐츠 공개 상태와 회차 운영 상태를 함께 사용한다.
- 상세에는 위치, 운영 시간, 소개, 유의사항, 날짜·회차, 잔여 정원,
  [정원 홀드·무료 예약](reservation.md)의 P0 취소 정책을 안내하는 문구와 인증 후기를 표시한다.
- 탈퇴 회원의 공개 후기는 작성자를 사용자별 가명이 아닌 공통 `탈퇴한 사용자`로 표시한다.

## FR-03·FR-04 연결 유저 시나리오

### US-02. 운영자가 행사·체험을 등록하고 지역 관리자가 승인한다

- **주체:** 행사·체험 운영자, 지역 관리자
- **목표:** 예약 가능한 콘텐츠를 표준 정보와 함께 안전하게 공개한다.
- **선행 조건:** 지역 관리자가 사업자 정보와 요청 지역을 수동 확인해
  운영자 계정을 승인했고 운영자 역할과 담당 지역이 부여돼 있다.

#### 기본 흐름

1. 승인된 운영자가 담당 지역에 콘텐츠를 만들면 서버가 현재 인증 운영자를 `operator_id`로,
   승인된 담당 지역을 `region_id`로 설정한다.
2. 운영자가 대표 이미지, 소개, 위치, 운영 시간, 연락처, 유의사항을 입력한다.
3. 날짜·회차별 정원, 연령·준비물, P0 취소 정책을 안내하는 문구와 공개 예정 시각을 입력한다.
4. 필수 항목 검증을 통과하면 승인 요청을 제출하고 콘텐츠를 `PENDING`으로 전환한다.
5. 담당 지역 관리자가 콘텐츠와 공개 예정 시각을 함께 검토해 승인 또는 반려한다.
6. 승인된 콘텐츠는 `APPROVED` 상태로 유지되며 공개 예정 시각 전에는 일반 사용자에게 노출되지 않는다.
7. 승인된 공개 예정 시각이 되면 시스템이 콘텐츠를 한 번만 `PUBLISHED`로 전환하고 지역 홈과 목록에 노출한다.
8. `APPROVED` 또는 `PUBLISHED` 콘텐츠의 운영자는 추가 회차를 `PENDING`으로 생성하거나 기존 회차 변경안을 별도 심사 요청으로 제출할 수 있다.
   생성 회차는 승인 뒤 `SCHEDULED`가 되고, 심사 중·반려된 수정 요청은 기존 회차와 콘텐츠 공개 상태를 바꾸지 않는다.
9. 연결된 회차가 하나 이상이고 모든 회차가 `COMPLETED`, `CANCELLED`, `REJECTED` 중 하나이면 시스템이 콘텐츠를 한 번만 `ENDED`로 전환해 예약 접수와 노출을 종료한다. `PENDING` 또는 `SCHEDULED` 회차가 있으면 종료하지 않으며, 지역 관리자는 같은 조건에서 스케줄러 실행을 기다리지 않고 정상 종료 처리할 수 있다.

#### 예외 흐름

- 필수 정보가 누락되면 승인 요청을 제출할 수 없다.
- 반려 시 사유를 기록하고 운영자가 보완 후 다시 제출한다.
- 승인 후 공개 예정 시각을 변경하려면 운영자가 변경 요청을 제출하고 지역 관리자의 승인을 다시 받아야 한다.
- 타 지역 콘텐츠 생성, 임의 `operator_id` 지정, 다른 운영자 콘텐츠 변경과 P0 소유권 이전 요청은 거부한다.
- 운영자와 지역 관리자는 담당하지 않은 지역·콘텐츠를 조회하거나 변경할 수 없다.

**적용 정책:** `AUTH-01`, `AUTH-02`, `AUTH-05`, `CON-01`~`CON-09`

## FR-03·FR-04 공통 콘텐츠·회차 상태 정책

### `CON-01`

콘텐츠 기본 상태는 `PENDING → APPROVED → PUBLISHED → ENDED`로 전이한다.
반려 시 `PENDING → REJECTED → PENDING`으로 전이하며 반려 사유를 기록하고 운영자가 보완 후 다시 제출한다.
공개 전 `APPROVED` 콘텐츠의 수정 요청은 수정본 생성과 함께 `APPROVED → PENDING`으로 전이한다.
이 `PENDING`은 수정본 심사가 종결돼도 유지하며, 승인된 공개 전 수정본을 반영할 때만 다시 `APPROVED`로 전이한다.

### `SES-01`

회차 운영 상태는 콘텐츠 공개 상태와 분리하며 P0 상태는
`PENDING → SCHEDULED`, `PENDING → REJECTED`, `SCHEDULED → COMPLETED` 또는 `SCHEDULED → CANCELLED`다.
`PENDING` 콘텐츠는 최초 회차를 콘텐츠 심사에 함께 제출하므로 별도 회차 생성·수정 API를 만들 수 없다. 소프트 삭제되지 않은
`APPROVED` 또는 `PUBLISHED` 콘텐츠에서만 소유 운영자가 추가 `PENDING` 회차를 생성할 수 있다. `PENDING` 회차는
공개·예약 대상이 아니며 담당 지역 관리자가 승인한 뒤에만 `SCHEDULED`가 된다.
MySQL 현재 시각보다 `starts_at`이 미래인 기존 `SCHEDULED` 회차의 수정 후보만 `session_revision`에 분리해
`PENDING → APPROVED` 또는 `PENDING → REJECTED`로 전이한다. 수정 요청은 심사 중·반려 시 기존 `SCHEDULED` 회차를
유지하며, 승인 때 대상 회차의 시작 전 여부·버전 일치와 활성 홀드·`CONFIRMED`·`CHECKED_IN` 예약 부재를 다시 확인한다.
회차 종료 이후 체크인 창이 닫히고 미체크인 예약의 노쇼 처리가 끝나면 `COMPLETED`로 전환한다.
콘텐츠 상태 변경만으로 회차를 자동 취소하지 않고, 명시적 회차 취소는
[정원 홀드·무료 예약](reservation.md)의 `RSV-06`을 적용한다.

## FR-03. 콘텐츠·회차 등록

### 참고 문서

| 문서 | 적용 범위 |
| --- | --- |
| [인증·프로필](auth-profile.md#fr-09-운영자-승인정보-마스킹) | 운영자 승인 상태, 담당 지역과 최초 소유자 설정 |
| [ADR-0002](../adr/0002-isolate-regions-in-a-shared-schema.md#결정) | 지역 범위와 저장된 소유 관계의 검증 |
| [ADR-0020](../adr/0020-merge-event-experience-details-into-content-and-revision.md#결정) | 행사·체험 필드를 통합한 콘텐츠·수정본·회차의 모델 경계 |
| [ADR-0011](../adr/0011-bootstrap-operator-ownership-on-content-creation.md#결정) | 서버가 인증 운영자와 승인된 담당 지역으로 최초 소유 관계를 생성하는 방식 |
| [ADR-0038](../adr/0038-create-sessions-with-lifecycle-and-review-session-changes.md#결정) | 추가 회차는 상태 전이로 생성하고 기존 회차 수정만 별도 심사하는 정책 |
| [P0 명세](../p0-spec.md#88-감사-및-운영-로그) | 최초 소유자 설정과 상태 전이 감사 요건 |

### 기능 범위

- 승인된 운영자는 담당 지역에 콘텐츠를 생성하며 서버가 인증 운영자를 최초 소유자로 설정한다.
- 운영자는 소유 콘텐츠의 표준 필수 정보, 회차별 일정·정원과 공개 예정 시각을 등록한다.
- 신규 회차의 상태는 `PENDING`이며, 담당 지역 관리자가 승인한 뒤에만 `SCHEDULED`가 된다.
- 콘텐츠가 `APPROVED` 또는 `PUBLISHED`가 된 뒤에는 새 `PENDING` 회차를 생성할 수 있고, `SCHEDULED` 회차의
  일정·체크인 창·정원 변경만 별도 심사 요청으로 제출한다. 심사 중인 변경안은 현재 회차를 바꾸지 않는다.
- 필수 검증을 통과한 콘텐츠만 승인 요청할 수 있다.

### 콘텐츠·회차 등록 정책

#### `CON-02`

승인된 운영자는 자신이 소유한 콘텐츠의 대표 이미지, 소개, 위치, 운영 시간, 연락처, 유의사항, 콘텐츠 공통 예약 가격, 날짜·회차별 정원,
연령·준비물, P0 취소 정책을 안내하는 문구와 공개 예정 시각의 필수 검증을 통과해야 승인 요청할 수 있다.
취소 기준은 고정된 무료 예약 취소 정책의 표시 문구이며, 운영자가 취소 가능 시점·인원 변경 또는
금전·환불 정책을 변경하는 수단이 아니다.
콘텐츠가 `PENDING`이면 최초 회차도 콘텐츠 승인 범위에 포함하므로 별도 회차 생성·수정 API를 허용하지 않는다.
콘텐츠가 `APPROVED` 또는 `PUBLISHED`이면 소유 운영자가 실제 회차를 `PENDING`으로 생성하고, 담당 지역 관리자가
승인한 회차만 `SCHEDULED`가 된다. 기존 `SCHEDULED` 회차의 변경 후보만 `session_revision`으로 제출한다.

## FR-04. 승인·자동 공개·종료

### 참고 문서

| 문서 | 적용 범위 |
| --- | --- |
| [인증·프로필](auth-profile.md#fr-01-인증역할지역-권한) | 담당 지역 관리자와 소유 운영자의 권한 경계 |
| [정원 홀드·무료 예약](reservation.md#rsv-06) | 회차 취소 시 활성 홀드·확정 예약 처리 |
| [P0 명세](../p0-spec.md#88-감사-및-운영-로그) | 승인·자동 공개·종료 처리자와 상태 이력 감사 |
| [ADR-0021](../adr/0021-record-content-reasons-in-content-log.md#결정) | 콘텐츠 사유를 상태 로그에 기록하고 현재 상태와 분리하는 모델 |
| [ADR-0059](../adr/0059-automatically-end-content-after-all-sessions-terminate.md#결정) | 모든 회차 종결을 기준으로 한 콘텐츠 자동 종료와 조정 스케줄러 |
| [ADR-0060](../adr/0060-serialize-content-ending-and-session-creation-with-content-lock.md#결정) | 자동·수동 종료와 추가 회차 생성을 같은 콘텐츠 행 잠금으로 처리하는 규칙 |
| [ADR-0061](../adr/0061-treat-rejected-sessions-as-terminal-for-content-ending.md#결정) | `REJECTED` 회차를 콘텐츠 종료 판정의 종결 상태로 처리하는 규칙 |
| [ADR-0062](../adr/0062-coordinate-content-ending-with-usecase.md#결정) | 별도 Scheduler와 수동 Controller가 같은 종료 UseCase를 호출하고 콘텐츠 한 건 단위 트랜잭션을 사용하는 규칙 |

### 기능 범위

- 지역 관리자는 콘텐츠와 공개 예정 시각을 함께 승인하거나 사유와 함께 반려한다.
- 지역 관리자는 `PENDING` 회차를 승인하거나 사유와 함께 반려한다. `session_revision`의 수정 후보도 승인하거나
  사유와 함께 반려하며, 수정 승인 시에는 대상 회차가 아직 시작 전인지, 버전이 일치하는지, 활성 홀드와
  `CONFIRMED`·`CHECKED_IN` 예약이 없는지를 다시 확인한 뒤에만 반영한다.
- 시스템은 승인된 공개 예정 시각에 콘텐츠를 한 번만 자동 공개한다.
- 연결된 회차가 하나 이상이고 모든 회차가 `COMPLETED`, `CANCELLED`, `REJECTED` 중 하나이면 시스템이 콘텐츠를
  한 번만 자동 종료한다. `PENDING` 또는 `SCHEDULED` 회차가 있으면 종료하지 않는다.
  지역 관리자의 정상 종료 요청은 같은 조건에서 스케줄러 실행을 기다리지 않고 동일한 상태 전이와 이력을 남긴다.

### 승인·자동 공개·종료 정책

#### `CON-03`

운영자가 지정한 공개 예정 시각과 콘텐츠를 지역 관리자가 함께 승인한다.
`APPROVED`는 일반 사용자에게 노출하지 않으며
시스템은 승인된 `publish_at`에 콘텐츠를 한 번만 `PUBLISHED`로 전환한다.
실제 공개 시각은 `status = PUBLISHED`인 `content_log` 행의 `date`이며, `content`에 별도 `published_at`을 저장하지 않는다.

#### `CON-04`

승인되었지만 아직 공개되지 않은 콘텐츠의 공개 예정 시각을 변경하려면 운영자가 수정본을 제출한다.
이때 원본은 `APPROVED → PENDING`으로 전이해 기존 `publish_at`에 따른 자동 공개를 차단한다.
수정본 승인 시 후보 `publish_at`을 원본에 반영하고 `PENDING → APPROVED`로 재승인한다.
수정본 반려·철회 시 원본은 `PENDING`으로 유지하며 자동 공개를 재개하지 않는다.
연결된 회차가 하나 이상이고 모든 회차가 `COMPLETED`, `CANCELLED`, `REJECTED` 중 하나이면 시스템이 콘텐츠를
한 번만 `ENDED`로 전환하고 신규 예약 접수와 노출을 종료한다. `PENDING` 또는 `SCHEDULED` 회차가 있으면 종료하지
않는다. 지역 관리자의 정상 종료 요청은 같은 조건에서 스케줄러 실행을 기다리지 않고 동일 전이를 수행한다. 기존
`CONFIRMED` 예약을 취소해야 하면 먼저 명시적으로 회차를 취소한다.
자동·수동 종료와 추가 회차 생성은 같은 `content` 행을 먼저 잠그고, 잠금을 얻은 뒤 콘텐츠와 전체 회차 상태를
다시 확인한다. 따라서 `ENDED` 콘텐츠와 새 `PENDING` 회차를 함께 커밋할 수 없다.
자동 종료의 `@Scheduled` 실행은 별도 Scheduler가 담당하고, Scheduler와 수동 Controller는 같은
`EndContentReservationsUseCase`를 호출한다. UseCase는 콘텐츠 한 건마다 트랜잭션을 열어 각 Service의 작업을
조정하며, Scheduler와 Controller는 개별 Service를 직접 호출하지 않는다.

### 완료 기준

- [AC-08 승인·자동 공개](../p0-spec.md#9-테스트-및-출시-수용-기준)
- [AC-19 모든 회차 종결 콘텐츠 자동 종료](../p0-spec.md#9-테스트-및-출시-수용-기준)

## FR-14. 콘텐츠 수정·중단·삭제

### 참고 문서

| 문서                                                                             | 적용 범위                       |
|--------------------------------------------------------------------------------|-----------------------------|
| [인증·프로필](auth-profile.md#fr-01-인증역할지역-권한)                                      | 담당 지역·소유 관계 기반의 변경 권한       |
| [ADR-0011](../adr/0011-bootstrap-operator-ownership-on-content-creation.md#결정) | 저장된 최초 소유 관계를 기준으로 한 운영자 권한 |
| [ADR-0020](../adr/0020-merge-event-experience-details-into-content-and-revision.md#결정) | 행사·체험 후보 필드를 수정본에 통합하는 모델 |
| [ADR-0037](../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md#결정) | 공개 전 수정 심사 중 자동 공개 차단과 후보 공개 예정 시각 |
| [P0 명세](../p0-spec.md#88-감사-및-운영-로그)                                           | 수정본 철회·중단·철회·삭제 상태 전이 감사    |
| [ADR-0021](../adr/0021-record-content-reasons-in-content-log.md#결정)              | 콘텐츠 사유를 상태 로그에 기록하고 현재 상태와 분리하는 모델 |

### 기능 범위

- 공개 콘텐츠 수정 심사 중에는 기존 공개본을 유지하고 승인된 수정본만 반영하며,
  소유 운영자는 심사 결정 전 수정본을 `EDIT_WITHDRAWN`으로 철회할 수 있다.
- 지역 관리자의 운영 중단, 운영자의 철회 요청, 상태별 삭제 제한과 감사 이력을 적용한다.

### 콘텐츠 수정·중단·삭제 정책

#### `CON-05`

`PUBLISHED` 콘텐츠는 직접 수정하지 않는다. 소유 운영자는 수정본을 `EDIT_REQUESTED`로 제출하고,
담당 지역 관리자는 `EDIT_REQUESTED → EDIT_APPROVED` 또는 `EDIT_REQUESTED → EDIT_REJECTED`로 심사한다.
공개 콘텐츠에서 만든 수정본의 `publish_at`은 `NULL`이며, 승인돼도 원본의 공개 상태와 기존 `publish_at`은 유지한다.

공개 전 `APPROVED` 콘텐츠도 수정본을 제출할 수 있다. 이 수정본의 후보 `publish_at`은 필수이고,
생성과 함께 원본을 `APPROVED → PENDING`으로 전이해 자동 공개를 막는다. 그 뒤 `EDIT_REJECTED` 또는
`EDIT_WITHDRAWN`이 되어도 원본은 `PENDING`으로 유지한다. 운영자는 활성 수정본이 없고 직전 공개 전 수정
요청으로 `APPROVED → PENDING`이 기록된 콘텐츠에 새 수정본을 다시 제출할 수 있다. 최초 등록 뒤의 일반
`PENDING` 콘텐츠에는 이 예외를 적용하지 않는다.

공개 전 수정본 승인에는 원본 `PENDING`, 수정본 `EDIT_REQUESTED`, 기준 원본 버전 일치와 후보 `publish_at` 존재가
필요하다. 성공 시 모든 후보 필드, 후보 `reservation_price`와 `publish_at`을 원본에 반영하고 원본을 `PENDING → APPROVED`로 전이한다.
공개 콘텐츠 수정본 승인에는 원본 `PUBLISHED`, 수정본 `EDIT_REQUESTED`, 기준 원본 버전 일치와 후보 `publish_at = NULL`이
필요하며, 원본 상태와 `publish_at`은 변경하지 않는다. 두 경우 모두 후보 `reservation_price`를 원본에 반영하며, 원본 반영, 원본 버전 증가, 수정본 종결과
감사 기록은 하나의 트랜잭션으로 처리한다. 이미 생성된 `reservation_price_snapshot`은 가격 변경으로 수정하지 않는다.

소유 운영자는 심사 결정 전에 `EDIT_REQUESTED → EDIT_WITHDRAWN`으로 철회할 수 있고,
철회 시각·처리자·사유를 기록하며 반복 철회 요청은 기존 결과를 반환한다.
승인·반려·철회는 모두 `EDIT_REQUESTED` 상태를 조건으로 전이하며 경합하면 성공한 최초 전이만 적용한다.
승인·반려가 먼저 성공하면 이후 철회를 거부하고, 철회가 먼저 성공하면 이후 승인·반려를 거부한다.
`EDIT_REJECTED`와 `EDIT_WITHDRAWN` 수정본은 원본 후보 필드를 반영하지 않고 상태와 사유 이력을 보존한다.
`EDIT_APPROVED` 반영 후에는 기존 수정본을 철회하지 않고 새 수정본 또는 전체 콘텐츠 철회 절차를 사용한다.
수정본의 관계형 리비전 영속 모델과 승인 시 원자 반영은
[ADR-0014](../adr/0014-store-published-content-edits-in-relational-revision-tables.md)와
[ADR-0037](../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md)를 따르며,
행사·체험 후보 필드를 수정본에 함께 저장하는 방식은
[ADR-0020](../adr/0020-merge-event-experience-details-into-content-and-revision.md)를 따른다.

#### `CON-06`

지역 관리자는 공개 콘텐츠를 `PUBLISHED → SUSPENDED`로 전환할 수 있다.
`SUSPENDED` 상태의 `content_log`에 중단 시각, 처리자와 사유를 기록하고 방문자에게 최신 사유를 표시한다.
신규 홀드를 차단하고 `ACTIVE` 홀드를 무효화해 정원을 한 번 복구하지만,
기존 `CONFIRMED` 예약은 명시적인 회차 취소가 없으면 유지한다.

#### `CON-07`

운영자는 자신이 소유한 공개 콘텐츠만 전체 콘텐츠 철회를 요청할 수 있다.
지역 관리자가 승인하면 `PUBLISHED → WITHDRAWN`으로 전환하고 `WITHDRAWN` `content_log`에 철회 시각·처리자·사유를 보존한다.
신규 홀드를 차단하고 `ACTIVE` 홀드를 무효화해 정원을 한 번 복구하지만,
기존 `CONFIRMED` 예약은 명시적인 회차 취소가 없으면 유지한다.
전체 콘텐츠 `WITHDRAWN`은 수정본 `EDIT_WITHDRAWN`과 다른 상태다.

#### `CON-08`

담당 지역 관리자만 공개 전 콘텐츠를 상태별로 삭제한다.
`PENDING`·`APPROVED`는 `content.deleted_at`을 기록하고 `content_log`에 `status = DELETED`, 처리자와 사유를 추가해 소프트 삭제한다.
소프트 삭제는 허용 상태와 `deleted_at IS NULL`을 조건으로 한 최초 처리만 성공하며 삭제 뒤에는
승인·자동 게시·복구와 다른 상태 전이를 허용하지 않는다.
자동 게시와 삭제가 경합하면 `deleted_at IS NULL` 및 현재 상태 조건을 먼저 충족해 커밋한 처리만 성공한다.
소유 운영자의 직접 삭제와 타 지역 관리자의 삭제는 거부한다.
`PUBLISHED`·`SUSPENDED`·`WITHDRAWN`·`ENDED`는 삭제하지 않고 상태와 사유 이력을 보존한다.

### 완료 기준

- [AC-09 공개본을 유지한 수정 심사](../p0-spec.md#9-테스트-및-출시-수용-기준)
- [AC-10 상태별 삭제 제한](../p0-spec.md#9-테스트-및-출시-수용-기준)
- [AC-11 운영 중단 이력과 안내](../p0-spec.md#9-테스트-및-출시-수용-기준)

## FR-03·FR-04·FR-14 공통 감사 정책

### `CON-09`

최초 콘텐츠 소유자 설정, 승인·반려·자동 공개·수정 심사·수정본 `EDIT_WITHDRAWN`,
운영 중단·전체 콘텐츠 `WITHDRAWN`·종료·삭제의 상태 전이는 감사 대상이다.
공통 감사 필드와 재현 요건은 [P0 명세](../p0-spec.md#88-감사-및-운영-로그)의 8.8절을 적용한다.

### 완료 기준

- [AC-12 콘텐츠 상태 변경 감사 이력](../p0-spec.md#9-테스트-및-출시-수용-기준)
- [AC-17 핵심 상태 전이 재현](../p0-spec.md#9-테스트-및-출시-수용-기준)

## 콘텐츠 상태와 예약 연계 정책

### `SES-02`

신규 홀드는 콘텐츠가 `PUBLISHED`, 회차가 `SCHEDULED`이고 MySQL 기준 시각이 회차 시작 전일 때만 허용한다.
콘텐츠가 `SUSPENDED`·`WITHDRAWN`·`ENDED`로 먼저 전환되면 신규 홀드를 차단하고
`ACTIVE` 홀드를 `INVALIDATED`로 전환해 정원을 한 번 복구하되
기존 `CONFIRMED`·`CHECKED_IN` 예약과 방문·후기는 유지한다.
콘텐츠 전이와 예약 확정이 경합하면 먼저 성공한 조건부 상태 전이를 기준으로 다른 요청을 거부한다.

## 전체 콘텐츠 기능 공통 연계 기준

권한 정책은 [인증·프로필](auth-profile.md#권한-및-개인정보-정책)의
`AUTH-01`~`AUTH-03`, `AUTH-05`를 적용한다.
최초 소유 관계는 [ADR-0011](../adr/0011-bootstrap-operator-ownership-on-content-creation.md)을 따른다.

## FR-02·FR-03·FR-04·FR-14 데이터 요구사항

| 데이터 | 핵심 식별·연결 정보 | 용도 |
| --- | --- | --- |
| 지역 | `region_id`, 공개 상태 | 지역 선택과 공개 범위 |
| 콘텐츠 | `content_id`, `region_id`, type, status, operator_id, `reservation_price`, 행사·체험 표시 필드(연령 조건·준비물·취소 안내), publish_at, deleted_at | 탐색·승인·자동 공개, 서버가 설정한 소유 관계와 공개 전 소프트 삭제 현재 상태 |
| 콘텐츠 수정본 | `content_revision_id`, content_id, editor_id, status, 후보 `reservation_price`, 행사·체험 후보 표시 필드, 후보 `publish_at`, submitted_at, reviewed_at, withdrawn_at, withdrawn_by, withdrawal_reason | 공개·공개 전 승인본을 분리한 수정 심사·철회 |
| 콘텐츠 상태 로그 | id, `content_id`, `actor_id`, status, reason, date | 생성·승인·자동 공개·중단·철회·종료·삭제의 처리자, 사유와 상태 변경 시각 |
| 행사·체험 회차 | `session_id`, `content_id`, 시작·종료 시각, 정원, status, 체크인 시작·종료 시각 | 무료 예약 마감·QR 가능 단위 |
| 회차 수정 심사 요청 | `session_revision_id`, content_id, target_session_id, 후보 일정·정원, base_session_version, status, submitted_at, reviewed_at | 기존 `SCHEDULED` 회차 변경의 심사 후보. 승인 때만 실제 회차에 반영 |

세부 컬럼, 관계와 DB 제약은 [ERD](../erd.md)를 기준으로 한다.
