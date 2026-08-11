CREATE INDEX idx_mission_region_mission_id
    ON mission (region_id, mission_id);

CREATE INDEX idx_mission_region_status_mission_id
    ON mission (region_id, status, mission_id);
