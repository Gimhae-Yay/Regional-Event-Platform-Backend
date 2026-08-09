CREATE TEMPORARY TABLE region_code_normalization_validation (
    is_valid BOOLEAN NOT NULL,
    CONSTRAINT ck_region_code_normalization_validation CHECK (is_valid = FALSE)
);

INSERT INTO region_code_normalization_validation (is_valid)
SELECT TRUE
WHERE EXISTS (
    SELECT 1
    FROM region
    WHERE CHAR_LENGTH(region_code) > 50
        OR NOT REGEXP_LIKE(region_code, '^[A-Za-z][A-Za-z0-9]*(-[A-Za-z0-9]+)*$', 'c')
);

INSERT INTO region_code_normalization_validation (is_valid)
SELECT TRUE
FROM (
    SELECT UPPER(region_code) AS normalized_region_code
    FROM region
    GROUP BY UPPER(region_code)
    HAVING COUNT(*) > 1
) AS region_code_conflicts;

DROP TABLE region_code_normalization_validation;

UPDATE region
SET region_code = UPPER(region_code)
WHERE region_code <> UPPER(region_code);

ALTER TABLE region
    ADD CONSTRAINT ck_region_region_code_normalized
        CHECK (
            CHAR_LENGTH(region_code) BETWEEN 1 AND 50
            AND REGEXP_LIKE(region_code, '^[A-Z][A-Z0-9]*(-[A-Z0-9]+)*$', 'c')
        );
