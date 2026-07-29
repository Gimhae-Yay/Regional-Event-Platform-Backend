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
