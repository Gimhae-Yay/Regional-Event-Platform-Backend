CREATE TABLE audit_event_actor_link (
    audit_event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_audit_event_actor_link PRIMARY KEY (audit_event_id),
    CONSTRAINT fk_audit_event_actor_link_audit_event
        FOREIGN KEY (audit_event_id) REFERENCES audit_event (audit_event_id),
    CONSTRAINT fk_audit_event_actor_link_app_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id)
);
