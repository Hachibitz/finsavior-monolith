-- Migrates legacy recurring bills from bill_table_data.is_recurrent to the
-- fixed_bill template model introduced in release-2.0.0.
--
-- Assumptions:
-- 1. Hibernate has already created the fixed_bill table and fixed_bill_id column.
-- 2. Legacy recurring rows have is_recurrent = true and fixed_bill_id IS NULL.
-- 3. Legacy records should use the yearly upfront generation strategy.
--
-- Safe to re-run: only rows without fixed_bill_id are considered.

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS legacy_fixed_bill_groups;

CREATE TEMPORARY TABLE legacy_fixed_bill_groups (
    migration_group_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bill_name VARCHAR(100) NOT NULL,
    bill_value DECIMAL(38, 2) NOT NULL,
    bill_description VARCHAR(255) NULL,
    bill_category VARCHAR(60) NULL,
    bill_type VARCHAR(255) NOT NULL,
    bill_table VARCHAR(255) NOT NULL,
    payment_type VARCHAR(30) NULL,
    card_id VARCHAR(64) NULL,
    day_of_month INT NULL,
    start_bill_date VARCHAR(20) NOT NULL,
    fixed_bill_id BIGINT NULL,
    INDEX idx_legacy_fixed_bill_group_match (
        user_id,
        bill_name,
        bill_value,
        bill_type,
        bill_table
    )
);

INSERT INTO legacy_fixed_bill_groups (
    user_id,
    bill_name,
    bill_value,
    bill_description,
    bill_category,
    bill_type,
    bill_table,
    payment_type,
    card_id,
    day_of_month,
    start_bill_date
)
SELECT
    b.user_id,
    LEFT(b.bill_name, 100),
    b.bill_value,
    LEFT(b.bill_description, 255),
    LEFT(b.bill_category, 60),
    b.bill_type,
    b.bill_table,
    LEFT(b.payment_type, 30),
    LEFT(b.card_id, 64),
    DAY(MIN(b.purchase_date)) AS day_of_month,
    SUBSTRING_INDEX(
        GROUP_CONCAT(b.bill_date ORDER BY b.id ASC SEPARATOR '||'),
        '||',
        1
    ) AS start_bill_date
FROM bill_table_data b
WHERE b.is_recurrent = TRUE
  AND b.fixed_bill_id IS NULL
GROUP BY
    b.user_id,
    LEFT(b.bill_name, 100),
    b.bill_value,
    LEFT(b.bill_description, 255),
    LEFT(b.bill_category, 60),
    b.bill_type,
    b.bill_table,
    LEFT(b.payment_type, 30),
    LEFT(b.card_id, 64);

INSERT INTO fixed_bill (
    user_id,
    bill_name,
    bill_value,
    bill_description,
    bill_category,
    bill_type,
    bill_table,
    payment_type,
    card_id,
    day_of_month,
    start_bill_date,
    generation_strategy,
    active,
    del_fg,
    insert_dtm,
    insert_id,
    update_dtm,
    update_id
)
SELECT
    g.user_id,
    g.bill_name,
    g.bill_value,
    g.bill_description,
    g.bill_category,
    g.bill_type,
    g.bill_table,
    g.payment_type,
    g.card_id,
    g.day_of_month,
    g.start_bill_date,
    'YEARLY_UPFRONT',
    TRUE,
    'N',
    NOW(),
    'finsavior-monolith',
    NOW(),
    'finsavior-monolith'
FROM legacy_fixed_bill_groups g;

UPDATE legacy_fixed_bill_groups g
JOIN fixed_bill fb
  ON fb.user_id = g.user_id
 AND fb.bill_name = g.bill_name
 AND fb.bill_value = g.bill_value
 AND (fb.bill_description <=> g.bill_description)
 AND (fb.bill_category <=> g.bill_category)
 AND fb.bill_type = g.bill_type
 AND fb.bill_table = g.bill_table
 AND (fb.payment_type <=> g.payment_type)
 AND (fb.card_id <=> g.card_id)
 AND (fb.day_of_month <=> g.day_of_month)
 AND fb.start_bill_date = g.start_bill_date
 AND fb.generation_strategy = 'YEARLY_UPFRONT'
 AND fb.insert_id = 'finsavior-monolith'
SET g.fixed_bill_id = fb.id;

UPDATE bill_table_data b
JOIN legacy_fixed_bill_groups g
  ON b.user_id = g.user_id
 AND LEFT(b.bill_name, 100) = g.bill_name
 AND b.bill_value = g.bill_value
 AND (LEFT(b.bill_description, 255) <=> g.bill_description)
 AND (LEFT(b.bill_category, 60) <=> g.bill_category)
 AND b.bill_type = g.bill_type
 AND b.bill_table = g.bill_table
 AND (LEFT(b.payment_type, 30) <=> g.payment_type)
 AND (LEFT(b.card_id, 64) <=> g.card_id)
SET b.fixed_bill_id = g.fixed_bill_id,
    b.update_dtm = NOW(),
    b.update_id = 'finsavior-monolith'
WHERE b.is_recurrent = TRUE
  AND b.fixed_bill_id IS NULL;

SELECT
    COUNT(*) AS fixed_bill_templates_created
FROM legacy_fixed_bill_groups;

SELECT
    COUNT(*) AS legacy_recurrent_rows_remaining
FROM bill_table_data
WHERE is_recurrent = TRUE
  AND fixed_bill_id IS NULL;

COMMIT;
