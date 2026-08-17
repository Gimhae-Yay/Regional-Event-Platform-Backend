CREATE INDEX idx_content_log_content_status_date_id
    ON content_log (content_id, status, date, id);
