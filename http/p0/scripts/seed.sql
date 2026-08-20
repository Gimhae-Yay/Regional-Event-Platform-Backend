-- P0 HTTP 시나리오 전용 로컬 시드. 운영 DB와 다른 개발 데이터에는 적용하지 않는다.
START TRANSACTION;

SET @now = UTC_TIMESTAMP(6);
SET @password_hash = '{bcrypt}$2a$12$ssgGGtMwA9aYPG.BtDlUaOVh6oJC19qJkadq6seaZSh/xLpYuxX.u';

DELETE FROM audit_event_actor_link
WHERE audit_event_id IN (
    SELECT audit_event_id FROM audit_event
    WHERE region_id IN (900001, 900002)
);
DELETE FROM audit_event
WHERE region_id IN (900001, 900002);
DELETE FROM idempotency_record
WHERE result_reservation_id IN (
    SELECT reservation_id FROM reservation
    WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023)
) OR result_visit_id IN (
    SELECT visit_id FROM visit
    WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023)
);
DELETE FROM review
WHERE visit_id IN (
    SELECT visit_id FROM visit
    WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023)
);
DELETE FROM visit
WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023);
DELETE FROM reservation
WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023);
DELETE FROM capacity_hold
WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023);
DELETE FROM review WHERE review_id BETWEEN 960001 AND 960009;
DELETE FROM visit WHERE visit_id BETWEEN 950001 AND 950009;
DELETE FROM reservation WHERE reservation_id BETWEEN 930001 AND 930009;
DELETE FROM capacity_hold WHERE hold_id BETWEEN 940001 AND 940009;
DELETE FROM content_log WHERE content_id IN (900001, 920001, 920002, 920003, 920004, 920006);
DELETE FROM content_revision WHERE content_id IN (900001, 920001, 920002);
DELETE FROM session_revision WHERE target_session_id IN (910001, 910003, 920022);

UPDATE content
SET representative_image_object_id = NULL,
    representative_image_assigned_at = NULL
WHERE content_id IN (900001, 920001, 920002, 920003, 920004, 920006);

DELETE FROM content_session
WHERE session_id IN (910001, 910002, 910003, 910004, 920011, 920012, 920021, 920022, 920023);
DELETE FROM image_object WHERE image_object_id = 980001;

INSERT INTO region (region_id, region_code, name, is_public, created_at, updated_at)
VALUES
  (900001, 'LOCAL-P0-1', '로컬 P0 지역 1', TRUE, @now, @now),
  (900002, 'LOCAL-P0-2', '로컬 P0 지역 2', TRUE, @now, @now)
ON DUPLICATE KEY UPDATE
  region_code = VALUES(region_code), name = VALUES(name), is_public = VALUES(is_public), updated_at = VALUES(updated_at);

INSERT INTO app_user (user_id, login_identifier, password_hash, name, phone, status, created_at, updated_at)
VALUES
  (4, 'p0-region-admin@example.test', @password_hash, 'P0 지역 관리자 1', '01000000004', 'ACTIVE', @now, @now),
  (5, 'p0-other-region-admin@example.test', @password_hash, 'P0 지역 관리자 2', '01000000005', 'ACTIVE', @now, @now),
  (6, 'p0-operator@example.test', @password_hash, 'P0 운영자 1', '01000000006', 'ACTIVE', @now, @now),
  (7, 'p0-other-operator@example.test', @password_hash, 'P0 운영자 2', '01000000007', 'ACTIVE', @now, @now),
  (8, 'p0-visitor@example.test', @password_hash, 'P0 방문자 1', '01000000008', 'ACTIVE', @now, @now),
  (9, 'p0-other-visitor@example.test', @password_hash, 'P0 방문자 2', '01000000009', 'ACTIVE', @now, @now)
ON DUPLICATE KEY UPDATE
  login_identifier = VALUES(login_identifier), password_hash = VALUES(password_hash), name = VALUES(name),
  phone = VALUES(phone), status = VALUES(status), updated_at = VALUES(updated_at);

DELETE FROM user_role_assignment WHERE user_id IN (4, 5, 6, 7, 8, 9);
INSERT INTO user_role_assignment (user_id, role, region_id, status, granted_at)
VALUES
  (4, 'VISITOR', NULL, 'ACTIVE', @now), (4, 'REGION_ADMIN', 900001, 'ACTIVE', @now),
  (5, 'VISITOR', NULL, 'ACTIVE', @now), (5, 'REGION_ADMIN', 900002, 'ACTIVE', @now),
  (6, 'VISITOR', NULL, 'ACTIVE', @now), (6, 'OPERATOR', 900001, 'ACTIVE', @now),
  (7, 'VISITOR', NULL, 'ACTIVE', @now), (7, 'OPERATOR', 900002, 'ACTIVE', @now),
  (8, 'VISITOR', NULL, 'ACTIVE', @now), (9, 'VISITOR', NULL, 'ACTIVE', @now);

INSERT INTO image_object (
  image_object_id, object_key, media_type, byte_size, checksum, lifecycle_status, delete_attempt_count,
  last_delete_attempted_at, created_at, created_by_user_id, region_id, upload_expires_at, linked_at
)
VALUES (
  980001, 'local/p0/representative.jpg', 'image/jpeg', 1024, 'p0-representative-checksum', 'ACTIVE', 0,
  NULL, @now, 6, 900001, DATE_ADD(@now, INTERVAL 1 DAY), @now
);

INSERT INTO content (
  content_id, region_id, operator_id, content_type, status, version_no, title, description, location_text,
  operating_hours_text, contact_text, precautions, age_requirement, materials, cancellation_policy_text,
  publish_at, deleted_at, created_at, updated_at, representative_image_object_id, representative_image_assigned_at
)
VALUES
  (900001, 900001, 6, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'P0 공개 체험', 'P0 공개 체험 설명', '로컬 P0 장소',
   '매일 10:00-18:00', '010-0000-0006', '신분증을 지참하세요.', '만 7세 이상', '간단한 재료 제공', '시작 24시간 전 취소 가능',
   DATE_SUB(@now, INTERVAL 1 DAY), NULL, @now, @now, 980001, @now),
  (920001, 900001, 6, 'EVENT_EXPERIENCE', 'PENDING', 0, 'P0 반려 대상 콘텐츠', '심사 반려 대상', '로컬 P0 장소',
   '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능',
   DATE_ADD(@now, INTERVAL 7 DAY), NULL, @now, @now, NULL, NULL),
  (920002, 900001, 6, 'EVENT_EXPERIENCE', 'PENDING', 0, 'P0 승인 및 삭제 대상 콘텐츠', '심사 승인 대상', '로컬 P0 장소',
   '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능',
   DATE_ADD(@now, INTERVAL 7 DAY), NULL, @now, @now, NULL, NULL),
  (920003, 900001, 6, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'P0 중단 대상 콘텐츠', '운영 중단 대상', '로컬 P0 장소',
   '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능',
   DATE_SUB(@now, INTERVAL 1 DAY), NULL, @now, @now, NULL, NULL),
  (920004, 900001, 6, 'EVENT_EXPERIENCE', 'APPROVED', 1, 'P0 비공개 콘텐츠', '상태 충돌 검증 대상', '로컬 P0 장소',
   '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능',
   DATE_ADD(@now, INTERVAL 1 DAY), NULL, @now, @now, NULL, NULL),
  (920006, 900001, 6, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'P0 종료 대상 콘텐츠', '운영 종료 대상', '로컬 P0 장소',
   '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능',
   DATE_SUB(@now, INTERVAL 1 DAY), NULL, @now, @now, NULL, NULL)
ON DUPLICATE KEY UPDATE
  region_id = VALUES(region_id), operator_id = VALUES(operator_id), content_type = VALUES(content_type), status = VALUES(status),
  version_no = VALUES(version_no), title = VALUES(title), description = VALUES(description), location_text = VALUES(location_text),
  operating_hours_text = VALUES(operating_hours_text), contact_text = VALUES(contact_text), precautions = VALUES(precautions),
  age_requirement = VALUES(age_requirement), materials = VALUES(materials), cancellation_policy_text = VALUES(cancellation_policy_text),
  publish_at = VALUES(publish_at), deleted_at = VALUES(deleted_at), updated_at = VALUES(updated_at),
  representative_image_object_id = VALUES(representative_image_object_id), representative_image_assigned_at = VALUES(representative_image_assigned_at);

INSERT INTO content_session (
  session_id, content_id, region_id, status, starts_at, ends_at, checkin_open_at, checkin_close_at,
  capacity, remaining_capacity, cancelled_at, cancelled_by_user_id, cancellation_reason, completed_at,
  version_no, created_at, updated_at, reviewed_at, reviewed_by_user_id, reject_reason
)
VALUES
  (910001, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 24 HOUR), DATE_ADD(@now, INTERVAL 26 HOUR),
   DATE_ADD(@now, INTERVAL 23 HOUR), DATE_ADD(@now, INTERVAL 25 HOUR), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (910002, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 48 HOUR), DATE_ADD(@now, INTERVAL 50 HOUR),
   DATE_ADD(@now, INTERVAL 47 HOUR), DATE_ADD(@now, INTERVAL 49 HOUR), 1, 0, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (910003, 900001, 900001, 'SCHEDULED', DATE_SUB(@now, INTERVAL 1 HOUR), DATE_ADD(@now, INTERVAL 1 HOUR),
   DATE_SUB(@now, INTERVAL 2 HOUR), DATE_SUB(@now, INTERVAL 5 MINUTE), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (910004, 900001, 900001, 'COMPLETED', DATE_SUB(@now, INTERVAL 72 HOUR), DATE_SUB(@now, INTERVAL 70 HOUR),
   DATE_SUB(@now, INTERVAL 73 HOUR), DATE_SUB(@now, INTERVAL 71 HOUR), 10, 10, NULL, NULL, NULL, DATE_SUB(@now, INTERVAL 70 HOUR), 1, @now, @now, NULL, NULL, NULL),
  (920011, 920002, 900001, 'PENDING', DATE_ADD(@now, INTERVAL 96 HOUR), DATE_ADD(@now, INTERVAL 98 HOUR),
   DATE_ADD(@now, INTERVAL 95 HOUR), DATE_ADD(@now, INTERVAL 97 HOUR), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (920012, 920001, 900001, 'PENDING', DATE_ADD(@now, INTERVAL 120 HOUR), DATE_ADD(@now, INTERVAL 122 HOUR),
   DATE_ADD(@now, INTERVAL 119 HOUR), DATE_ADD(@now, INTERVAL 121 HOUR), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (920021, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 48 HOUR), DATE_ADD(@now, INTERVAL 50 HOUR),
   DATE_ADD(@now, INTERVAL 47 HOUR), DATE_ADD(@now, INTERVAL 49 HOUR), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (920022, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 72 HOUR), DATE_ADD(@now, INTERVAL 74 HOUR),
   DATE_ADD(@now, INTERVAL 71 HOUR), DATE_ADD(@now, INTERVAL 73 HOUR), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (920023, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 15 MINUTE), DATE_ADD(@now, INTERVAL 2 HOUR),
   DATE_SUB(@now, INTERVAL 5 MINUTE), DATE_ADD(@now, INTERVAL 1 HOUR), 10, 10, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL);

INSERT INTO content_revision (
  content_revision_id, content_id, revision_no, base_content_version, editor_user_id, status, title, description,
  location_text, operating_hours_text, contact_text, precautions, age_requirement, materials, cancellation_policy_text,
  submitted_at, reviewed_at, reviewed_by_user_id, review_reason, withdrawn_at, withdrawn_by_user_id, withdrawal_reason,
  created_at, candidate_image_object_id, candidate_image_assigned_at, publish_at
)
VALUES
  (920001, 920001, 1, 0, 6, 'EDIT_REQUESTED', 'P0 수정 승인 대상', '수정 승인 대상', '로컬 P0 장소', '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능', @now, NULL, NULL, NULL, NULL, NULL, NULL, @now, 980001, @now, DATE_ADD(@now, INTERVAL 7 DAY)),
  (920002, 920002, 1, 0, 6, 'EDIT_REQUESTED', 'P0 수정 반려 대상', '수정 반려 대상', '로컬 P0 장소', '매일 10:00-18:00', '010-0000-0006', '안내 확인', '만 7세 이상', '재료 제공', '시작 전 취소 가능', @now, NULL, NULL, NULL, NULL, NULL, NULL, @now, 980001, @now, DATE_ADD(@now, INTERVAL 7 DAY));

INSERT INTO session_revision (
  session_revision_id, content_id, region_id, target_session_id, base_session_version, starts_at, ends_at,
  checkin_open_at, checkin_close_at, capacity, status, requested_by_user_id, submitted_at, reviewed_at,
  reviewed_by_user_id, reject_reason, created_at
)
VALUES
  (920001, 900001, 900001, 910001, 1, DATE_ADD(@now, INTERVAL 24 HOUR), DATE_ADD(@now, INTERVAL 26 HOUR),
   DATE_ADD(@now, INTERVAL 23 HOUR), DATE_ADD(@now, INTERVAL 25 HOUR), 12, 'PENDING', 6, @now, NULL, NULL, NULL, @now),
  (920002, 900001, 900001, 910003, 1, DATE_ADD(@now, INTERVAL 48 HOUR), DATE_ADD(@now, INTERVAL 50 HOUR),
   DATE_ADD(@now, INTERVAL 47 HOUR), DATE_ADD(@now, INTERVAL 49 HOUR), 10, 'PENDING', 6, @now, NULL, NULL, NULL, @now);

INSERT INTO capacity_hold (
  hold_id, region_id, session_id, user_id, quantity, status, expires_at, terminal_at, invalidation_reason,
  capacity_released_at, created_at
)
VALUES
  (940001, 900001, 910001, 8, 1, 'CONSUMED', DATE_ADD(@now, INTERVAL 10 MINUTE), @now, NULL, NULL, @now),
  (940002, 900001, 910001, 8, 1, 'EXPIRED', DATE_SUB(@now, INTERVAL 10 MINUTE), DATE_SUB(@now, INTERVAL 10 MINUTE), NULL, DATE_SUB(@now, INTERVAL 10 MINUTE), @now),
  (940003, 900001, 910003, 8, 1, 'CONSUMED', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY), NULL, NULL, @now),
  (940004, 900001, 910004, 8, 1, 'CONSUMED', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY), NULL, NULL, @now),
  (940005, 900001, 910004, 9, 1, 'CONSUMED', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY), NULL, NULL, @now),
  (940006, 900001, 920023, 8, 1, 'CONSUMED', @now, @now, NULL, NULL, @now),
  (940007, 900001, 920023, 8, 1, 'CONSUMED', @now, @now, NULL, NULL, @now),
  (940008, 900001, 910001, 8, 1, 'CONSUMED', @now, @now, NULL, NULL, @now),
  (940009, 900001, 910004, 8, 1, 'CONSUMED', @now, @now, NULL, NULL, @now);

INSERT INTO reservation (
  reservation_id, reservation_no, qr_reference, region_id, hold_id, session_id, user_id, status, confirmed_at,
  cancelled_at, cancellation_reason, expired_at, capacity_released_at, updated_at
)
VALUES
  (930001, 'RLOCALMANUAL', '00000000-0000-4000-8000-000000000001', 900001, 940001, 910001, 8, 'CONFIRMED', @now, NULL, NULL, NULL, NULL, @now),
  (930002, 'RLOCALCANCEL', '00000000-0000-4000-8000-000000000002', 900001, 940002, 910001, 8, 'CANCELLED', @now, @now, 'P0 취소 예약', NULL, NULL, @now),
  (930003, 'RLOCALCHECKED', '00000000-0000-4000-8000-000000000003', 900001, 940003, 910003, 8, 'CHECKED_IN', @now, NULL, NULL, NULL, NULL, @now),
  (930004, 'RLOCALCOMPLETED', '00000000-0000-4000-8000-000000000004', 900001, 940004, 910004, 8, 'CONFIRMED', @now, NULL, NULL, NULL, NULL, @now),
  (930005, 'RLOCALOTHERVISIT', '00000000-0000-4000-8000-000000000005', 900001, 940005, 910004, 9, 'CHECKED_IN', @now, NULL, NULL, NULL, NULL, @now),
  (930006, 'RLOCALQR', '00000000-0000-4000-8000-000000000006', 900001, 940006, 920023, 8, 'CONFIRMED', @now, NULL, NULL, NULL, NULL, @now),
  (930007, 'RLOCALMANUAL2', '00000000-0000-4000-8000-000000000007', 900001, 940007, 920023, 8, 'CONFIRMED', @now, NULL, NULL, NULL, NULL, @now),
  (930008, 'RLOCALCANCELTEST', '00000000-0000-4000-8000-000000000008', 900001, 940008, 910001, 8, 'CANCELLED', @now, @now, 'P0 취소 검증', NULL, NULL, @now),
  (930009, 'RLOCALREVIEW', '00000000-0000-4000-8000-000000000009', 900001, 940009, 910004, 8, 'CHECKED_IN', @now, NULL, NULL, NULL, NULL, @now);

INSERT INTO visit (
  visit_id, region_id, reservation_id, user_id, content_id, session_id, checked_in_by_user_id, checkin_method,
  checked_at, author_unlinked_at
)
VALUES
  (950001, 900001, 930009, 8, 900001, 910004, 6, 'QR', @now, NULL),
  (950002, 900001, 930005, 9, 900001, 910004, 6, 'QR', @now, NULL),
  (950003, 900001, 930003, 8, 900001, 910003, 6, 'QR', @now, NULL);

INSERT INTO review (
  review_id, region_id, visit_id, user_id, content_id, rating, review_text, status, created_at, updated_at,
  deleted_at, author_unlinked_at
)
VALUES
  (960001, 900001, 950002, 9, 900001, 5, '다른 방문자의 공개 후기', 'PUBLISHED', @now, @now, NULL, NULL),
  (960004, 900001, 950003, 8, 900001, 5, 'P0 방문자의 기존 후기', 'PUBLISHED', @now, @now, NULL, NULL);

INSERT INTO content_log (id, content_id, actor_id, status, reason, date)
VALUES (970001, 900001, 6, 'PUBLISHED', NULL, @now);

INSERT INTO audit_event (
  audit_event_id, request_id, region_id, target_type, target_id, previous_state, next_state, result,
  reason_code, actor_kind, actor_role, occurred_at
)
VALUES (
  990001, '00000000-0000-4000-8000-000000009001', 900001, 'RESERVATION', 930006, 'CONFIRMED', 'CONFIRMED',
  'FAILURE', 'QR_CHECK_IN_REFERENCE_INVALID', 'USER', 'OPERATOR', @now
);

COMMIT;
