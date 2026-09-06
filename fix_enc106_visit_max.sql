-- =============================================================================
-- Patient 25 / Encounter 106 — APPLY visit max-limit 100 + mapping fixes
-- Database: DBOneHealthLocaly
--
-- Findings (before fix):
--   Eligibility Active / eligible / inforce, max_limit=100, copay 20%
--   Encounter header wrongly SELF_PAY (billing already calculated as insurance)
--   Patient share sum = 119.50  → visit max NOT applied (excess 19.50 on X-Ray)
--   Paid wallet 118 + debit 1.50 = 119.50
--   Radiology (PSP 299 / diag 193) has NO SBS mapping / NO price-list item
--
-- After fix (chronological visit pool):
--   Patient total 100.00 | Insurance 497.50 | Net 597.50
--   Only radiology shares change: patient 34→14.50, insurance 136→155.50
--   Debit 1.50 reversed; 18.00 released from payment 75 back to wallet
--
-- REVIEW THEN RUN. This script commits only if validation block passes.
-- =============================================================================

BEGIN;

-- 1) Encounter coverage header
UPDATE patient_encounters
SET coverage_type = 'INSURANCE',
    patient_insurance_id = 17,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 106
  AND patient_id = 25;

-- 2) Fill missing SBS mapping refs on PSP (Cons + CBC already have active mappings)
UPDATE patient_services_and_products
SET waseel_sbs_mapping_id = 4404,
    waseel_sbs_code = 'Cons003',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 289 AND encounter_id = 106;

UPDATE patient_services_and_products
SET waseel_sbs_mapping_id = 3496,
    waseel_sbs_code = '73100-00-80',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 294 AND encounter_id = 106;

-- 3) Apply visit max on radiology only (last line chronologically)
UPDATE patient_services_and_products
SET patient_share_amount = 14.5000,
    insurance_share_amount = 155.5000,
    paid_amount = 14.5000,
    remaining_amount = 0,
    payment_status = 'PAID',
    coverage_status = 'COVERED',
    patient_insurance_id = 17,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 299
  AND encounter_id = 106
  AND patient_id = 25;

-- Keep other PSP insurance link consistent
UPDATE patient_services_and_products
SET patient_insurance_id = 17,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE encounter_id = 106
  AND id IN (289, 294, 295, 296, 297)
  AND (patient_insurance_id IS DISTINCT FROM 17);

-- 4) Charge line 297 (radiology)
UPDATE billing_charge_line
SET patient_responsibility_amount = 14.5000,
    insurance_responsibility_amount = 155.5000,
    allocated_amount = 14.5000,
    outstanding_amount = 155.5000,
    reserved_amount = 0,
    status = 'PARTIALLY_ALLOCATED',
    patient_insurance_id = 17,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 297
  AND encounter_id = 106
  AND patient_service_product_id = 299;

-- 5) Pricing snapshot 297
UPDATE billing_pricing_snapshot
SET patient_responsibility_amount = 14.5000,
    insurance_responsibility_amount = 155.5000,
    calculation_payload = jsonb_set(
        jsonb_set(
            jsonb_set(
                COALESCE(calculation_payload, '{}'::jsonb),
                '{patientResponsibilityAmount}',
                '14.5000'::jsonb
            ),
            '{insuranceResponsibilityAmount}',
            '155.5000'::jsonb
        ),
        '{visitMaxLimit}',
        '100'::jsonb
    ),
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 297
  AND encounter_id = 106
  AND patient_service_product_id = 299;

-- 6) Responsibilities for radiology
UPDATE billing_charge_responsibility
SET responsibility_amount = 14.5000,
    allocated_amount = 14.5000,
    outstanding_amount = 0,
    coverage_percentage = 20.000000,
    copay_amount = 14.5000,
    non_covered_amount = 0,
    patient_insurance_id = 17,
    policy_number = '52332912',
    member_number = '001141675429001',
    status = 'FULLY_ALLOCATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 371
  AND encounter_id = 106
  AND charge_line_id = 297
  AND responsible_party_type = 'PATIENT';

UPDATE billing_charge_responsibility
SET responsibility_amount = 155.5000,
    allocated_amount = 0,
    outstanding_amount = 155.5000,
    coverage_percentage = 80.000000,
    copay_amount = 0,
    non_covered_amount = 0,
    patient_insurance_id = 17,
    policy_number = '52332912',
    member_number = '001141675429001',
    status = 'CALCULATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 372
  AND encounter_id = 106
  AND charge_line_id = 297
  AND responsible_party_type = 'INSURANCE';

-- 7) Reverse radiology debit 1.50
UPDATE billing_allocation
SET remaining_allocated_amount = 0,
    reversed_amount = allocated_amount,
    status = 'REVERSED',
    reversed_date = NOW(),
    reversed_by = 'manual-billing-fix',
    reversal_reason = 'Visit max-limit 100 applied; radiology patient share reduced to 14.50',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 172
  AND encounter_id = 106
  AND allocation_source_type = 'DEBIT'
  AND status = 'ACTIVE';

UPDATE billing_debit_transaction
SET status = 'REVERSED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 120
  AND encounter_id = 106
  AND status = 'COMPLETED';

UPDATE billing_debit_account
SET current_debit_balance = 0,
    total_debit_created = 0,
    total_debit_settled = 0,
    available_credit = credit_limit,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 15
  AND patient_id = 25;

-- 8) Shrink radiology payment allocation/reservation from 32.50 → 14.50 (release 18)
UPDATE billing_allocation
SET allocated_amount = 14.5000,
    remaining_allocated_amount = 14.5000,
    reversed_amount = 0,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 171
  AND encounter_id = 106
  AND status = 'ACTIVE';

UPDATE billing_reservation
SET consumed_amount = 14.5000,
    released_amount = 18.0000,
    remaining_reserved_amount = 0,
    status = 'PARTIALLY_RELEASED',
    released_date = NOW(),
    released_by = 'manual-billing-fix',
    release_reason = 'AMOUNT_REDUCED',
    release_notes = 'Visit max-limit 100; radiology patient share reduced 32.50→14.50',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 109
  AND encounter_id = 106;

-- 9) Wallet: return released 18 to available balance
UPDATE billing_wallet
SET available_balance = available_balance + 18.0000,
    consumed_amount = consumed_amount - 18.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 24
  AND patient_id = 25;

-- 10) Financial document PATIENT (45) — radiology item 135
UPDATE financial_document_items
SET unit_price = 14.5000,
    gross_amount = 14.5000,
    discount_amount = 0,
    net_amount = 14.5000,
    patient_share_amount = 14.5000,
    insurance_share_amount = 0,
    paid_amount = 14.5000,
    remaining_amount = 0,
    insurance_paid_amount = 0,
    insurance_remaining_amount = 0,
    status = 'PAID',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 135
  AND document_id = 45
  AND patient_service_product_id = 299;

UPDATE financial_documents
SET total_amount = 100.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 45
  AND encounter_id = 106
  AND document_subtype = 'PATIENT';

-- 11) Financial document INSURANCE_CLAIM (46) — radiology item 141
UPDATE financial_document_items
SET unit_price = 155.5000,
    gross_amount = 155.5000,
    discount_amount = 0,
    net_amount = 155.5000,
    patient_share_amount = 0,
    insurance_share_amount = 155.5000,
    paid_amount = 0,
    remaining_amount = 155.5000,
    insurance_paid_amount = 0,
    insurance_remaining_amount = 155.5000,
    -- keep existing item_code until a real mapping is created for diag 193
    status = 'PENDING',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 141
  AND document_id = 46
  AND patient_service_product_id = 299;

UPDATE financial_documents
SET total_amount = 497.5000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 46
  AND encounter_id = 106
  AND document_subtype = 'INSURANCE_CLAIM';

-- 12) Charge header totals
UPDATE billing_charge
SET allocated_amount = 100.0000,
    outstanding_amount = 497.5000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 46
  AND encounter_id = 106
  AND patient_id = 25;

-- =============================================================================
-- Validation — rolls back automatically if any check fails
-- =============================================================================
DO $$
DECLARE
    v_cov text;
    v_ins_id bigint;
    v_psp_pat numeric;
    v_psp_ins numeric;
    v_pat_fd numeric;
    v_ins_fd numeric;
    v_alloc numeric;
    v_out numeric;
    v_debit numeric;
    v_active_debit int;
    v_wallet_avail numeric;
    v_wallet_cons numeric;
    v_rad_pat numeric;
    v_rad_ins numeric;
BEGIN
    SELECT coverage_type, patient_insurance_id INTO v_cov, v_ins_id
    FROM patient_encounters WHERE id = 106;

    SELECT COALESCE(SUM(patient_share_amount),0), COALESCE(SUM(insurance_share_amount),0)
      INTO v_psp_pat, v_psp_ins
    FROM patient_services_and_products
    WHERE encounter_id = 106 AND payment_status <> 'CANCELLED';

    SELECT total_amount INTO v_pat_fd FROM financial_documents WHERE id = 45;
    SELECT total_amount INTO v_ins_fd FROM financial_documents WHERE id = 46;

    SELECT allocated_amount, outstanding_amount INTO v_alloc, v_out
    FROM billing_charge WHERE id = 46;

    SELECT current_debit_balance INTO v_debit
    FROM billing_debit_account WHERE patient_id = 25;

    SELECT COUNT(*) INTO v_active_debit
    FROM billing_allocation
    WHERE encounter_id = 106 AND allocation_source_type = 'DEBIT' AND status = 'ACTIVE';

    SELECT available_balance, consumed_amount INTO v_wallet_avail, v_wallet_cons
    FROM billing_wallet WHERE id = 24;

    SELECT patient_share_amount, insurance_share_amount INTO v_rad_pat, v_rad_ins
    FROM patient_services_and_products WHERE id = 299;

    IF v_cov IS DISTINCT FROM 'INSURANCE' OR v_ins_id IS DISTINCT FROM 17 THEN
        RAISE EXCEPTION 'Encounter coverage not fixed: % / %', v_cov, v_ins_id;
    END IF;
    IF v_psp_pat IS DISTINCT FROM 100.0000 OR v_psp_ins IS DISTINCT FROM 497.5000 THEN
        RAISE EXCEPTION 'PSP share mismatch patient=% insurance=%', v_psp_pat, v_psp_ins;
    END IF;
    IF v_pat_fd IS DISTINCT FROM 100.0000 OR v_ins_fd IS DISTINCT FROM 497.5000 THEN
        RAISE EXCEPTION 'FD totals mismatch patient=% insurance=%', v_pat_fd, v_ins_fd;
    END IF;
    IF v_alloc IS DISTINCT FROM 100.0000 OR v_out IS DISTINCT FROM 497.5000 THEN
        RAISE EXCEPTION 'Charge totals mismatch alloc=% out=%', v_alloc, v_out;
    END IF;
    IF v_debit IS DISTINCT FROM 0 OR v_active_debit <> 0 THEN
        RAISE EXCEPTION 'Debit not cleared debit=% active_alloc=%', v_debit, v_active_debit;
    END IF;
    IF v_wallet_cons IS DISTINCT FROM 100.0000 THEN
        RAISE EXCEPTION 'Wallet consumed expected 100 got %', v_wallet_cons;
    END IF;
    IF v_rad_pat IS DISTINCT FROM 14.5000 OR v_rad_ins IS DISTINCT FROM 155.5000 THEN
        RAISE EXCEPTION 'Radiology shares wrong pat=% ins=%', v_rad_pat, v_rad_ins;
    END IF;
END $$;

-- Post-check selects
SELECT id, coverage_type, patient_insurance_id, billing_status
FROM patient_encounters WHERE id = 106;

SELECT id, waseel_sbs_mapping_id, waseel_sbs_code, net_amount,
       patient_share_amount, insurance_share_amount, payment_status
FROM patient_services_and_products
WHERE encounter_id = 106
ORDER BY id;

SELECT id, document_subtype, total_amount FROM financial_documents WHERE encounter_id = 106;

SELECT id, allocated_amount, outstanding_amount, net_amount FROM billing_charge WHERE id = 46;

SELECT current_debit_balance FROM billing_debit_account WHERE patient_id = 25;
SELECT available_balance, consumed_amount FROM billing_wallet WHERE id = 24;

COMMIT;

-- =============================================================================
-- OPTIONAL (separate decision): create radiology mapping for diagnostic_test 193
-- There is NO mapping today. FD currently uses code 57512-03-02 which does NOT
-- exist in waseel_sbs_catalog. Closest Tawuniya codes:
--   57512-03-00  Hand and wrist bilateral     PL item 238  unit 150  disc 25% → net 112.50
--   57512-02-00  Hand/wrist/forearm unilateral PL item 237  unit 105  disc 25% → net 78.75
--   57506-03-11  Wrist 1-2 views unilateral    PL item 233  unit  90  disc 25% → net 67.50
-- Pick the clinically correct SBS, then create mapping + optionally reprice.
-- Example ONLY (do not run until SBS chosen):
--
-- INSERT INTO waseel_item_mapping (
--   item_type, source_id, item_code, item_name, sbs_catalog_id, is_active, notes,
--   created_date, created_by
-- ) VALUES (
--   'RADIOLOGY', 193, '57512-03-00', 'Hand/Wrist X-Ray', 3237, TRUE,
--   'Manual map for enc 106', NOW(), 'manual-billing-fix'
-- );
--
-- Then update PSP 299 / line 297 / snapshots / FD item codes / price list link
-- and recalculate shares with the new net under the same visit max 100 pool.
-- =============================================================================
