-- Run manually when deploying (spring.jpa.hibernate.ddl-auto=none).
ALTER TABLE claim_request
    ADD COLUMN IF NOT EXISTS validation_errors_json TEXT;
