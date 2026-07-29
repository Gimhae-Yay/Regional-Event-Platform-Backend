CREATE TABLE region (
    region_id BIGINT NOT NULL AUTO_INCREMENT,
    region_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_public BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_region PRIMARY KEY (region_id),
    CONSTRAINT uk_region_region_code UNIQUE (region_code)
);

CREATE TABLE app_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    login_identifier VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (user_id),
    CONSTRAINT uk_app_user_login_identifier UNIQUE (login_identifier),
    CONSTRAINT ck_app_user_status CHECK (status REGEXP '^(ACTIVE|WITHDRAWING)$')
);

CREATE TABLE user_role_assignment (
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    region_id BIGINT,
    granted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_user_role_assignment PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_assignment_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_user_role_assignment_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_user_role_assignment_role
        CHECK (role REGEXP '^(VISITOR|OPERATOR|REGION_ADMIN)$'),
    CONSTRAINT ck_user_role_assignment_visitor_without_region
        CHECK (role <> 'VISITOR' OR region_id IS NULL),
    CONSTRAINT ck_user_role_assignment_scoped_role_with_region
        CHECK (role = 'VISITOR' OR region_id IS NOT NULL)
);

CREATE TABLE operator_application (
    operator_application_id BIGINT NOT NULL AUTO_INCREMENT,
    applicant_user_id BIGINT,
    requested_region_id BIGINT NOT NULL,
    business_information TEXT,
    status VARCHAR(30) NOT NULL,
    inspected_user_id BIGINT,
    rejected_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_operator_application PRIMARY KEY (operator_application_id),
    CONSTRAINT fk_operator_application_applicant_user
        FOREIGN KEY (applicant_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_operator_application_requested_region
        FOREIGN KEY (requested_region_id) REFERENCES region (region_id),
    CONSTRAINT fk_operator_application_inspected_user
        FOREIGN KEY (inspected_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_operator_application_status
        CHECK (status REGEXP '^(PENDING|APPROVED|REJECTED|CANCELLED)$'),
    CONSTRAINT ck_operator_application_approved_review_result
        CHECK (status <> 'APPROVED' OR (inspected_user_id IS NOT NULL AND rejected_reason IS NULL)),
    CONSTRAINT ck_operator_application_rejected_review_result
        CHECK (status <> 'REJECTED' OR (inspected_user_id IS NOT NULL AND rejected_reason IS NOT NULL))
);

CREATE TABLE image_object (
    image_object_id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(768) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    checksum VARCHAR(255) NOT NULL,
    lifecycle_status VARCHAR(30) NOT NULL,
    delete_attempt_count INT NOT NULL,
    last_delete_attempted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_image_object PRIMARY KEY (image_object_id),
    CONSTRAINT uk_image_object_object_key UNIQUE (object_key),
    CONSTRAINT ck_image_object_lifecycle_status
        CHECK (lifecycle_status REGEXP '^(ACTIVE|DELETE_PENDING)$'),
    CONSTRAINT ck_image_object_byte_size CHECK (byte_size >= 0),
    CONSTRAINT ck_image_object_delete_attempt_count CHECK (delete_attempt_count >= 0)
);

CREATE INDEX idx_image_object_lifecycle_status_last_delete_attempted_at
    ON image_object (lifecycle_status, last_delete_attempted_at);

CREATE TABLE content (
    content_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location_text VARCHAR(255) NOT NULL,
    operating_hours_text TEXT NOT NULL,
    contact_text VARCHAR(255) NOT NULL,
    precautions TEXT NOT NULL,
    age_requirement VARCHAR(255) NOT NULL,
    materials TEXT NOT NULL,
    cancellation_policy_text TEXT NOT NULL,
    publish_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content PRIMARY KEY (content_id),
    CONSTRAINT uk_content_content_id_region_id UNIQUE (content_id, region_id),
    CONSTRAINT fk_content_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_content_operator FOREIGN KEY (operator_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_content_type CHECK (content_type = 'EVENT_EXPERIENCE'),
    CONSTRAINT ck_content_status
        CHECK (status REGEXP '^(PENDING|REJECTED|APPROVED|PUBLISHED|SUSPENDED|WITHDRAWN|ENDED)$'),
    CONSTRAINT ck_content_soft_delete_status
        CHECK (
            CASE
                WHEN deleted_at IS NULL THEN TRUE
                WHEN status = 'PENDING' THEN TRUE
                WHEN status = 'APPROVED' THEN TRUE
                ELSE FALSE
            END
        )
);

CREATE INDEX idx_content_region_status_type_publish_deleted
    ON content (region_id, status, content_type, publish_at, deleted_at);

CREATE INDEX idx_content_status_publish_deleted
    ON content (status, publish_at, deleted_at);

CREATE TABLE content_session (
    session_id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    checkin_open_at TIMESTAMP(6) NOT NULL,
    checkin_close_at TIMESTAMP(6) NOT NULL,
    capacity INT NOT NULL,
    remaining_capacity INT NOT NULL,
    cancelled_at TIMESTAMP(6),
    cancelled_by_user_id BIGINT,
    cancellation_reason TEXT,
    completed_at TIMESTAMP(6),
    version_no INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content_session PRIMARY KEY (session_id),
    CONSTRAINT uk_content_session_session_region UNIQUE (session_id, region_id),
    CONSTRAINT uk_content_session_session_content_region UNIQUE (session_id, content_id, region_id),
    CONSTRAINT fk_content_session_content_region
        FOREIGN KEY (content_id, region_id) REFERENCES content (content_id, region_id),
    CONSTRAINT fk_content_session_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_content_session_cancelled_by_user
        FOREIGN KEY (cancelled_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_content_session_status
        CHECK (status REGEXP '^(SCHEDULED|COMPLETED|CANCELLED)$'),
    CONSTRAINT ck_content_session_time_range
        CHECK (starts_at < ends_at AND checkin_open_at < checkin_close_at AND ends_at <= checkin_close_at),
    CONSTRAINT ck_content_session_capacity
        CHECK (capacity > 0 AND remaining_capacity >= 0 AND remaining_capacity <= capacity),
    CONSTRAINT ck_content_session_cancelled
        CHECK (
            (status = 'CANCELLED'
                AND cancelled_at IS NOT NULL
                AND cancelled_by_user_id IS NOT NULL
                AND cancellation_reason IS NOT NULL)
            OR (status <> 'CANCELLED'
                AND cancelled_at IS NULL
                AND cancelled_by_user_id IS NULL
                AND cancellation_reason IS NULL)
        ),
    CONSTRAINT ck_content_session_completed
        CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL))
);

CREATE INDEX idx_content_session_content_status_starts
    ON content_session (content_id, status, starts_at);

CREATE INDEX idx_content_session_region_status_starts
    ON content_session (region_id, status, starts_at);

CREATE TABLE content_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    actor_id BIGINT,
    status VARCHAR(30) NOT NULL,
    reason TEXT,
    date TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content_log PRIMARY KEY (id),
    CONSTRAINT fk_content_log_content FOREIGN KEY (content_id) REFERENCES content (content_id),
    CONSTRAINT fk_content_log_actor FOREIGN KEY (actor_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_content_log_status
        CHECK (status REGEXP '^(PENDING|REJECTED|APPROVED|PUBLISHED|SUSPENDED|WITHDRAWN|ENDED|DELETED)$'),
    CONSTRAINT ck_content_log_reason
        CHECK (
            (status <> 'REJECTED' AND status <> 'SUSPENDED' AND status <> 'WITHDRAWN' AND status <> 'DELETED')
            OR reason IS NOT NULL
        )
);

CREATE TABLE content_revision (
    content_revision_id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    base_content_version INT NOT NULL,
    editor_user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location_text VARCHAR(255) NOT NULL,
    operating_hours_text TEXT NOT NULL,
    contact_text VARCHAR(255) NOT NULL,
    precautions TEXT NOT NULL,
    age_requirement VARCHAR(255) NOT NULL,
    materials TEXT NOT NULL,
    cancellation_policy_text TEXT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    reviewed_by_user_id BIGINT,
    review_reason TEXT,
    withdrawn_at TIMESTAMP(6),
    withdrawn_by_user_id BIGINT,
    withdrawal_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    active_request_content_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'EDIT_REQUESTED' THEN content_id ELSE NULL END
    ),
    CONSTRAINT pk_content_revision PRIMARY KEY (content_revision_id),
    CONSTRAINT uk_content_revision_content_revision_no UNIQUE (content_id, revision_no),
    CONSTRAINT uk_content_revision_active_request UNIQUE (active_request_content_id),
    CONSTRAINT fk_content_revision_content FOREIGN KEY (content_id) REFERENCES content (content_id),
    CONSTRAINT fk_content_revision_editor FOREIGN KEY (editor_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_content_revision_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_content_revision_withdrawer FOREIGN KEY (withdrawn_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_content_revision_status
        CHECK (status REGEXP '^(EDIT_REQUESTED|EDIT_APPROVED|EDIT_REJECTED|EDIT_WITHDRAWN)$'),
    CONSTRAINT ck_content_revision_reviewed
        CHECK (
            (status <> 'EDIT_APPROVED' AND status <> 'EDIT_REJECTED')
            OR (reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL AND review_reason IS NOT NULL)
        ),
    CONSTRAINT ck_content_revision_withdrawn
        CHECK (
            status <> 'EDIT_WITHDRAWN'
            OR (withdrawn_at IS NOT NULL AND withdrawn_by_user_id IS NOT NULL AND withdrawal_reason IS NOT NULL)
        )
);

CREATE TABLE content_representative_image (
    content_id BIGINT NOT NULL,
    image_object_id BIGINT NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content_representative_image PRIMARY KEY (content_id),
    CONSTRAINT uk_content_representative_image_object UNIQUE (image_object_id),
    CONSTRAINT fk_content_representative_image_content
        FOREIGN KEY (content_id) REFERENCES content (content_id),
    CONSTRAINT fk_content_representative_image_object
        FOREIGN KEY (image_object_id) REFERENCES image_object (image_object_id)
);

CREATE TABLE content_revision_representative_image (
    content_revision_id BIGINT NOT NULL,
    image_object_id BIGINT NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content_revision_representative_image PRIMARY KEY (content_revision_id),
    CONSTRAINT uk_content_revision_representative_image_object UNIQUE (image_object_id),
    CONSTRAINT fk_content_revision_representative_image_revision
        FOREIGN KEY (content_revision_id) REFERENCES content_revision (content_revision_id),
    CONSTRAINT fk_content_revision_representative_image_object
        FOREIGN KEY (image_object_id) REFERENCES image_object (image_object_id)
);

CREATE TABLE capacity_hold (
    hold_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT,
    quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    terminal_at TIMESTAMP(6),
    invalidation_reason TEXT,
    capacity_released_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_capacity_hold PRIMARY KEY (hold_id),
    CONSTRAINT uk_capacity_hold_hold_session_region UNIQUE (hold_id, session_id, region_id),
    CONSTRAINT fk_capacity_hold_session_region
        FOREIGN KEY (session_id, region_id) REFERENCES content_session (session_id, region_id),
    CONSTRAINT fk_capacity_hold_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_capacity_hold_status
        CHECK (status REGEXP '^(ACTIVE|CONSUMED|EXPIRED|INVALIDATED)$'),
    CONSTRAINT ck_capacity_hold_quantity CHECK (quantity > 0),
    CONSTRAINT ck_capacity_hold_terminal
        CHECK (
            (status = 'ACTIVE' AND terminal_at IS NULL AND capacity_released_at IS NULL)
            OR (status = 'CONSUMED' AND terminal_at IS NOT NULL AND capacity_released_at IS NULL)
            OR ((status = 'EXPIRED' OR status = 'INVALIDATED') AND terminal_at IS NOT NULL AND capacity_released_at IS NOT NULL)
        )
);

CREATE TABLE reservation (
    reservation_id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_no VARCHAR(255) NOT NULL,
    qr_reference VARCHAR(255) NOT NULL,
    region_id BIGINT NOT NULL,
    hold_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT,
    status VARCHAR(30) NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    cancelled_at TIMESTAMP(6),
    cancellation_reason TEXT,
    expired_at TIMESTAMP(6),
    capacity_released_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_reservation PRIMARY KEY (reservation_id),
    CONSTRAINT uk_reservation_qr_reference UNIQUE (qr_reference),
    CONSTRAINT uk_reservation_hold UNIQUE (hold_id),
    CONSTRAINT uk_reservation_reservation_session_region UNIQUE (reservation_id, session_id, region_id),
    CONSTRAINT fk_reservation_hold_session_region
        FOREIGN KEY (hold_id, session_id, region_id)
        REFERENCES capacity_hold (hold_id, session_id, region_id),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_reservation_status
        CHECK (status REGEXP '^(CONFIRMED|CHECKED_IN|CANCELLED|EXPIRED)$'),
    CONSTRAINT ck_reservation_cancelled
        CHECK (
            (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
            OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
        ),
    CONSTRAINT ck_reservation_expired
        CHECK (
            (status = 'EXPIRED' AND expired_at IS NOT NULL AND capacity_released_at IS NULL)
            OR (status <> 'EXPIRED' AND expired_at IS NULL)
        )
);

CREATE TABLE visit (
    visit_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    user_id BIGINT,
    content_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    checked_in_by_user_id BIGINT NOT NULL,
    checkin_method VARCHAR(30) NOT NULL,
    checked_at TIMESTAMP(6) NOT NULL,
    author_unlinked_at TIMESTAMP(6),
    CONSTRAINT pk_visit PRIMARY KEY (visit_id),
    CONSTRAINT uk_visit_reservation UNIQUE (reservation_id),
    CONSTRAINT uk_visit_visit_content_region UNIQUE (visit_id, content_id, region_id),
    CONSTRAINT fk_visit_reservation_session_region
        FOREIGN KEY (reservation_id, session_id, region_id)
        REFERENCES reservation (reservation_id, session_id, region_id),
    CONSTRAINT fk_visit_session_content_region
        FOREIGN KEY (session_id, content_id, region_id)
        REFERENCES content_session (session_id, content_id, region_id),
    CONSTRAINT fk_visit_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_visit_checked_in_by_user
        FOREIGN KEY (checked_in_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_visit_checkin_method CHECK (checkin_method REGEXP '^(QR|RESERVATION_NUMBER)$')
);

CREATE TABLE idempotency_record (
    idempotency_record_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT,
    operation VARCHAR(30) NOT NULL,
    idempotency_key_hash VARCHAR(255),
    request_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    result_code VARCHAR(100),
    result_reservation_id BIGINT,
    result_visit_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (idempotency_record_id),
    CONSTRAINT uk_idempotency_record_actor_operation_key
        UNIQUE (actor_user_id, operation, idempotency_key_hash),
    CONSTRAINT fk_idempotency_record_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_idempotency_record_reservation
        FOREIGN KEY (result_reservation_id) REFERENCES reservation (reservation_id),
    CONSTRAINT fk_idempotency_record_visit FOREIGN KEY (result_visit_id) REFERENCES visit (visit_id),
    CONSTRAINT ck_idempotency_record_operation
        CHECK (operation REGEXP '^(RESERVATION_CONFIRM|CHECK_IN)$'),
    CONSTRAINT ck_idempotency_record_status
        CHECK (status REGEXP '^(PROCESSING|SUCCEEDED|FAILED)$'),
    CONSTRAINT ck_idempotency_record_result
        CHECK (
            ((status = 'PROCESSING' OR status = 'FAILED')
                AND result_reservation_id IS NULL
                AND result_visit_id IS NULL)
            OR (status = 'SUCCEEDED'
                AND operation = 'RESERVATION_CONFIRM'
                AND result_reservation_id IS NOT NULL
                AND result_visit_id IS NULL)
            OR (status = 'SUCCEEDED'
                AND operation = 'CHECK_IN'
                AND result_reservation_id IS NULL
                AND result_visit_id IS NOT NULL)
        )
);

CREATE TABLE review (
    review_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    user_id BIGINT,
    content_id BIGINT NOT NULL,
    rating INT,
    review_text TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    author_unlinked_at TIMESTAMP(6),
    CONSTRAINT pk_review PRIMARY KEY (review_id),
    CONSTRAINT uk_review_visit UNIQUE (visit_id),
    CONSTRAINT fk_review_visit_content_region
        FOREIGN KEY (visit_id, content_id, region_id) REFERENCES visit (visit_id, content_id, region_id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_review_status CHECK (status REGEXP '^(PUBLISHED|DELETED)$'),
    CONSTRAINT ck_review_published
        CHECK (status <> 'PUBLISHED' OR (rating IS NOT NULL AND review_text IS NOT NULL AND deleted_at IS NULL)),
    CONSTRAINT ck_review_deleted
        CHECK (status <> 'DELETED' OR deleted_at IS NOT NULL)
);

CREATE TABLE audit_event (
    audit_event_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(36) NOT NULL,
    region_id BIGINT,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT,
    previous_state VARCHAR(30),
    next_state VARCHAR(30),
    result VARCHAR(10) NOT NULL,
    reason_code VARCHAR(100),
    actor_kind VARCHAR(30) NOT NULL,
    actor_role VARCHAR(30),
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (audit_event_id),
    CONSTRAINT fk_audit_event_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_audit_event_result CHECK (result REGEXP '^(SUCCESS|FAILURE)$'),
    CONSTRAINT ck_audit_event_target_type
        CHECK (target_type REGEXP '^(REGION|OPERATOR_APPLICATION|CONTENT|CONTENT_SESSION|RESERVATION|VISIT|REVIEW)$')
);

CREATE TABLE audit_event_actor_link (
    audit_event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_audit_event_actor_link PRIMARY KEY (audit_event_id),
    CONSTRAINT fk_audit_event_actor_link_audit_event
        FOREIGN KEY (audit_event_id) REFERENCES audit_event (audit_event_id),
    CONSTRAINT fk_audit_event_actor_link_app_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id)
);
