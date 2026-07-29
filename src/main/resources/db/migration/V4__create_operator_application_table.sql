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
    CONSTRAINT ck_operator_application_approved_review_result
        CHECK (
            status <> 'APPROVED'
            OR (inspected_user_id IS NOT NULL AND rejected_reason IS NULL)
        ),
    CONSTRAINT ck_operator_application_rejected_review_result
        CHECK (
            status <> 'REJECTED'
            OR (inspected_user_id IS NOT NULL AND rejected_reason IS NOT NULL)
        )
);
