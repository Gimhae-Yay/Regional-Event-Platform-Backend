# API 명세서

도메인별 API 계약과 공통 HTTP 계약의 진입점이다. 공통 규칙은 한 곳에서만 관리하고,
도메인 API 명세에서는 해당 규칙을 참조한다.

## 공통 계약

모든 API 명세를 읽기 전에 아래 공통 계약을 확인한다.

| 문서 | 소유 범위 |
| --- | --- |
| [API 공통 계약](common/README.md) | 공통 계약 문서의 안내와 적용 원칙 |
| [API 공통 규칙](common/api-conventions.md) | Base URL, 미디어 타입, 시간·식별자와 공통 입력 규칙 |
| [인증·인가](common/authentication.md) | 인증 전달 방식과 역할·지역·소유권 표기 규칙 |
| [응답·오류](common/response-and-error.md) | 공통 성공·오류 응답 구조, HTTP 상태와 오류 코드 작성 규칙 |
| [페이지네이션](common/pagination.md) | 목록 API의 페이지·정렬·필터 계약 |

## P0 도메인 API 명세

도메인 API 명세서가 작성된 경우, 해당 문서가 요청·응답·상태 코드·오류 코드의 단일 기준이다.
`docs/p0/` 문서는 기능 범위와 정책 근거를 제공하며 API 계약을 대체하지 않는다.
도메인 API 명세서가 아직 없으면 `docs/p0/` 문서를 API 명세 작성 근거로 사용한다.

| 도메인 | 도메인 API 명세서 | 기능·정책 기준 문서 | 주요 범위 |
| --- | --- | --- | --- |
| 인증·프로필 | [인증·프로필 API](p0/auth-profile/auth-profile.md) | [인증·프로필](../p0/auth-profile.md) | 회원가입·로그인·로그아웃·회원탈퇴, 역할·지역 권한, 운영자 승인, 개인정보 |
| 지역·콘텐츠 카탈로그 | [지역·콘텐츠 카탈로그 API 명세서](p0/content-catalog/content-catalog.md) | [지역·콘텐츠 카탈로그](../p0/content-catalog.md) | 지역 선택, 콘텐츠·회차, 공개·승인·운영 상태 |
| 운영자 콘텐츠 | [대표 이미지 S3 업로드 URL 발급](p0/content/upload-representative-image.md) · [콘텐츠 생성](p0/content/create-content.md) · [콘텐츠 수정본 생성](p0/content/create-content-revision.md) · [내 콘텐츠 수정](p0/content/update-my-content.md) · [콘텐츠 수정본 편집](p0/content/update-content-revision.md) | [지역·콘텐츠 카탈로그](../p0/content-catalog.md) | 대표 이미지 업로드, 콘텐츠 생성·보완·수정 심사 |
| 지역 콘텐츠 관리자 | [수정본 심사 대기 목록](p0/content-catalog/list-pending-content-revisions.md) · [수정본 심사 상세](p0/content-catalog/review-content-revision-detail.md) · [수정본 승인](p0/region-content/approve-content-revision.md) · [수정본 반려](p0/region-content/reject-content-revision.md) · [콘텐츠 삭제](p0/region-content/delete-content.md) · [운영 중단](p0/region-content/suspend-content.md) | [지역·콘텐츠 카탈로그](../p0/content-catalog.md) | 수정본 심사·공개본 반영, 공개 전 삭제, 운영 중단 |
| 정원 홀드·무료 예약 | [정원 홀드·무료 예약 API 명세서](p0/reservation/reservation.md) | [정원 홀드·무료 예약](../p0/reservation.md) | 정원 홀드, 예약 확정·취소·만료, 동시성·멱등성 |
| 예약 QR·체크인 | [예약 QR·체크인 API 명세서](p0/check-in/check-in.md) | [예약 QR·체크인](../p0/check-in.md) | QR 발급·검증, 체크인, 방문 기록 |
| 인증 후기 | [인증 후기 API](p0/review/review.md) | [인증 후기](../p0/review.md) | 후기 작성·조회·수정·삭제와 원문 파기 |

## P1 도메인 API 명세

P1 API 명세는 `docs/p1/` 소유 문서와 `docs/p1-spec.md`를 기능·정책 기준으로 삼는다.
도메인 API 명세서가 작성된 경우, 해당 문서가 요청·응답·상태 코드·오류 코드의 단일 기준이다.

| 도메인 | 도메인 API 명세서 | 기능·정책 기준 문서 | 주요 범위 |
| --- | --- | --- | --- |
| 지역 | [지역 API 명세서](p1/region/region.md) | [전체관리자](../p1/platform-admin.md), [P1 명세](../p1-spec.md), [P0 ERD](../erd.md), [P1 ERD](../p1-erd.md) | 지역 생성, 지역 공개 여부 변경, 전체 지역 조회 |
| 지역 미션 | [지역 미션 API 명세서](p1/mission/mission.md) | [지역 미션](../p1/regional-mission.md) | 미션 생성·수정·검토 요청·승인 즉시 공개·반려·종료, 공개 조회, 참여, 진행도, 보상 수령, 자동 종료 Scheduler |
| 쿠폰 | [쿠폰 API 명세서](p1/coupon/coupon.md) | [쿠폰](../p1/coupon.md) | 쿠폰 정책 생성·수정·공개·종료, 발급, 내 쿠폰 조회, 사용 가능 판단, 결제 연계 사용 확정, 사용 이력 |
| 결제 | [결제 API 명세서](p1/payment/payment.md) | [유료 결제·환불](../p1/payment-refund.md) | 결제 생성, 웹훅 승인 확인, 결제 상태와 불일치 운영 |
| 환불 | [환불 API 명세서](p1/refund/refund.md) | [유료 결제·환불](../p1/payment-refund.md) | 환불 생성·조회, 실패 조회·재시도·종결 |

## 새 도메인 API 명세 추가

1. [API 명세서 템플릿](api-specification-template.md)을 복사해 `{기능 요구사항 우선순위}/{도메인명}/`에 API 명세 파일을 만든다.
2. 이 문서의 `{기능 요구사항 우선순위}` 도메인 API 명세 표에서 `작성 전` 경로를 해당 파일 링크로 교체한다.
3. 공통 계약에 없는 규칙이 필요하면 도메인 명세에만 중복 작성하지 말고 `common/` 문서의 변경 필요성을 함께 검토한다.
