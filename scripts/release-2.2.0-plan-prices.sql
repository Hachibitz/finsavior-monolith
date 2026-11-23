-- Apply once to the MySQL database before deploying release 2.2.0 if Hibernate does not alter the schema.
-- Adds provider/sync tracking columns to plan_display_price and updates the unique key.
ALTER TABLE plan_display_price
    ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN external_price_id VARCHAR(128) NULL,
    ADD COLUMN last_synced_at DATETIME(6) NULL;

ALTER TABLE plan_display_price
    DROP INDEX uk_plan_display_price,
    ADD CONSTRAINT uk_plan_display_price_provider UNIQUE (plan_tier, billing_cycle, currency, provider);
