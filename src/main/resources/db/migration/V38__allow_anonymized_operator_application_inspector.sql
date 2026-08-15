ALTER TABLE operator_application
    DROP CONSTRAINT ck_operator_application_approved_review_result,
    DROP CONSTRAINT ck_operator_application_rejected_review_result,
    ADD CONSTRAINT ck_operator_application_approved_review_result
        CHECK (status <> 'APPROVED' OR rejected_reason IS NULL),
    ADD CONSTRAINT ck_operator_application_rejected_review_result
        CHECK (status <> 'REJECTED' OR rejected_reason IS NOT NULL);
