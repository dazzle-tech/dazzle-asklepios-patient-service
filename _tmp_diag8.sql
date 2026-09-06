\pset pager off
\d waseel_sbs_catalog

\echo ===== SBS CATALOG SEARCH 57512 =====
SELECT id, code, left(name,80) AS name, category
FROM waseel_sbs_catalog
WHERE code ILIKE '%57512%' OR name ILIKE '%hand%wrist%'
LIMIT 30;

\echo ===== DEBIT ENC/PATIENT =====
SELECT * FROM billing_debit_account WHERE patient_id = 25;
SELECT * FROM billing_debit_transaction WHERE encounter_id = 106 OR patient_id = 25;

\echo ===== WALLET =====
SELECT * FROM billing_wallet WHERE patient_id = 25;

\echo ===== CHARGE HEADER =====
SELECT * FROM billing_charge WHERE encounter_id = 106;

\echo ===== PATIENT PAYMENTS TABLE =====
SELECT * FROM patient_payments WHERE encounter_id = 106;

\echo ===== LEDGER ENC 106 =====
SELECT id, entry_type, source_channel, amount, charge_line_id, encounter_id, created_date
FROM billing_ledger WHERE encounter_id = 106 ORDER BY id;
