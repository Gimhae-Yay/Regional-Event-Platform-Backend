CREATE TABLE user_role_assignment (
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    region_id BIGINT NULL,
    granted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_user_role_assignment PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_assignment_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_user_role_assignment_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_user_role_assignment_visitor_without_region CHECK (
        (`role` <> 'VISITOR' OR region_id IS NULL)
    ),
    CONSTRAINT ck_user_role_assignment_scoped_role_with_region CHECK (
        (`role` = 'VISITOR' OR region_id IS NOT NULL)
    )
);
