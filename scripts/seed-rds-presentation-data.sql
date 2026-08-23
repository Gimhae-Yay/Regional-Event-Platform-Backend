-- 프레젠테이션용 전체 시드 데이터입니다.
-- 실행 전 기존 애플리케이션 데이터를 비운 뒤 한 번만 실행합니다.
-- 대상 DB: local_stamp (MySQL 8.0)
-- 시드 계정 공통 비밀번호: Test!23456

USE local_stamp;

SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

SET @now = UTC_TIMESTAMP(6);
SET @password_hash = '{bcrypt}$2a$12$ssgGGtMwA9aYPG.BtDlUaOVh6oJC19qJkadq6seaZSh/xLpYuxX.u';

-- 지역과 계정
INSERT INTO region (region_code, name, is_public, created_at, updated_at) VALUES
    ('GIMHAE', '김해', TRUE, @now, @now),
    ('DONGHAE', '동해', TRUE, @now, @now);

SET @gimhae_region_id = (SELECT region_id FROM region WHERE region_code = 'GIMHAE');
SET @donghae_region_id = (SELECT region_id FROM region WHERE region_code = 'DONGHAE');

INSERT INTO app_user (
    login_identifier, password_hash, name, phone, status, account_kind, created_at, updated_at
) VALUES
    ('gimhae-operator@example.test', @password_hash, '김해 운영자', '010-7000-0001', 'ACTIVE', 'ORDINARY', @now, @now),
    ('donghae-operator@example.test', @password_hash, '동해 운영자', '010-7000-0002', 'ACTIVE', 'ORDINARY', @now, @now),
    ('gimhae-admin@example.test', @password_hash, '김해 지역 관리자', '010-7000-0003', 'ACTIVE', 'ORDINARY', @now, @now),
    ('donghae-admin@example.test', @password_hash, '동해 지역 관리자', '010-7000-0004', 'ACTIVE', 'ORDINARY', @now, @now),
    ('minji@example.test', @password_hash, '김민지', '010-7000-0011', 'ACTIVE', 'ORDINARY', @now, @now),
    ('junho@example.test', @password_hash, '박준호', '010-7000-0012', 'ACTIVE', 'ORDINARY', @now, @now),
    ('sora@example.test', @password_hash, '이소라', '010-7000-0013', 'ACTIVE', 'ORDINARY', @now, @now),
    ('taeyang@example.test', @password_hash, '최태양', '010-7000-0014', 'ACTIVE', 'ORDINARY', @now, @now),
    ('platform-admin@example.test', @password_hash, '플랫폼 관리자', '010-7000-0099', 'ACTIVE', 'PRIVILEGED', @now, @now);

SET @gimhae_operator_id = (SELECT user_id FROM app_user WHERE login_identifier = 'gimhae-operator@example.test');
SET @donghae_operator_id = (SELECT user_id FROM app_user WHERE login_identifier = 'donghae-operator@example.test');
SET @gimhae_admin_id = (SELECT user_id FROM app_user WHERE login_identifier = 'gimhae-admin@example.test');
SET @donghae_admin_id = (SELECT user_id FROM app_user WHERE login_identifier = 'donghae-admin@example.test');
SET @minji_id = (SELECT user_id FROM app_user WHERE login_identifier = 'minji@example.test');
SET @junho_id = (SELECT user_id FROM app_user WHERE login_identifier = 'junho@example.test');
SET @sora_id = (SELECT user_id FROM app_user WHERE login_identifier = 'sora@example.test');
SET @taeyang_id = (SELECT user_id FROM app_user WHERE login_identifier = 'taeyang@example.test');
SET @platform_admin_id = (SELECT user_id FROM app_user WHERE login_identifier = 'platform-admin@example.test');

INSERT INTO user_role_assignment (user_id, role, region_id, status, granted_at) VALUES
    (@gimhae_operator_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@gimhae_operator_id, 'OPERATOR', @gimhae_region_id, 'ACTIVE', @now),
    (@donghae_operator_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@donghae_operator_id, 'OPERATOR', @donghae_region_id, 'ACTIVE', @now),
    (@gimhae_admin_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@gimhae_admin_id, 'REGION_ADMIN', @gimhae_region_id, 'ACTIVE', @now),
    (@donghae_admin_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@donghae_admin_id, 'REGION_ADMIN', @donghae_region_id, 'ACTIVE', @now),
    (@minji_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@junho_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@sora_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@taeyang_id, 'VISITOR', NULL, 'ACTIVE', @now);

INSERT INTO platform_admin_assignment (
    user_id, grade, status, granted_at, inactivated_at, inactive_reason_code
) VALUES (
    @platform_admin_id, 'SUPER_ADMIN', 'ACTIVE', @now, NULL, NULL
);

INSERT INTO operator_application (
    applicant_user_id, requested_region_id, business_information, status, inspected_user_id,
    rejected_reason, created_at, updated_at
) VALUES
    (@gimhae_operator_id, @gimhae_region_id, '김해 지역 문화 체험 운영', 'APPROVED', @gimhae_admin_id, NULL, DATE_SUB(@now, INTERVAL 100 DAY), DATE_SUB(@now, INTERVAL 95 DAY)),
    (@donghae_operator_id, @donghae_region_id, '동해 관광 체험 운영', 'APPROVED', @donghae_admin_id, NULL, DATE_SUB(@now, INTERVAL 100 DAY), DATE_SUB(@now, INTERVAL 95 DAY));

-- 대표 이미지 연결 대상 콘텐츠: 지역별 7개(콘텐츠 상태: ENDED 1, PUBLISHED 6)
-- PUBLISHED 콘텐츠는 회차 시점 기준으로 종료 1, 진행 중 3, 시작 전 3개를 구성합니다.
-- 아래에는 관리자·예외 상태 화면을 위한 추가 콘텐츠를 함께 생성합니다.
INSERT INTO content (
    region_id, operator_id, content_type, status, version_no, title, description,
    location_text, operating_hours_text, contact_text, precautions, age_requirement,
    materials, cancellation_policy_text, publish_at, deleted_at, created_at, updated_at,
    reservation_price
) VALUES
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'ENDED', 1,
        '가야금과 함께 걷는 김해 역사 여행', '가야 문화유산을 둘러보며 가야금 연주를 듣는 해설 프로그램입니다.',
        '김해 대성동고분박물관 앞', '토요일 10:00~12:00', '010-7000-0001', '편한 신발을 착용해 주세요.', '전 연령', '해설 자료와 간단한 기념품', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 90 DAY), NULL, DATE_SUB(@now, INTERVAL 90 DAY), DATE_SUB(@now, INTERVAL 4 DAY), 0),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '김해 도예 체험', '직접 빚고 색칠하는 생활 도자기 체험입니다.',
        '김해시 문화센터 공방', '화요일~일요일 10:00~18:00', '010-7000-0001', '앞치마를 제공하지만 편한 복장을 권장합니다.', '만 7세 이상', '흙, 유약, 앞치마 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 60 DAY), NULL, DATE_SUB(@now, INTERVAL 60 DAY), @now, 12000),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '김해 야간 산책', '문화해설사와 함께 김해 원도심의 야경을 걷는 프로그램입니다.',
        '봉리단길 관광안내소', '금요일~일요일 18:30~20:30', '010-7000-0001', '우천 시 우산을 준비해 주세요.', '전 연령', '개인 물병', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 60 DAY), NULL, DATE_SUB(@now, INTERVAL 60 DAY), @now, 0),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '대성동고분박물관 깊이 보기', '전시 유물과 고분군을 함께 살펴보는 심화 해설입니다.',
        '대성동고분박물관', '수요일~일요일 14:00~16:00', '010-7000-0001', '박물관 내부에서는 정숙해 주세요.', '만 10세 이상', '해설 자료 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 60 DAY), NULL, DATE_SUB(@now, INTERVAL 60 DAY), @now, 0),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '낙동강 카약 일몰 체험', '해 질 무렵 낙동강을 따라 노를 저으며 즐기는 카약 체험입니다.',
        '대동생태체육공원 선착장', '토요일 17:00~19:00', '010-7000-0001', '구명조끼 착용은 필수입니다.', '만 14세 이상', '카약, 패들, 구명조끼 제공', '회차 시작 48시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 30 DAY), NULL, DATE_SUB(@now, INTERVAL 30 DAY), @now, 18000),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '봉리단길 베이킹 클래스', '김해 쌀가루로 만드는 지역 디저트 베이킹 수업입니다.',
        '봉리단길 공유주방', '일요일 13:00~15:00', '010-7000-0001', '알레르기 유발 재료를 사전에 알려 주세요.', '만 12세 이상', '재료와 포장 상자 제공', '회차 시작 48시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 30 DAY), NULL, DATE_SUB(@now, INTERVAL 30 DAY), @now, 22000),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '화포천 습지 생태 탐방', '전문 해설사와 새와 습지를 관찰하는 아침 탐방입니다.',
        '화포천습지생태공원', '토요일 09:00~11:00', '010-7000-0001', '망원경이 있으면 가져와 주세요.', '전 연령', '관찰 노트 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 30 DAY), NULL, DATE_SUB(@now, INTERVAL 30 DAY), @now, 5000),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'ENDED', 1,
        '묵호항 새벽 경매 체험', '묵호항의 새벽 경매를 보고 수산물 이야기를 듣는 프로그램입니다.',
        '묵호항 수산시장 입구', '토요일 05:30~07:00', '010-7000-0002', '따뜻한 외투를 준비해 주세요.', '만 12세 이상', '따뜻한 음료 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 90 DAY), NULL, DATE_SUB(@now, INTERVAL 90 DAY), DATE_SUB(@now, INTERVAL 4 DAY), 0),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '동해 해변 요가', '동해 바다를 보며 진행하는 아침 요가 수업입니다.',
        '망상해변 잔디광장', '토요일 08:00~09:30', '010-7000-0002', '요가 매트와 물을 준비해 주세요.', '만 12세 이상', '초보자용 블록 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 60 DAY), NULL, DATE_SUB(@now, INTERVAL 60 DAY), @now, 0),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '도째비골 스카이워크 해설', '도째비골의 지형과 바다 풍경을 함께 읽는 전망대 해설입니다.',
        '도째비골 스카이밸리', '금요일~일요일 15:00~16:30', '010-7000-0002', '고소공포증이 있으면 참여 전 안내해 주세요.', '만 10세 이상', '해설 자료 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 60 DAY), NULL, DATE_SUB(@now, INTERVAL 60 DAY), @now, 0),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '무릉별유천지 암벽 체험', '안전 장비를 갖추고 기초 동작을 배우는 암벽 체험입니다.',
        '무릉별유천지 체험장', '토요일 13:00~15:00', '010-7000-0002', '안내자의 안전 지시를 따라 주세요.', '만 14세 이상', '헬멧과 안전 장비 제공', '회차 시작 48시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 60 DAY), NULL, DATE_SUB(@now, INTERVAL 60 DAY), @now, 0),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '망상 해변 서핑 입문', '파도 읽기부터 패들링까지 배우는 초보 서핑 체험입니다.',
        '망상해변 서핑존', '일요일 10:00~12:00', '010-7000-0002', '수영 가능 여부를 확인해 주세요.', '만 14세 이상', '보드와 웻슈트 제공', '회차 시작 48시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 30 DAY), NULL, DATE_SUB(@now, INTERVAL 30 DAY), @now, 14000),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '천곡황금박쥐동굴 탐험', '동굴 생태와 지질을 관찰하는 가족 탐험 프로그램입니다.',
        '천곡황금박쥐동굴 매표소', '토요일 14:00~16:00', '010-7000-0002', '미끄럽지 않은 신발을 착용해 주세요.', '만 7세 이상', '안전모 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 30 DAY), NULL, DATE_SUB(@now, INTERVAL 30 DAY), @now, 8000),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'PUBLISHED', 1,
        '논골담길 사진 산책', '골목 풍경을 기록하며 걷는 스마트폰 사진 산책입니다.',
        '논골담길 관광안내소', '일요일 16:00~18:00', '010-7000-0002', '스마트폰 충전 상태를 확인해 주세요.', '전 연령', '사진 미션 카드 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 30 DAY), NULL, DATE_SUB(@now, INTERVAL 30 DAY), @now, 6000),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'PENDING', 1,
        '김해 가을 문화 해설', '관리자 심사를 기다리는 가을 문화 해설 프로그램입니다.',
        '김해한옥체험관', '토요일 14:00~16:00', '010-7000-0001', '심사 완료 후 참가 안내를 확인해 주세요.', '전 연령', '해설 자료 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_ADD(@now, INTERVAL 14 DAY), NULL, DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 2 DAY), 7000),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'REJECTED', 1,
        '김해 야외 음악 체험', '심사에서 반려된 야외 음악 체험 프로그램입니다.',
        '김해 시민공원', '일요일 17:00~19:00', '010-7000-0001', '운영 계획 보완 후 다시 신청할 수 있습니다.', '전 연령', '간단한 악기 체험', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_ADD(@now, INTERVAL 21 DAY), NULL, DATE_SUB(@now, INTERVAL 4 DAY), DATE_SUB(@now, INTERVAL 3 DAY), 9000),
    (@gimhae_region_id, @gimhae_operator_id, 'EVENT_EXPERIENCE', 'APPROVED', 1,
        '김해 전통차 시음 체험', '승인되어 공개를 기다리는 전통차 시음 체험입니다.',
        '김해 전통문화관', '토요일 11:00~12:30', '010-7000-0001', '뜨거운 차를 다룰 때 안내를 따라 주세요.', '전 연령', '전통차와 다과 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_ADD(@now, INTERVAL 10 DAY), NULL, DATE_SUB(@now, INTERVAL 5 DAY), DATE_SUB(@now, INTERVAL 1 DAY), 8000),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'SUSPENDED', 1,
        '동해 해안 생태 관찰', '기상 점검으로 일시 중단된 해안 생태 관찰 프로그램입니다.',
        '추암해변 탐방로', '토요일 10:00~12:00', '010-7000-0002', '재개 일정은 별도 공지합니다.', '전 연령', '관찰 노트 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 20 DAY), NULL, DATE_SUB(@now, INTERVAL 20 DAY), DATE_SUB(@now, INTERVAL 1 DAY), 6000),
    (@donghae_region_id, @donghae_operator_id, 'EVENT_EXPERIENCE', 'WITHDRAWN', 1,
        '동해 항구 드로잉 체험', '운영자가 철회를 요청해 종료된 항구 드로잉 체험입니다.',
        '묵호항 문화광장', '일요일 15:00~17:00', '010-7000-0002', '운영 철회된 프로그램입니다.', '만 10세 이상', '드로잉 도구 제공', '회차 시작 24시간 전까지 취소할 수 있습니다.',
        DATE_SUB(@now, INTERVAL 15 DAY), NULL, DATE_SUB(@now, INTERVAL 15 DAY), DATE_SUB(@now, INTERVAL 2 DAY), 10000);

SET @gimhae_ended_content_id = (SELECT content_id FROM content WHERE title = '가야금과 함께 걷는 김해 역사 여행');
SET @gimhae_pottery_content_id = (SELECT content_id FROM content WHERE title = '김해 도예 체험');
SET @gimhae_walk_content_id = (SELECT content_id FROM content WHERE title = '김해 야간 산책');
SET @gimhae_museum_content_id = (SELECT content_id FROM content WHERE title = '대성동고분박물관 깊이 보기');
SET @gimhae_kayak_content_id = (SELECT content_id FROM content WHERE title = '낙동강 카약 일몰 체험');
SET @gimhae_baking_content_id = (SELECT content_id FROM content WHERE title = '봉리단길 베이킹 클래스');
SET @gimhae_wetland_content_id = (SELECT content_id FROM content WHERE title = '화포천 습지 생태 탐방');
SET @donghae_ended_content_id = (SELECT content_id FROM content WHERE title = '묵호항 새벽 경매 체험');
SET @donghae_yoga_content_id = (SELECT content_id FROM content WHERE title = '동해 해변 요가');
SET @donghae_skywalk_content_id = (SELECT content_id FROM content WHERE title = '도째비골 스카이워크 해설');
SET @donghae_climbing_content_id = (SELECT content_id FROM content WHERE title = '무릉별유천지 암벽 체험');
SET @donghae_surf_content_id = (SELECT content_id FROM content WHERE title = '망상 해변 서핑 입문');
SET @donghae_cave_content_id = (SELECT content_id FROM content WHERE title = '천곡황금박쥐동굴 탐험');
SET @donghae_photo_content_id = (SELECT content_id FROM content WHERE title = '논골담길 사진 산책');
SET @gimhae_pending_content_id = (SELECT content_id FROM content WHERE title = '김해 가을 문화 해설');
SET @gimhae_rejected_content_id = (SELECT content_id FROM content WHERE title = '김해 야외 음악 체험');
SET @gimhae_approved_content_id = (SELECT content_id FROM content WHERE title = '김해 전통차 시음 체험');
SET @donghae_suspended_content_id = (SELECT content_id FROM content WHERE title = '동해 해안 생태 관찰');
SET @donghae_withdrawn_content_id = (SELECT content_id FROM content WHERE title = '동해 항구 드로잉 체험');

INSERT INTO content_log (content_id, actor_id, status, reason, date) VALUES
    (@gimhae_ended_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 90 DAY)),
    (@gimhae_ended_content_id, @gimhae_operator_id, 'ENDED', '운영 기간 종료', DATE_SUB(@now, INTERVAL 4 DAY)),
    (@gimhae_pottery_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 60 DAY)),
    (@gimhae_walk_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 60 DAY)),
    (@gimhae_museum_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 60 DAY)),
    (@gimhae_kayak_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 30 DAY)),
    (@gimhae_baking_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 30 DAY)),
    (@gimhae_wetland_content_id, @gimhae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 30 DAY)),
    (@donghae_ended_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 90 DAY)),
    (@donghae_ended_content_id, @donghae_operator_id, 'ENDED', '운영 기간 종료', DATE_SUB(@now, INTERVAL 4 DAY)),
    (@donghae_yoga_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 60 DAY)),
    (@donghae_skywalk_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 60 DAY)),
    (@donghae_climbing_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 60 DAY)),
    (@donghae_surf_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 30 DAY)),
    (@donghae_cave_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 30 DAY)),
    (@donghae_photo_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 30 DAY)),
    (@gimhae_pending_content_id, @gimhae_operator_id, 'PENDING', NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
    (@gimhae_rejected_content_id, @gimhae_operator_id, 'PENDING', NULL, DATE_SUB(@now, INTERVAL 4 DAY)),
    (@gimhae_rejected_content_id, @gimhae_admin_id, 'REJECTED', '안전 운영 계획을 보완해 주세요.', DATE_SUB(@now, INTERVAL 3 DAY)),
    (@gimhae_approved_content_id, @gimhae_operator_id, 'PENDING', NULL, DATE_SUB(@now, INTERVAL 5 DAY)),
    (@gimhae_approved_content_id, @gimhae_admin_id, 'APPROVED', NULL, DATE_SUB(@now, INTERVAL 1 DAY)),
    (@donghae_suspended_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 20 DAY)),
    (@donghae_suspended_content_id, @donghae_admin_id, 'SUSPENDED', '기상 안전 점검', DATE_SUB(@now, INTERVAL 1 DAY)),
    (@donghae_withdrawn_content_id, @donghae_operator_id, 'PUBLISHED', NULL, DATE_SUB(@now, INTERVAL 15 DAY)),
    (@donghae_withdrawn_content_id, @donghae_operator_id, 'WITHDRAWN', '운영자 요청으로 철회', DATE_SUB(@now, INTERVAL 2 DAY));

-- 종료 회차는 진행 중 콘텐츠마다 0~3개로 고정 분포를 사용합니다.
SET @gimhae_ended_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 45 DAY), '10:00:00');
SET @gimhae_pottery_past_1_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 28 DAY), '10:00:00');
SET @gimhae_pottery_past_2_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 14 DAY), '10:00:00');
SET @gimhae_walk_past_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 12 DAY), '18:30:00');
SET @gimhae_museum_past_1_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 26 DAY), '14:00:00');
SET @gimhae_museum_past_2_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 19 DAY), '14:00:00');
SET @gimhae_museum_past_3_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 9 DAY), '14:00:00');
SET @donghae_ended_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 45 DAY), '05:30:00');
SET @donghae_yoga_past_1_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 25 DAY), '08:00:00');
SET @donghae_yoga_past_2_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 11 DAY), '08:00:00');
SET @donghae_climbing_past_starts = TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL 10 DAY), '13:00:00');
-- 김해 도예 체험: 2026-08-25 KST 10:00~22:00, 체크인 13:00~21:00
-- 시드는 UTC로 저장하므로 한국시간에서 9시간을 뺀 값을 사용합니다.
SET @gimhae_pottery_august_25_starts = TIMESTAMP('2026-08-25', '01:00:00');
SET @gimhae_pottery_august_25_ends = TIMESTAMP('2026-08-25', '13:00:00');
SET @gimhae_pottery_august_25_checkin_open = TIMESTAMP('2026-08-25', '04:00:00');
SET @gimhae_pottery_august_25_checkin_close = TIMESTAMP('2026-08-25', '12:00:00');
SET @gimhae_walk_current_starts = DATE_SUB(@now, INTERVAL 30 MINUTE);
SET @gimhae_museum_current_starts = DATE_SUB(@now, INTERVAL 20 MINUTE);
SET @donghae_yoga_current_starts = DATE_SUB(@now, INTERVAL 40 MINUTE);
SET @donghae_skywalk_current_starts = DATE_SUB(@now, INTERVAL 35 MINUTE);
SET @donghae_climbing_current_starts = DATE_SUB(@now, INTERVAL 25 MINUTE);
SET @gimhae_kayak_future_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 7 DAY), '17:00:00');
-- 김해 카약 일몰 체험: 시드 실행일(KST)의 09:00~23:59에 체크인할 수 있습니다.
-- 세션은 UTC로 저장하므로 KST 날짜의 09:00과 23:59를 각각 UTC 00:00과 14:59로 변환합니다.
SET @gimhae_kayak_checkin_open = TIMESTAMP(DATE(DATE_ADD(@now, INTERVAL 9 HOUR)), '00:00:00');
SET @gimhae_kayak_checkin_close = TIMESTAMP(DATE(DATE_ADD(@now, INTERVAL 9 HOUR)), '14:59:00');
SET @gimhae_baking_future_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 8 DAY), '13:00:00');
SET @gimhae_wetland_future_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 9 DAY), '09:00:00');
SET @donghae_surf_future_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 7 DAY), '10:00:00');
-- 망상 해변 서핑 입문: 시드 실행일(KST)의 09:00~23:59에 체크인할 수 있습니다.
SET @donghae_surf_checkin_open = TIMESTAMP(DATE(DATE_ADD(@now, INTERVAL 9 HOUR)), '00:00:00');
SET @donghae_surf_checkin_close = TIMESTAMP(DATE(DATE_ADD(@now, INTERVAL 9 HOUR)), '14:59:00');
SET @donghae_cave_future_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 8 DAY), '14:00:00');
SET @donghae_photo_future_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 9 DAY), '16:00:00');
SET @gimhae_approved_pending_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 10 DAY), '11:00:00');
SET @gimhae_approved_rejected_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 17 DAY), '11:00:00');
SET @gimhae_wetland_cancelled_starts = TIMESTAMP(DATE_ADD(UTC_DATE(), INTERVAL 12 DAY), '09:00:00');

INSERT INTO content_session (
    content_id, region_id, status, starts_at, ends_at, checkin_open_at, checkin_close_at,
    capacity, remaining_capacity, cancelled_at, cancelled_by_user_id, cancellation_reason,
    completed_at, version_no, created_at, updated_at, reviewed_at, reviewed_by_user_id, reject_reason
) VALUES
    (@gimhae_ended_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_ended_starts, DATE_ADD(@gimhae_ended_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_ended_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_ended_starts, INTERVAL 105 MINUTE), 25, 24, NULL, NULL, NULL, DATE_ADD(@gimhae_ended_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_ended_starts, INTERVAL 30 DAY), @gimhae_admin_id, NULL),
    (@gimhae_pottery_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_pottery_past_1_starts, DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_pottery_past_1_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 105 MINUTE), 20, 19, NULL, NULL, NULL, DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_pottery_past_1_starts, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_pottery_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_pottery_past_2_starts, DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_pottery_past_2_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 105 MINUTE), 20, 19, NULL, NULL, NULL, DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_pottery_past_2_starts, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_walk_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_walk_past_starts, DATE_ADD(@gimhae_walk_past_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_walk_past_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_walk_past_starts, INTERVAL 105 MINUTE), 25, 24, NULL, NULL, NULL, DATE_ADD(@gimhae_walk_past_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_walk_past_starts, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_museum_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_museum_past_1_starts, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 105 MINUTE), 20, 19, NULL, NULL, NULL, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_museum_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_museum_past_2_starts, DATE_ADD(@gimhae_museum_past_2_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_museum_past_2_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_museum_past_2_starts, INTERVAL 105 MINUTE), 20, 20, NULL, NULL, NULL, DATE_ADD(@gimhae_museum_past_2_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_museum_past_2_starts, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_museum_content_id, @gimhae_region_id, 'COMPLETED', @gimhae_museum_past_3_starts, DATE_ADD(@gimhae_museum_past_3_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_museum_past_3_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_museum_past_3_starts, INTERVAL 105 MINUTE), 20, 20, NULL, NULL, NULL, DATE_ADD(@gimhae_museum_past_3_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@gimhae_museum_past_3_starts, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@donghae_ended_content_id, @donghae_region_id, 'COMPLETED', @donghae_ended_starts, DATE_ADD(@donghae_ended_starts, INTERVAL 90 MINUTE), DATE_SUB(@donghae_ended_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_ended_starts, INTERVAL 75 MINUTE), 25, 24, NULL, NULL, NULL, DATE_ADD(@donghae_ended_starts, INTERVAL 90 MINUTE), 1, @now, @now, DATE_SUB(@donghae_ended_starts, INTERVAL 30 DAY), @donghae_admin_id, NULL),
    (@donghae_yoga_content_id, @donghae_region_id, 'COMPLETED', @donghae_yoga_past_1_starts, DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 90 MINUTE), DATE_SUB(@donghae_yoga_past_1_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 75 MINUTE), 20, 19, NULL, NULL, NULL, DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 90 MINUTE), 1, @now, @now, DATE_SUB(@donghae_yoga_past_1_starts, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@donghae_yoga_content_id, @donghae_region_id, 'COMPLETED', @donghae_yoga_past_2_starts, DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 90 MINUTE), DATE_SUB(@donghae_yoga_past_2_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 75 MINUTE), 20, 20, NULL, NULL, NULL, DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 90 MINUTE), 1, @now, @now, DATE_SUB(@donghae_yoga_past_2_starts, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@donghae_climbing_content_id, @donghae_region_id, 'COMPLETED', @donghae_climbing_past_starts, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 2 HOUR), DATE_SUB(@donghae_climbing_past_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 105 MINUTE), 12, 10, NULL, NULL, NULL, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 2 HOUR), 1, @now, @now, DATE_SUB(@donghae_climbing_past_starts, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@gimhae_pottery_content_id, @gimhae_region_id, 'SCHEDULED', @gimhae_pottery_august_25_starts, @gimhae_pottery_august_25_ends, @gimhae_pottery_august_25_checkin_open, @gimhae_pottery_august_25_checkin_close, 20, 20, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_walk_content_id, @gimhae_region_id, 'SCHEDULED', @gimhae_walk_current_starts, DATE_ADD(@gimhae_walk_current_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_walk_current_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_walk_current_starts, INTERVAL 105 MINUTE), 25, 25, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_museum_content_id, @gimhae_region_id, 'SCHEDULED', @gimhae_museum_current_starts, DATE_ADD(@gimhae_museum_current_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_museum_current_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_museum_current_starts, INTERVAL 105 MINUTE), 20, 20, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@donghae_yoga_content_id, @donghae_region_id, 'SCHEDULED', @donghae_yoga_current_starts, DATE_ADD(@donghae_yoga_current_starts, INTERVAL 90 MINUTE), DATE_SUB(@donghae_yoga_current_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_yoga_current_starts, INTERVAL 75 MINUTE), 20, 20, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@donghae_skywalk_content_id, @donghae_region_id, 'SCHEDULED', @donghae_skywalk_current_starts, DATE_ADD(@donghae_skywalk_current_starts, INTERVAL 90 MINUTE), DATE_SUB(@donghae_skywalk_current_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_skywalk_current_starts, INTERVAL 75 MINUTE), 20, 20, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@donghae_climbing_content_id, @donghae_region_id, 'SCHEDULED', @donghae_climbing_current_starts, DATE_ADD(@donghae_climbing_current_starts, INTERVAL 2 HOUR), DATE_SUB(@donghae_climbing_current_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_climbing_current_starts, INTERVAL 105 MINUTE), 12, 12, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@gimhae_kayak_content_id, @gimhae_region_id, 'SCHEDULED', @gimhae_kayak_checkin_open, DATE_ADD(@gimhae_kayak_checkin_close, INTERVAL 1 MINUTE), @gimhae_kayak_checkin_open, @gimhae_kayak_checkin_close, 12, 11, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_baking_content_id, @gimhae_region_id, 'SCHEDULED', @gimhae_baking_future_starts, DATE_ADD(@gimhae_baking_future_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_baking_future_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_baking_future_starts, INTERVAL 105 MINUTE), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@gimhae_wetland_content_id, @gimhae_region_id, 'SCHEDULED', @gimhae_wetland_future_starts, DATE_ADD(@gimhae_wetland_future_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_wetland_future_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_wetland_future_starts, INTERVAL 105 MINUTE), 18, 16, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL),
    (@donghae_surf_content_id, @donghae_region_id, 'SCHEDULED', @donghae_surf_checkin_open, DATE_ADD(@donghae_surf_checkin_close, INTERVAL 1 MINUTE), @donghae_surf_checkin_open, @donghae_surf_checkin_close, 14, 13, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@donghae_cave_content_id, @donghae_region_id, 'SCHEDULED', @donghae_cave_future_starts, DATE_ADD(@donghae_cave_future_starts, INTERVAL 2 HOUR), DATE_SUB(@donghae_cave_future_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_cave_future_starts, INTERVAL 105 MINUTE), 20, 20, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@donghae_photo_content_id, @donghae_region_id, 'SCHEDULED', @donghae_photo_future_starts, DATE_ADD(@donghae_photo_future_starts, INTERVAL 2 HOUR), DATE_SUB(@donghae_photo_future_starts, INTERVAL 30 MINUTE), DATE_ADD(@donghae_photo_future_starts, INTERVAL 105 MINUTE), 18, 18, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @donghae_admin_id, NULL),
    (@gimhae_approved_content_id, @gimhae_region_id, 'PENDING', @gimhae_approved_pending_starts, DATE_ADD(@gimhae_approved_pending_starts, INTERVAL 90 MINUTE), DATE_SUB(@gimhae_approved_pending_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_approved_pending_starts, INTERVAL 75 MINUTE), 15, 15, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
    (@gimhae_approved_content_id, @gimhae_region_id, 'REJECTED', @gimhae_approved_rejected_starts, DATE_ADD(@gimhae_approved_rejected_starts, INTERVAL 90 MINUTE), DATE_SUB(@gimhae_approved_rejected_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_approved_rejected_starts, INTERVAL 75 MINUTE), 15, 15, NULL, NULL, NULL, NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 1 DAY), @gimhae_admin_id, '운영 인력 배치 계획을 보완해 주세요.'),
    (@gimhae_wetland_content_id, @gimhae_region_id, 'CANCELLED', @gimhae_wetland_cancelled_starts, DATE_ADD(@gimhae_wetland_cancelled_starts, INTERVAL 2 HOUR), DATE_SUB(@gimhae_wetland_cancelled_starts, INTERVAL 30 MINUTE), DATE_ADD(@gimhae_wetland_cancelled_starts, INTERVAL 105 MINUTE), 18, 18, DATE_SUB(@now, INTERVAL 1 DAY), @gimhae_operator_id, '집중호우 예보', NULL, 1, @now, @now, DATE_SUB(@now, INTERVAL 7 DAY), @gimhae_admin_id, NULL);

SET @gimhae_pottery_session_1 = (SELECT session_id FROM content_session WHERE content_id = @gimhae_pottery_content_id AND starts_at = @gimhae_pottery_past_1_starts);
SET @gimhae_pottery_session_2 = (SELECT session_id FROM content_session WHERE content_id = @gimhae_pottery_content_id AND starts_at = @gimhae_pottery_past_2_starts);
SET @gimhae_walk_session_1 = (SELECT session_id FROM content_session WHERE content_id = @gimhae_walk_content_id AND starts_at = @gimhae_walk_past_starts);
SET @gimhae_museum_session_1 = (SELECT session_id FROM content_session WHERE content_id = @gimhae_museum_content_id AND starts_at = @gimhae_museum_past_1_starts);
SET @donghae_yoga_session_1 = (SELECT session_id FROM content_session WHERE content_id = @donghae_yoga_content_id AND starts_at = @donghae_yoga_past_1_starts);
SET @donghae_yoga_session_2 = (SELECT session_id FROM content_session WHERE content_id = @donghae_yoga_content_id AND starts_at = @donghae_yoga_past_2_starts);
SET @donghae_climbing_session_1 = (SELECT session_id FROM content_session WHERE content_id = @donghae_climbing_content_id AND starts_at = @donghae_climbing_past_starts);
SET @gimhae_kayak_session_id = (SELECT session_id FROM content_session WHERE content_id = @gimhae_kayak_content_id AND starts_at = @gimhae_kayak_checkin_open);
SET @gimhae_baking_session_id = (SELECT session_id FROM content_session WHERE content_id = @gimhae_baking_content_id AND starts_at = @gimhae_baking_future_starts);
SET @gimhae_wetland_session_id = (SELECT session_id FROM content_session WHERE content_id = @gimhae_wetland_content_id AND starts_at = @gimhae_wetland_future_starts);
SET @donghae_surf_session_id = (SELECT session_id FROM content_session WHERE content_id = @donghae_surf_content_id AND starts_at = @donghae_surf_checkin_open);
SET @donghae_cave_session_id = (SELECT session_id FROM content_session WHERE content_id = @donghae_cave_content_id AND starts_at = @donghae_cave_future_starts);

-- 예약, 방문, 후기
INSERT INTO capacity_hold (
    region_id, session_id, user_id, quantity, status, expires_at, terminal_at,
    invalidation_reason, capacity_released_at, created_at
) VALUES
    (@gimhae_region_id, @gimhae_pottery_session_1, @minji_id, 1, 'CONSUMED', DATE_SUB(@gimhae_pottery_past_1_starts, INTERVAL 1 DAY), DATE_SUB(@gimhae_pottery_past_1_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@gimhae_pottery_past_1_starts, INTERVAL 2 DAY)),
    (@gimhae_region_id, @gimhae_pottery_session_2, @junho_id, 1, 'CONSUMED', DATE_SUB(@gimhae_pottery_past_2_starts, INTERVAL 1 DAY), DATE_SUB(@gimhae_pottery_past_2_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@gimhae_pottery_past_2_starts, INTERVAL 2 DAY)),
    (@gimhae_region_id, @gimhae_walk_session_1, @minji_id, 1, 'CONSUMED', DATE_SUB(@gimhae_walk_past_starts, INTERVAL 1 DAY), DATE_SUB(@gimhae_walk_past_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@gimhae_walk_past_starts, INTERVAL 2 DAY)),
    (@gimhae_region_id, @gimhae_museum_session_1, @minji_id, 1, 'CONSUMED', DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 1 DAY), DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 2 DAY)),
    (@gimhae_region_id, @gimhae_museum_session_1, @junho_id, 1, 'CONSUMED', DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 1 DAY), DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 3 DAY), NULL, NULL, DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 3 DAY)),
    (@donghae_region_id, @donghae_yoga_session_1, @sora_id, 1, 'CONSUMED', DATE_SUB(@donghae_yoga_past_1_starts, INTERVAL 1 DAY), DATE_SUB(@donghae_yoga_past_1_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@donghae_yoga_past_1_starts, INTERVAL 2 DAY)),
    (@donghae_region_id, @donghae_yoga_session_2, @minji_id, 1, 'CONSUMED', DATE_SUB(@donghae_yoga_past_2_starts, INTERVAL 1 DAY), DATE_SUB(@donghae_yoga_past_2_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@donghae_yoga_past_2_starts, INTERVAL 2 DAY)),
    (@donghae_region_id, @donghae_climbing_session_1, @sora_id, 1, 'CONSUMED', DATE_SUB(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_SUB(@donghae_climbing_past_starts, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@donghae_climbing_past_starts, INTERVAL 2 DAY)),
    (@donghae_region_id, @donghae_climbing_session_1, @taeyang_id, 1, 'CONSUMED', DATE_SUB(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_SUB(@donghae_climbing_past_starts, INTERVAL 3 DAY), NULL, NULL, DATE_SUB(@donghae_climbing_past_starts, INTERVAL 3 DAY)),
    (@gimhae_region_id, @gimhae_kayak_session_id, @minji_id, 1, 'CONSUMED', DATE_ADD(@now, INTERVAL 10 MINUTE), @now, NULL, NULL, @now),
    (@gimhae_region_id, @gimhae_baking_session_id, @junho_id, 1, 'CONSUMED', DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 3 DAY), NULL, NULL, DATE_SUB(@now, INTERVAL 3 DAY)),
    (@donghae_region_id, @donghae_surf_session_id, @sora_id, 1, 'CONSUMED', DATE_ADD(@now, INTERVAL 10 MINUTE), @now, NULL, NULL, @now),
    (@donghae_region_id, @donghae_cave_session_id, @taeyang_id, 1, 'EXPIRED', DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 1 DAY), NULL, DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 1 DAY)),
    (@gimhae_region_id, @gimhae_wetland_session_id, @minji_id, 2, 'ACTIVE', DATE_ADD(@now, INTERVAL 15 MINUTE), NULL, NULL, NULL, @now),
    (@gimhae_region_id, @gimhae_wetland_session_id, @taeyang_id, 1, 'INVALIDATED', DATE_ADD(@now, INTERVAL 15 MINUTE), DATE_SUB(@now, INTERVAL 10 MINUTE), '참가자 요청으로 무효화', DATE_SUB(@now, INTERVAL 10 MINUTE), DATE_SUB(@now, INTERVAL 20 MINUTE));

SET @hold_gimhae_pottery_1 = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_pottery_session_1 AND user_id = @minji_id);
SET @hold_gimhae_pottery_2 = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_pottery_session_2 AND user_id = @junho_id);
SET @hold_gimhae_walk_1 = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_walk_session_1 AND user_id = @minji_id);
SET @hold_gimhae_museum_minji = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_museum_session_1 AND user_id = @minji_id);
SET @hold_gimhae_museum_junho = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_museum_session_1 AND user_id = @junho_id);
SET @hold_donghae_yoga_sora = (SELECT hold_id FROM capacity_hold WHERE session_id = @donghae_yoga_session_1 AND user_id = @sora_id);
SET @hold_donghae_yoga_minji = (SELECT hold_id FROM capacity_hold WHERE session_id = @donghae_yoga_session_2 AND user_id = @minji_id);
SET @hold_donghae_climbing_sora = (SELECT hold_id FROM capacity_hold WHERE session_id = @donghae_climbing_session_1 AND user_id = @sora_id);
SET @hold_donghae_climbing_taeyang = (SELECT hold_id FROM capacity_hold WHERE session_id = @donghae_climbing_session_1 AND user_id = @taeyang_id);
SET @hold_gimhae_kayak = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_kayak_session_id AND user_id = @minji_id);
SET @hold_gimhae_baking = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_baking_session_id AND user_id = @junho_id);
SET @hold_donghae_surf = (SELECT hold_id FROM capacity_hold WHERE session_id = @donghae_surf_session_id AND user_id = @sora_id);
SET @hold_donghae_cave = (SELECT hold_id FROM capacity_hold WHERE session_id = @donghae_cave_session_id AND user_id = @taeyang_id);
SET @hold_gimhae_wetland_active = (SELECT hold_id FROM capacity_hold WHERE session_id = @gimhae_wetland_session_id AND user_id = @minji_id);

INSERT INTO reservation (
    reservation_no, qr_reference, region_id, hold_id, session_id, user_id, status, confirmed_at,
    cancelled_at, cancellation_reason, expired_at, capacity_released_at, updated_at
) VALUES
    ('GIM-2026-0001', 'qr-gimhae-0001', @gimhae_region_id, @hold_gimhae_pottery_1, @gimhae_pottery_session_1, @minji_id, 'CHECKED_IN', DATE_SUB(@gimhae_pottery_past_1_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 10 MINUTE)),
    ('GIM-2026-0002', 'qr-gimhae-0002', @gimhae_region_id, @hold_gimhae_pottery_2, @gimhae_pottery_session_2, @junho_id, 'CHECKED_IN', DATE_SUB(@gimhae_pottery_past_2_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 10 MINUTE)),
    ('GIM-2026-0003', 'qr-gimhae-0003', @gimhae_region_id, @hold_gimhae_walk_1, @gimhae_walk_session_1, @minji_id, 'CHECKED_IN', DATE_SUB(@gimhae_walk_past_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@gimhae_walk_past_starts, INTERVAL 10 MINUTE)),
    ('GIM-2026-0004', 'qr-gimhae-0004', @gimhae_region_id, @hold_gimhae_museum_minji, @gimhae_museum_session_1, @minji_id, 'CHECKED_IN', DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE)),
    ('GIM-2026-0005', 'qr-gimhae-0005', @gimhae_region_id, @hold_gimhae_museum_junho, @gimhae_museum_session_1, @junho_id, 'CHECKED_IN', DATE_SUB(@gimhae_museum_past_1_starts, INTERVAL 3 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 12 MINUTE)),
    ('DON-2026-0001', 'qr-donghae-0001', @donghae_region_id, @hold_donghae_yoga_sora, @donghae_yoga_session_1, @sora_id, 'CHECKED_IN', DATE_SUB(@donghae_yoga_past_1_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 10 MINUTE)),
    ('DON-2026-0002', 'qr-donghae-0002', @donghae_region_id, @hold_donghae_yoga_minji, @donghae_yoga_session_2, @minji_id, 'CHECKED_IN', DATE_SUB(@donghae_yoga_past_2_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 10 MINUTE)),
    ('DON-2026-0003', 'qr-donghae-0003', @donghae_region_id, @hold_donghae_climbing_sora, @donghae_climbing_session_1, @sora_id, 'CHECKED_IN', DATE_SUB(@donghae_climbing_past_starts, INTERVAL 2 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE)),
    ('DON-2026-0004', 'qr-donghae-0004', @donghae_region_id, @hold_donghae_climbing_taeyang, @donghae_climbing_session_1, @taeyang_id, 'CHECKED_IN', DATE_SUB(@donghae_climbing_past_starts, INTERVAL 3 DAY), NULL, NULL, NULL, NULL, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 12 MINUTE)),
    ('GIM-2026-0101', 'qr-gimhae-0101', @gimhae_region_id, @hold_gimhae_kayak, @gimhae_kayak_session_id, @minji_id, 'CONFIRMED', @now, NULL, NULL, NULL, NULL, @now),
    ('GIM-2026-0102', 'qr-gimhae-0102', @gimhae_region_id, @hold_gimhae_baking, @gimhae_baking_session_id, @junho_id, 'CANCELLED', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 2 DAY), '일정 변경', NULL, DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 2 DAY)),
    ('DON-2026-0101', 'qr-donghae-0101', @donghae_region_id, @hold_donghae_surf, @donghae_surf_session_id, @sora_id, 'CONFIRMED', @now, NULL, NULL, NULL, NULL, @now),
    ('DON-2026-0102', 'qr-donghae-0102', @donghae_region_id, @hold_donghae_cave, @donghae_cave_session_id, @taeyang_id, 'EXPIRED', DATE_SUB(@now, INTERVAL 2 DAY), NULL, NULL, DATE_SUB(@now, INTERVAL 1 DAY), NULL, DATE_SUB(@now, INTERVAL 1 DAY));

SET @reservation_gimhae_pottery_1 = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0001');
SET @reservation_gimhae_pottery_2 = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0002');
SET @reservation_gimhae_walk_1 = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0003');
SET @reservation_gimhae_museum_minji = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0004');
SET @reservation_gimhae_museum_junho = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0005');
SET @reservation_donghae_yoga_sora = (SELECT reservation_id FROM reservation WHERE reservation_no = 'DON-2026-0001');
SET @reservation_donghae_yoga_minji = (SELECT reservation_id FROM reservation WHERE reservation_no = 'DON-2026-0002');
SET @reservation_donghae_climbing_sora = (SELECT reservation_id FROM reservation WHERE reservation_no = 'DON-2026-0003');
SET @reservation_donghae_climbing_taeyang = (SELECT reservation_id FROM reservation WHERE reservation_no = 'DON-2026-0004');
SET @reservation_gimhae_kayak = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0101');
SET @reservation_gimhae_baking = (SELECT reservation_id FROM reservation WHERE reservation_no = 'GIM-2026-0102');
SET @reservation_donghae_surf = (SELECT reservation_id FROM reservation WHERE reservation_no = 'DON-2026-0101');

INSERT INTO visit (
    region_id, reservation_id, user_id, content_id, session_id, checked_in_by_user_id,
    checkin_method, checked_at, author_unlinked_at
) VALUES
    (@gimhae_region_id, @reservation_gimhae_pottery_1, @minji_id, @gimhae_pottery_content_id, @gimhae_pottery_session_1, @gimhae_operator_id, 'QR', DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 10 MINUTE), NULL),
    (@gimhae_region_id, @reservation_gimhae_pottery_2, @junho_id, @gimhae_pottery_content_id, @gimhae_pottery_session_2, @gimhae_operator_id, 'QR', DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 10 MINUTE), NULL),
    (@gimhae_region_id, @reservation_gimhae_walk_1, @minji_id, @gimhae_walk_content_id, @gimhae_walk_session_1, @gimhae_operator_id, 'RESERVATION_NUMBER', DATE_ADD(@gimhae_walk_past_starts, INTERVAL 10 MINUTE), NULL),
    (@gimhae_region_id, @reservation_gimhae_museum_minji, @minji_id, @gimhae_museum_content_id, @gimhae_museum_session_1, @gimhae_operator_id, 'QR', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE), NULL),
    (@gimhae_region_id, @reservation_gimhae_museum_junho, @junho_id, @gimhae_museum_content_id, @gimhae_museum_session_1, @gimhae_operator_id, 'QR', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 12 MINUTE), NULL),
    (@donghae_region_id, @reservation_donghae_yoga_sora, @sora_id, @donghae_yoga_content_id, @donghae_yoga_session_1, @donghae_operator_id, 'QR', DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 10 MINUTE), NULL),
    (@donghae_region_id, @reservation_donghae_yoga_minji, @minji_id, @donghae_yoga_content_id, @donghae_yoga_session_2, @donghae_operator_id, 'QR', DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 10 MINUTE), NULL),
    (@donghae_region_id, @reservation_donghae_climbing_sora, @sora_id, @donghae_climbing_content_id, @donghae_climbing_session_1, @donghae_operator_id, 'QR', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE), NULL),
    (@donghae_region_id, @reservation_donghae_climbing_taeyang, @taeyang_id, @donghae_climbing_content_id, @donghae_climbing_session_1, @donghae_operator_id, 'RESERVATION_NUMBER', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 12 MINUTE), NULL);

SET @visit_gimhae_pottery_minji = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_gimhae_pottery_1);
SET @visit_gimhae_pottery_junho = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_gimhae_pottery_2);
SET @visit_gimhae_walk_minji = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_gimhae_walk_1);
SET @visit_gimhae_museum_minji = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_gimhae_museum_minji);
SET @visit_gimhae_museum_junho = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_gimhae_museum_junho);
SET @visit_donghae_yoga_sora = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_donghae_yoga_sora);
SET @visit_donghae_yoga_minji = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_donghae_yoga_minji);
SET @visit_donghae_climbing_sora = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_donghae_climbing_sora);
SET @visit_donghae_climbing_taeyang = (SELECT visit_id FROM visit WHERE reservation_id = @reservation_donghae_climbing_taeyang);

INSERT INTO review (
    region_id, visit_id, user_id, content_id, rating, review_text, status, created_at,
    updated_at, deleted_at, author_unlinked_at
) VALUES
    (@gimhae_region_id, @visit_gimhae_pottery_minji, @minji_id, @gimhae_pottery_content_id, 5, '처음 만든 도자기인데도 설명이 친절해서 즐겁게 완성했어요.', 'PUBLISHED', DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 1 DAY), NULL, NULL),
    (@gimhae_region_id, @visit_gimhae_pottery_junho, @junho_id, @gimhae_pottery_content_id, 4, '아이와 함께하기 좋고 작품을 포장해서 가져갈 수 있어 좋았습니다.', 'PUBLISHED', DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 1 DAY), NULL, NULL),
    (@gimhae_region_id, @visit_gimhae_walk_minji, @minji_id, @gimhae_walk_content_id, 5, '알고 있던 골목도 해설과 함께 걸으니 새롭게 느껴졌어요.', 'PUBLISHED', DATE_ADD(@gimhae_walk_past_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_walk_past_starts, INTERVAL 1 DAY), NULL, NULL),
    (@gimhae_region_id, @visit_gimhae_museum_minji, @minji_id, @gimhae_museum_content_id, 5, '가야 유물을 이해하기 쉽게 알려 주셔서 가족 모두 만족했습니다.', 'PUBLISHED', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY), NULL, NULL),
    (@gimhae_region_id, @visit_gimhae_museum_junho, @junho_id, @gimhae_museum_content_id, 4, '전시를 천천히 볼 수 있는 알찬 해설이었습니다.', 'PUBLISHED', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 2 DAY), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 2 DAY), NULL, NULL),
    (@donghae_region_id, @visit_donghae_yoga_sora, @sora_id, @donghae_yoga_content_id, 5, '바다를 보며 하는 요가라 아침부터 기분 좋게 시작했어요.', 'PUBLISHED', DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 1 DAY), NULL, NULL),
    (@donghae_region_id, @visit_donghae_yoga_minji, @minji_id, @donghae_yoga_content_id, 4, '초보자도 따라가기 쉬운 동작이라 여행 중에 참여하기 좋았습니다.', 'PUBLISHED', DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 1 DAY), NULL, NULL),
    (@donghae_region_id, @visit_donghae_climbing_sora, @sora_id, @donghae_climbing_content_id, 5, '안전 설명이 꼼꼼했고 처음 암벽을 해보는 사람에게 추천합니다.', 'PUBLISHED', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), NULL, NULL),
    (@donghae_region_id, @visit_donghae_climbing_taeyang, @taeyang_id, @donghae_climbing_content_id, 4, '난이도가 적당하고 직원분들이 계속 도와 주셔서 안심됐어요.', 'PUBLISHED', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), NULL, NULL);

-- 쿠폰 정책, 스탬프북, 지역 미션
INSERT INTO coupon_policy (
    content_id, region_id, name, description, issuance_type, discount_amount,
    minimum_payment_amount, valid_days, issue_starts_at, issue_ends_at, total_issue_limit,
    issued_count, status, published_at, ended_at, updated_at
) VALUES
    (@gimhae_pottery_content_id, @gimhae_region_id, '김해 방문 감사 쿠폰', '김해 체험 방문 완료 후 발급되는 할인 쿠폰입니다.', 'VISIT', 1000, 5000, 30, DATE_SUB(@now, INTERVAL 60 DAY), DATE_ADD(@now, INTERVAL 60 DAY), NULL, 2, 'PUBLISHED', DATE_SUB(@now, INTERVAL 60 DAY), NULL, @now),
    (@gimhae_kayak_content_id, @gimhae_region_id, '김해 미션 완주 쿠폰', '김해 지역 미션을 완주한 방문자에게 드리는 쿠폰입니다.', 'MISSION_REWARD', 2000, 10000, 30, DATE_SUB(@now, INTERVAL 60 DAY), DATE_ADD(@now, INTERVAL 60 DAY), NULL, 1, 'PUBLISHED', DATE_SUB(@now, INTERVAL 60 DAY), NULL, @now),
    (@gimhae_wetland_content_id, @gimhae_region_id, '김해 스탬프 완성 쿠폰', '김해 스탬프북을 완성한 방문자에게 드리는 쿠폰입니다.', 'STAMPBOOK_COMPLETION', 1500, 7000, 30, DATE_SUB(@now, INTERVAL 60 DAY), DATE_ADD(@now, INTERVAL 60 DAY), NULL, 1, 'PUBLISHED', DATE_SUB(@now, INTERVAL 60 DAY), NULL, @now),
    (@gimhae_baking_content_id, @gimhae_region_id, '김해 여름 한정 쿠폰', '종료된 여름 한정 쿠폰 정책입니다.', 'VISIT', 1000, 5000, 14, DATE_SUB(@now, INTERVAL 90 DAY), DATE_SUB(@now, INTERVAL 3 DAY), 100, 0, 'ENDED', DATE_SUB(@now, INTERVAL 90 DAY), DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY)),
    (@gimhae_wetland_content_id, @gimhae_region_id, '김해 가을 준비 쿠폰', '아직 공개되지 않은 쿠폰 정책입니다.', 'VISIT', 1000, 5000, 30, DATE_ADD(@now, INTERVAL 20 DAY), DATE_ADD(@now, INTERVAL 80 DAY), 100, 0, 'DRAFT', NULL, NULL, @now),
    (@donghae_yoga_content_id, @donghae_region_id, '동해 방문 감사 쿠폰', '동해 체험 방문 완료 후 발급되는 할인 쿠폰입니다.', 'VISIT', 1000, 5000, 30, DATE_SUB(@now, INTERVAL 60 DAY), DATE_ADD(@now, INTERVAL 60 DAY), NULL, 2, 'PUBLISHED', DATE_SUB(@now, INTERVAL 60 DAY), NULL, @now),
    (@donghae_surf_content_id, @donghae_region_id, '동해 미션 완주 쿠폰', '동해 지역 미션을 완주한 방문자에게 드리는 쿠폰입니다.', 'MISSION_REWARD', 2000, 10000, 30, DATE_SUB(@now, INTERVAL 60 DAY), DATE_ADD(@now, INTERVAL 60 DAY), NULL, 1, 'PUBLISHED', DATE_SUB(@now, INTERVAL 60 DAY), NULL, @now),
    (@donghae_photo_content_id, @donghae_region_id, '동해 스탬프 완성 쿠폰', '동해 스탬프북을 완성한 방문자에게 드리는 쿠폰입니다.', 'STAMPBOOK_COMPLETION', 1500, 7000, 7, DATE_SUB(@now, INTERVAL 60 DAY), DATE_ADD(@now, INTERVAL 60 DAY), NULL, 1, 'PUBLISHED', DATE_SUB(@now, INTERVAL 60 DAY), NULL, @now),
    (@donghae_cave_content_id, @donghae_region_id, '동해 여름 한정 쿠폰', '종료된 여름 한정 쿠폰 정책입니다.', 'VISIT', 1000, 5000, 14, DATE_SUB(@now, INTERVAL 90 DAY), DATE_SUB(@now, INTERVAL 3 DAY), 100, 0, 'ENDED', DATE_SUB(@now, INTERVAL 90 DAY), DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY)),
    (@donghae_photo_content_id, @donghae_region_id, '동해 가을 준비 쿠폰', '아직 공개되지 않은 쿠폰 정책입니다.', 'VISIT', 1000, 5000, 30, DATE_ADD(@now, INTERVAL 20 DAY), DATE_ADD(@now, INTERVAL 80 DAY), 100, 0, 'DRAFT', NULL, NULL, @now);

SET @gimhae_visit_policy_id = (SELECT coupon_policy_id FROM coupon_policy WHERE name = '김해 방문 감사 쿠폰');
SET @gimhae_mission_policy_id = (SELECT coupon_policy_id FROM coupon_policy WHERE name = '김해 미션 완주 쿠폰');
SET @gimhae_stamp_policy_id = (SELECT coupon_policy_id FROM coupon_policy WHERE name = '김해 스탬프 완성 쿠폰');
SET @donghae_visit_policy_id = (SELECT coupon_policy_id FROM coupon_policy WHERE name = '동해 방문 감사 쿠폰');
SET @donghae_mission_policy_id = (SELECT coupon_policy_id FROM coupon_policy WHERE name = '동해 미션 완주 쿠폰');
SET @donghae_stamp_policy_id = (SELECT coupon_policy_id FROM coupon_policy WHERE name = '동해 스탬프 완성 쿠폰');

INSERT INTO stampbook (
    title, region_id, reward_coupon_policy_id, status, published_at, ended_at
) VALUES
    ('김해 문화 세 곳 스탬프북', @gimhae_region_id, @gimhae_stamp_policy_id, 'PUBLISHED', DATE_SUB(@now, INTERVAL 30 DAY), NULL),
    ('동해 바다와 체험 스탬프북', @donghae_region_id, @donghae_stamp_policy_id, 'PUBLISHED', DATE_SUB(@now, INTERVAL 30 DAY), NULL);

SET @gimhae_stampbook_id = (SELECT stampbook_id FROM stampbook WHERE title = '김해 문화 세 곳 스탬프북');
SET @donghae_stampbook_id = (SELECT stampbook_id FROM stampbook WHERE title = '동해 바다와 체험 스탬프북');

INSERT INTO stampbook_content (stampbook_id, content_id) VALUES
    (@gimhae_stampbook_id, @gimhae_pottery_content_id),
    (@gimhae_stampbook_id, @gimhae_walk_content_id),
    (@gimhae_stampbook_id, @gimhae_museum_content_id),
    (@donghae_stampbook_id, @donghae_yoga_content_id),
    (@donghae_stampbook_id, @donghae_climbing_content_id);

INSERT INTO stampbook_progress (stampbook_id, user_id, status, completed_at) VALUES
    (@gimhae_stampbook_id, @minji_id, 'COMPLETED', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE)),
    (@gimhae_stampbook_id, @junho_id, 'IN_PROGRESS', NULL),
    (@donghae_stampbook_id, @sora_id, 'COMPLETED', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE)),
    (@donghae_stampbook_id, @taeyang_id, 'IN_PROGRESS', NULL);

SET @gimhae_stamp_progress_minji = (SELECT stampbook_progress_id FROM stampbook_progress WHERE stampbook_id = @gimhae_stampbook_id AND user_id = @minji_id);
SET @gimhae_stamp_progress_junho = (SELECT stampbook_progress_id FROM stampbook_progress WHERE stampbook_id = @gimhae_stampbook_id AND user_id = @junho_id);
SET @donghae_stamp_progress_sora = (SELECT stampbook_progress_id FROM stampbook_progress WHERE stampbook_id = @donghae_stampbook_id AND user_id = @sora_id);
SET @donghae_stamp_progress_taeyang = (SELECT stampbook_progress_id FROM stampbook_progress WHERE stampbook_id = @donghae_stampbook_id AND user_id = @taeyang_id);

INSERT INTO stamp_earn (stampbook_progress_id, visit_id, content_id, earned_at) VALUES
    (@gimhae_stamp_progress_minji, @visit_gimhae_pottery_minji, @gimhae_pottery_content_id, DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 10 MINUTE)),
    (@gimhae_stamp_progress_minji, @visit_gimhae_walk_minji, @gimhae_walk_content_id, DATE_ADD(@gimhae_walk_past_starts, INTERVAL 10 MINUTE)),
    (@gimhae_stamp_progress_minji, @visit_gimhae_museum_minji, @gimhae_museum_content_id, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE)),
    (@gimhae_stamp_progress_junho, @visit_gimhae_pottery_junho, @gimhae_pottery_content_id, DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 10 MINUTE)),
    (@gimhae_stamp_progress_junho, @visit_gimhae_museum_junho, @gimhae_museum_content_id, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 12 MINUTE)),
    (@donghae_stamp_progress_sora, @visit_donghae_yoga_sora, @donghae_yoga_content_id, DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 10 MINUTE)),
    (@donghae_stamp_progress_sora, @visit_donghae_climbing_sora, @donghae_climbing_content_id, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE)),
    (@donghae_stamp_progress_taeyang, @visit_donghae_climbing_taeyang, @donghae_climbing_content_id, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 12 MINUTE));

INSERT INTO stampbook_reward_grant (stampbook_progress_id, coupon_policy_id, granted_at) VALUES
    (@gimhae_stamp_progress_minji, @gimhae_stamp_policy_id, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE)),
    (@donghae_stamp_progress_sora, @donghae_stamp_policy_id, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE));

SET @gimhae_stamp_grant_id = (SELECT stampbook_reward_grant_id FROM stampbook_reward_grant WHERE stampbook_progress_id = @gimhae_stamp_progress_minji);
SET @donghae_stamp_grant_id = (SELECT stampbook_reward_grant_id FROM stampbook_reward_grant WHERE stampbook_progress_id = @donghae_stamp_progress_sora);

INSERT INTO mission (
    region_id, condition_type, required_visit_count, reward_coupon_policy_id, status, ends_at,
    published_at, ended_at, title
) VALUES
    (@gimhae_region_id, 'CONTENT_SET', NULL, @gimhae_mission_policy_id, 'PUBLISHED', DATE_ADD(@now, INTERVAL 60 DAY), DATE_SUB(@now, INTERVAL 30 DAY), NULL, '김해 문화 세 곳 방문 미션'),
    (@donghae_region_id, 'VISIT_COUNT', 2, @donghae_mission_policy_id, 'PUBLISHED', DATE_ADD(@now, INTERVAL 60 DAY), DATE_SUB(@now, INTERVAL 30 DAY), NULL, '동해 체험 두 번 방문 미션');

SET @gimhae_mission_id = (SELECT mission_id FROM mission WHERE title = '김해 문화 세 곳 방문 미션');
SET @donghae_mission_id = (SELECT mission_id FROM mission WHERE title = '동해 체험 두 번 방문 미션');

INSERT INTO mission_target_content (mission_id, content_id) VALUES
    (@gimhae_mission_id, @gimhae_pottery_content_id),
    (@gimhae_mission_id, @gimhae_walk_content_id),
    (@gimhae_mission_id, @gimhae_museum_content_id);

INSERT INTO mission_participation (mission_id, user_id, status, joined_at, completed_at) VALUES
    (@gimhae_mission_id, @minji_id, 'COMPLETED', DATE_SUB(@now, INTERVAL 29 DAY), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE)),
    (@gimhae_mission_id, @junho_id, 'IN_PROGRESS', DATE_SUB(@now, INTERVAL 29 DAY), NULL),
    (@donghae_mission_id, @sora_id, 'COMPLETED', DATE_SUB(@now, INTERVAL 29 DAY), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE)),
    (@donghae_mission_id, @taeyang_id, 'IN_PROGRESS', DATE_SUB(@now, INTERVAL 29 DAY), NULL);

SET @gimhae_mission_minji = (SELECT mission_participation_id FROM mission_participation WHERE mission_id = @gimhae_mission_id AND user_id = @minji_id);
SET @gimhae_mission_junho = (SELECT mission_participation_id FROM mission_participation WHERE mission_id = @gimhae_mission_id AND user_id = @junho_id);
SET @donghae_mission_sora = (SELECT mission_participation_id FROM mission_participation WHERE mission_id = @donghae_mission_id AND user_id = @sora_id);
SET @donghae_mission_taeyang = (SELECT mission_participation_id FROM mission_participation WHERE mission_id = @donghae_mission_id AND user_id = @taeyang_id);

INSERT INTO mission_progress (mission_participation_id, visit_id, content_id, recorded_at) VALUES
    (@gimhae_mission_minji, @visit_gimhae_pottery_minji, @gimhae_pottery_content_id, DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 10 MINUTE)),
    (@gimhae_mission_minji, @visit_gimhae_walk_minji, @gimhae_walk_content_id, DATE_ADD(@gimhae_walk_past_starts, INTERVAL 10 MINUTE)),
    (@gimhae_mission_minji, @visit_gimhae_museum_minji, @gimhae_museum_content_id, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 10 MINUTE)),
    (@gimhae_mission_junho, @visit_gimhae_pottery_junho, @gimhae_pottery_content_id, DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 10 MINUTE)),
    (@gimhae_mission_junho, @visit_gimhae_museum_junho, @gimhae_museum_content_id, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 12 MINUTE)),
    (@donghae_mission_sora, @visit_donghae_yoga_sora, @donghae_yoga_content_id, DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 10 MINUTE)),
    (@donghae_mission_sora, @visit_donghae_climbing_sora, @donghae_climbing_content_id, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 10 MINUTE)),
    (@donghae_mission_taeyang, @visit_donghae_climbing_taeyang, @donghae_climbing_content_id, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 12 MINUTE));

INSERT INTO mission_reward_claim (mission_participation_id, coupon_policy_id, claimed_at) VALUES
    (@gimhae_mission_minji, @gimhae_mission_policy_id, DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY)),
    (@donghae_mission_sora, @donghae_mission_policy_id, DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY));

SET @gimhae_mission_claim_id = (SELECT mission_reward_claim_id FROM mission_reward_claim WHERE mission_participation_id = @gimhae_mission_minji);
SET @donghae_mission_claim_id = (SELECT mission_reward_claim_id FROM mission_reward_claim WHERE mission_participation_id = @donghae_mission_sora);

-- 쿠폰: 사용 가능, 사용됨, 예약됨, 만료됨, 무효화됨 상태를 모두 포함합니다.
INSERT INTO coupon (coupon_policy_id, user_id, status, issued_at, expires_at) VALUES
    (@gimhae_visit_policy_id, @junho_id, 'AVAILABLE', DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 31 DAY)),
    (@gimhae_mission_policy_id, @minji_id, 'USED', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 31 DAY)),
    (@gimhae_stamp_policy_id, @minji_id, 'AVAILABLE', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 31 DAY)),
    (@donghae_visit_policy_id, @taeyang_id, 'AVAILABLE', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 31 DAY)),
    (@donghae_mission_policy_id, @sora_id, 'AVAILABLE', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 31 DAY)),
    (@donghae_stamp_policy_id, @sora_id, 'EXPIRED', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 8 DAY)),
    (@gimhae_visit_policy_id, @minji_id, 'RESERVED', DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 1 DAY), DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 31 DAY)),
    (@donghae_visit_policy_id, @minji_id, 'INVALIDATED', DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 1 DAY), DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 31 DAY));

SET @coupon_gimhae_visit = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @gimhae_visit_policy_id AND user_id = @junho_id);
SET @coupon_gimhae_mission = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @gimhae_mission_policy_id AND user_id = @minji_id);
SET @coupon_gimhae_stamp = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @gimhae_stamp_policy_id AND user_id = @minji_id);
SET @coupon_donghae_visit = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @donghae_visit_policy_id AND user_id = @taeyang_id);
SET @coupon_donghae_mission = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @donghae_mission_policy_id AND user_id = @sora_id);
SET @coupon_donghae_stamp = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @donghae_stamp_policy_id AND user_id = @sora_id);
SET @coupon_gimhae_visit_reserved = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @gimhae_visit_policy_id AND user_id = @minji_id);
SET @coupon_donghae_visit_invalidated = (SELECT coupon_id FROM coupon WHERE coupon_policy_id = @donghae_visit_policy_id AND user_id = @minji_id);

INSERT INTO coupon_issuance (
    coupon_id, coupon_policy_id, recipient_user_id, visit_id, mission_reward_claim_id,
    stampbook_reward_grant_id, issuance_identity_hash, issued_at
) VALUES
    (@coupon_gimhae_visit, @gimhae_visit_policy_id, @junho_id, @visit_gimhae_pottery_junho, NULL, NULL, SHA2('coupon-gimhae-visit-junho', 256), DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 1 DAY)),
    (@coupon_gimhae_mission, @gimhae_mission_policy_id, @minji_id, NULL, @gimhae_mission_claim_id, NULL, SHA2('coupon-gimhae-mission-minji', 256), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY)),
    (@coupon_gimhae_stamp, @gimhae_stamp_policy_id, @minji_id, NULL, NULL, @gimhae_stamp_grant_id, SHA2('coupon-gimhae-stamp-minji', 256), DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_visit, @donghae_visit_policy_id, @taeyang_id, @visit_donghae_climbing_taeyang, NULL, NULL, SHA2('coupon-donghae-visit-taeyang', 256), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_mission, @donghae_mission_policy_id, @sora_id, NULL, @donghae_mission_claim_id, NULL, SHA2('coupon-donghae-mission-sora', 256), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_stamp, @donghae_stamp_policy_id, @sora_id, NULL, NULL, @donghae_stamp_grant_id, SHA2('coupon-donghae-stamp-sora', 256), DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY)),
    (@coupon_gimhae_visit_reserved, @gimhae_visit_policy_id, @minji_id, @visit_gimhae_pottery_minji, NULL, NULL, SHA2('coupon-gimhae-visit-minji', 256), DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_visit_invalidated, @donghae_visit_policy_id, @minji_id, @visit_donghae_yoga_minji, NULL, NULL, SHA2('coupon-donghae-visit-minji', 256), DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 1 DAY));

INSERT INTO coupon_status_history (
    coupon_id, previous_status, next_status, reason_code, actor_kind, occurred_at
) VALUES
    (@coupon_gimhae_visit, NULL, 'AVAILABLE', 'VISIT_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@gimhae_pottery_past_2_starts, INTERVAL 1 DAY)),
    (@coupon_gimhae_visit, 'AVAILABLE', 'RESERVED', 'PAYMENT_CREATE', 'USER', DATE_SUB(@now, INTERVAL 3 DAY)),
    (@coupon_gimhae_visit, 'RESERVED', 'USED', 'PAYMENT_APPROVED', 'SYSTEM', DATE_SUB(@now, INTERVAL 3 DAY)),
    (@coupon_gimhae_visit, 'USED', 'AVAILABLE', 'REFUND_SUCCEEDED', 'SYSTEM', DATE_SUB(@now, INTERVAL 2 DAY)),
    (@coupon_gimhae_mission, NULL, 'AVAILABLE', 'MISSION_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY)),
    (@coupon_gimhae_mission, 'AVAILABLE', 'RESERVED', 'PAYMENT_CREATE', 'USER', @now),
    (@coupon_gimhae_mission, 'RESERVED', 'USED', 'PAYMENT_APPROVED', 'SYSTEM', @now),
    (@coupon_gimhae_stamp, NULL, 'AVAILABLE', 'STAMPBOOK_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@gimhae_museum_past_1_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_visit, NULL, 'AVAILABLE', 'VISIT_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_mission, NULL, 'AVAILABLE', 'MISSION_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_stamp, NULL, 'AVAILABLE', 'STAMPBOOK_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@donghae_climbing_past_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_stamp, 'AVAILABLE', 'EXPIRED', 'EXPIRES_AT_REACHED', 'SYSTEM', DATE_SUB(@now, INTERVAL 1 DAY)),
    (@coupon_gimhae_visit_reserved, NULL, 'AVAILABLE', 'VISIT_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@gimhae_pottery_past_1_starts, INTERVAL 1 DAY)),
    (@coupon_gimhae_visit_reserved, 'AVAILABLE', 'RESERVED', 'PAYMENT_CREATE', 'USER', @now),
    (@coupon_donghae_visit_invalidated, NULL, 'AVAILABLE', 'VISIT_REWARD_ISSUED', 'SYSTEM', DATE_ADD(@donghae_yoga_past_2_starts, INTERVAL 1 DAY)),
    (@coupon_donghae_visit_invalidated, 'AVAILABLE', 'INVALIDATED', 'ISSUANCE_REVOKED', 'SYSTEM', DATE_SUB(@now, INTERVAL 1 DAY));

-- 유료 예약, 쿠폰 사용, 환불 이력
INSERT INTO reservation_price_snapshot (
    hold_id, coupon_id, base_amount, discount_amount, final_amount, currency, created_at
) VALUES
    (@hold_gimhae_kayak, @coupon_gimhae_mission, 18000, 2000, 16000, 'KRW', @now),
    (@hold_gimhae_baking, @coupon_gimhae_visit, 22000, 1000, 21000, 'KRW', DATE_SUB(@now, INTERVAL 3 DAY)),
    (@hold_gimhae_wetland_active, @coupon_gimhae_visit_reserved, 10000, 1000, 9000, 'KRW', @now);

SET @kayak_snapshot_id = (SELECT reservation_price_snapshot_id FROM reservation_price_snapshot WHERE hold_id = @hold_gimhae_kayak);
SET @baking_snapshot_id = (SELECT reservation_price_snapshot_id FROM reservation_price_snapshot WHERE hold_id = @hold_gimhae_baking);
SET @wetland_pending_snapshot_id = (SELECT reservation_price_snapshot_id FROM reservation_price_snapshot WHERE hold_id = @hold_gimhae_wetland_active);

INSERT INTO payment (
    hold_id, reservation_price_snapshot_id, reservation_id, order_id, portone_payment_id,
    status, finalized_at, created_at
) VALUES
    (@hold_gimhae_kayak, @kayak_snapshot_id, @reservation_gimhae_kayak, 'order-gimhae-kayak-2026', 'portone-gimhae-kayak-2026', 'APPROVED', @now, @now),
    (@hold_gimhae_baking, @baking_snapshot_id, @reservation_gimhae_baking, 'order-gimhae-baking-2026', 'portone-gimhae-baking-2026', 'APPROVED', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY)),
    (@hold_gimhae_wetland_active, @wetland_pending_snapshot_id, NULL, 'order-gimhae-wetland-pending-2026', NULL, 'PENDING', NULL, @now);

SET @kayak_payment_id = (SELECT payment_id FROM payment WHERE order_id = 'order-gimhae-kayak-2026');
SET @baking_payment_id = (SELECT payment_id FROM payment WHERE order_id = 'order-gimhae-baking-2026');

INSERT INTO payment_idempotency (
    actor_user_id, operation, idempotency_key_hash, request_hash, status, payment_id,
    reservation_id, completed_at, expires_at
) VALUES
    (@minji_id, 'PAYMENT_CREATE', SHA2('payment-key-gimhae-kayak', 256), SHA2('payment-request-gimhae-kayak', 256), 'SUCCEEDED', @kayak_payment_id, NULL, @now, DATE_ADD(@now, INTERVAL 1 DAY)),
    (@junho_id, 'PAYMENT_CREATE', SHA2('payment-key-gimhae-baking', 256), SHA2('payment-request-gimhae-baking', 256), 'SUCCEEDED', @baking_payment_id, NULL, DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 2 DAY));

INSERT INTO payment_verification (
    payment_id, verification_reason, observed_amount, observed_currency, observed_order_id,
    external_status, internal_decision, response_hash, verified_at
) VALUES
    (@kayak_payment_id, 'PAYMENT_CONFIRM', 16000, 'KRW', 'order-gimhae-kayak-2026', 'PAID', 'APPROVED', SHA2('verify-gimhae-kayak', 256), @now),
    (@baking_payment_id, 'PAYMENT_CONFIRM', 21000, 'KRW', 'order-gimhae-baking-2026', 'PAID', 'APPROVED', SHA2('verify-gimhae-baking', 256), DATE_SUB(@now, INTERVAL 3 DAY));

INSERT INTO payment_webhook (
    provider_event_id, payment_id, authentication_result, processing_result, payload_hash, received_at
) VALUES
    ('event-gimhae-kayak-2026', @kayak_payment_id, 'AUTHENTICATED', 'PROCESSED', SHA2('webhook-gimhae-kayak', 256), @now),
    ('event-gimhae-baking-2026', @baking_payment_id, 'AUTHENTICATED', 'PROCESSED', SHA2('webhook-gimhae-baking', 256), DATE_SUB(@now, INTERVAL 3 DAY));

INSERT INTO refund (payment_id, amount, status, requested_at, completed_at, resolved_at) VALUES
    (@baking_payment_id, 21000, 'SUCCEEDED', DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 2 DAY));

SET @baking_refund_id = (SELECT refund_id FROM refund WHERE payment_id = @baking_payment_id);

INSERT INTO refund_attempt (
    refund_id, attempt_no, initiator_kind, portone_cancellation_id, outcome_kind,
    failure_reason_code, external_status, result_hash, attempted_at
) VALUES (
    @baking_refund_id, 1, 'SYSTEM', 'cancel-gimhae-baking-2026', 'RESPONDED', NULL,
    'CANCELLED', SHA2('refund-gimhae-baking', 256), DATE_SUB(@now, INTERVAL 2 DAY)
);

INSERT INTO coupon_redemption (
    coupon_id, reservation_price_snapshot_id, reservation_id, status, redeemed_at,
    reversed_at, refund_id, reversal_reason_code
) VALUES
    (@coupon_gimhae_mission, @kayak_snapshot_id, @reservation_gimhae_kayak, 'CONFIRMED', @now, NULL, NULL, NULL),
    (@coupon_gimhae_visit, @baking_snapshot_id, @reservation_gimhae_baking, 'REVERSED', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 2 DAY), @baking_refund_id, 'REFUND_SUCCEEDED');

INSERT INTO idempotency_record (
    actor_user_id, operation, idempotency_key_hash, request_hash, status, result_code,
    result_reservation_id, result_visit_id, created_at, completed_at, expires_at
) VALUES
    (@minji_id, 'RESERVATION_CONFIRM', SHA2('reservation-gimhae-kayak', 256), SHA2('request-gimhae-kayak', 256), 'SUCCEEDED', 'RESERVATION_CONFIRMED', @reservation_gimhae_kayak, NULL, @now, @now, DATE_ADD(@now, INTERVAL 1 DAY)),
    (@sora_id, 'CHECK_IN', SHA2('checkin-donghae-yoga-sora', 256), SHA2('request-donghae-yoga-sora', 256), 'SUCCEEDED', 'CHECK_IN_COMPLETED', NULL, @visit_donghae_yoga_sora, DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 10 MINUTE), DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 10 MINUTE), DATE_ADD(@donghae_yoga_past_1_starts, INTERVAL 1 DAY));

COMMIT;

-- 프레젠테이션 직전 빠른 확인용 조회
SELECT
    r.name AS 지역,
    c.status AS 콘텐츠상태,
    COUNT(*) AS 콘텐츠수
FROM content c
JOIN region r ON r.region_id = c.region_id
GROUP BY r.name, c.status
ORDER BY r.name, c.status;

SELECT
    r.name AS 지역,
    COUNT(DISTINCT rv.review_id) AS 후기수,
    COUNT(DISTINCT v.visit_id) AS 방문수,
    COUNT(DISTINCT rs.reservation_id) AS 예약수,
    COUNT(DISTINCT sb.stampbook_id) AS 스탬프북수,
    COUNT(DISTINCT m.mission_id) AS 미션수,
    COUNT(DISTINCT cp.coupon_policy_id) AS 쿠폰정책수
FROM region r
LEFT JOIN review rv ON rv.region_id = r.region_id
LEFT JOIN visit v ON v.region_id = r.region_id
LEFT JOIN reservation rs ON rs.region_id = r.region_id
LEFT JOIN stampbook sb ON sb.region_id = r.region_id
LEFT JOIN mission m ON m.region_id = r.region_id
LEFT JOIN coupon_policy cp ON cp.region_id = r.region_id
WHERE r.region_code IN ('GIMHAE', 'DONGHAE')
GROUP BY r.region_id, r.name
ORDER BY r.name;

SELECT
    상태대상,
    상태,
    건수
FROM (
    SELECT '회차' AS 상태대상, status AS 상태, COUNT(*) AS 건수
    FROM content_session
    GROUP BY status
    UNION ALL
    SELECT '정원 홀드', status, COUNT(*)
    FROM capacity_hold
    GROUP BY status
    UNION ALL
    SELECT '쿠폰', status, COUNT(*)
    FROM coupon
    GROUP BY status
    UNION ALL
    SELECT '결제', status, COUNT(*)
    FROM payment
    GROUP BY status
) AS status_summary
ORDER BY 상태대상, 상태;
