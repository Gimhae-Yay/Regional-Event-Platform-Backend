ALTER TABLE operator_application
    DROP CONSTRAINT ck_operator_application_approved_review_result;

ALTER TABLE operator_application
    DROP CONSTRAINT ck_operator_application_rejected_review_result;

ALTER TABLE operator_application
    ADD CONSTRAINT ck_operator_application_approved_review_result
        CHECK (status <> 'APPROVED' OR rejected_reason IS NULL);

ALTER TABLE operator_application
    ADD CONSTRAINT ck_operator_application_rejected_review_result
        CHECK (status <> 'REJECTED' OR rejected_reason IS NOT NULL);
