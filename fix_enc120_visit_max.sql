-- =============================================================================
-- Patient 10 / Encounter 120 (E00219) — one script, run once
-- Database: DBOneHealthLocaly
--
-- Does NOT touch diagnostic_test (internal_code stays RAD-DEXA-LS).
-- Item mapping only: waseel_item_mapping 3015 → RADIOLOGY / source 209 /
--   SBS catalog 3029 / 12306-00-00, then apply Tawuniya PL coverage on billing.
--
-- Eligibility: insurance 8 Tawuniya, Active / eligible / inforce, copay 20%,
--   visit max_limit 100 already consumed on COVERED lines.
-- DEXA today: NOT_COVERED cash 50. After: COVERED net 150, patient 0, ins 150.
-- Totals after: Patient 100 | Insurance 976.25 | Net 1076.25 | Gross 1420 | Disc 343.75
-- Reverse DEXA debit 50 only. No financial documents on this encounter.
-- =============================================================================

BEGIN;

-- 1) Waseel item mapping only (do not change diagnostic_test)
UPDATE waseel_item_mapping
SET item_type = 'RADIOLOGY',
    source_id = 209,
    item_code = '12306-00-00',
    item_name = 'Bone densitometry using dual energy x-ray absorptiometry',
    sbs_catalog_id = 3029,
    is_active = TRUE,
    notes = 'DEXA Lumbar Spine → SBS 12306-00-00 (enc 120 billing fix)',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 3015
  AND source_id = 209;

UPDATE price_list_setup_item
SET waseel_item_mapping_id = 3015,
    sbs_catalog_id = 3029,
    item_type = 'RADIOLOGY',
    source_id = 209,
    item_code = '12306-00-00',
    is_active = TRUE,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 30
  AND price_list_setup_id = 1
  AND source_id = 209;

-- 2) Encounter header
UPDATE patient_encounters
SET coverage_type = 'INSURANCE',
    patient_insurance_id = 8,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 120
  AND patient_id = 10;

-- 3) PSP mapping refs (GP + CBC already priced correctly)
UPDATE patient_services_and_products
SET waseel_sbs_mapping_id = 3494,
    waseel_sbs_code = 'Cons001',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 316
  AND encounter_id = 120
  AND patient_id = 10;

UPDATE patient_services_and_products
SET waseel_sbs_mapping_id = 3496,
    waseel_sbs_code = '73100-00-80',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 319
  AND encounter_id = 120
  AND patient_id = 10;

-- 4) DEXA PSP: Tawuniya 200 − 25% = 150, visit max already 100 → patient 0
UPDATE patient_services_and_products
SET unit_price = 200.00,
    discount_amount = 50.00,
    total_amount = 150.0000,
    gross_amount = 200.0000,
    net_amount = 150.0000,
    patient_share_amount = 0.0000,
    insurance_share_amount = 150.0000,
    paid_amount = 0.0000,
    remaining_amount = 0.0000,
    payment_status = 'PENDING',
    coverage_status = 'COVERED',
    not_covered_reason = NULL,
    price_source = 'PRICE_LIST',
    patient_insurance_id = 8,
    waseel_sbs_mapping_id = 3015,
    waseel_sbs_code = '12306-00-00',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 331
  AND encounter_id = 120
  AND patient_id = 10
  AND diagnostic_test_id = 209;

-- 5) Charge line
UPDATE billing_charge_line
SET item_code = '12306-00-00',
    item_description = 'Bone densitometry using dual energy x-ray absorptiometry',
    unit_price = 200.0000,
    gross_amount = 200.0000,
    discount_amount = 50.0000,
    net_amount = 150.0000,
    patient_responsibility_amount = 0.0000,
    insurance_responsibility_amount = 150.0000,
    allocated_amount = 0.0000,
    outstanding_amount = 150.0000,
    reserved_amount = 0,
    status = 'OPEN',
    patient_insurance_id = 8,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 329
  AND encounter_id = 120
  AND patient_service_product_id = 331;

-- 6) Pricing snapshot
UPDATE billing_pricing_snapshot
SET price_list_id = 1,
    price_list_item_id = 30,
    pricing_source = 'INSURANCE_PRICE_LIST',
    price_source = 'PRICE_LIST',
    price_list_name = 'One Health TAWUNIYA Price List',
    price_list_item_code = '12306-00-00',
    base_unit_price = 200.0000,
    gross_amount = 200.0000,
    discount_type = 'PERCENTAGE',
    discount_rate = 25.000000,
    discount_amount = 50.0000,
    taxable_amount = 150.0000,
    net_amount = 150.0000,
    patient_responsibility_amount = 0.0000,
    insurance_responsibility_amount = 150.0000,
    setup_source_id = 209,
    status = 'ACTIVE',
    calculation_payload = jsonb_build_object(
        'quantity', 1,
        'netAmount', 150.0000,
        'unitPrice', 200.0000,
        'grossAmount', 200.0000,
        'priceListId', 1,
        'priceSource', 'PRICE_LIST',
        'pricingSource', 'INSURANCE_PRICE_LIST',
        'discountAmount', 50.0000,
        'discountRate', 25.000000,
        'priceListItemId', 30,
        'priceListItemCode', '12306-00-00',
        'patientResponsibilityAmount', 0.0000,
        'insuranceResponsibilityAmount', 150.0000,
        'visitMaxLimit', 100
    ),
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 329
  AND encounter_id = 120
  AND patient_service_product_id = 331;

-- 7) Responsibilities
UPDATE billing_charge_responsibility
SET responsibility_amount = 0.0000,
    allocated_amount = 0.0000,
    outstanding_amount = 0.0000,
    coverage_percentage = 0,
    copay_amount = 0.0000,
    non_covered_amount = 0.0000,
    patient_insurance_id = 8,
    policy_number = '52332912',
    member_number = '001049911348001',
    status = 'CALCULATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 424
  AND encounter_id = 120
  AND charge_line_id = 329
  AND responsible_party_type = 'PATIENT';

INSERT INTO billing_charge_responsibility (
    charge_id, charge_line_id, patient_service_product_id,
    patient_id, encounter_id,
    responsible_party_type, responsibility_role,
    patient_insurance_id, policy_number, member_number,
    responsibility_amount, allocated_amount, outstanding_amount,
    coverage_percentage, deductible_amount, copay_amount,
    coinsurance_amount, non_covered_amount, contractual_adjustment_amount,
    currency, status, pre_authorization_required, pre_authorization_status,
    effective_date, idempotency_key,
    created_by, created_date, last_modified_by, last_modified_date
)
SELECT
    53, 329, 331, 10, 120,
    'INSURANCE', 'PRIMARY',
    8, '52332912', '001049911348001',
    150.0000, 0, 150.0000,
    100.000000, 0, 0, 0, 0, 0,
    'SAR', 'CALCULATED', FALSE, 'NOT_REQUIRED',
    NOW(),
    'FIX120:RESPONSIBILITY:INSURANCE:PSP:331',
    'manual-billing-fix', NOW(), 'manual-billing-fix', NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM billing_charge_responsibility x
    WHERE x.charge_line_id = 329
      AND x.responsible_party_type = 'INSURANCE'
);

-- 8) Reverse DEXA debit 50 only
UPDATE billing_allocation
SET remaining_allocated_amount = 0,
    reversed_amount = allocated_amount,
    status = 'REVERSED',
    reversed_date = NOW(),
    reversed_by = 'manual-billing-fix',
    reversal_reason = 'DEXA mapped to Tawuniya PL; visit max 100 already consumed',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 76
  AND encounter_id = 120
  AND patient_service_product_id = 331
  AND allocation_source_type = 'DEBIT'
  AND status = 'ACTIVE';

UPDATE billing_debit_transaction
SET status = 'REVERSED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 55
  AND encounter_id = 120
  AND amount = 50.0000
  AND status = 'COMPLETED';

UPDATE billing_debit_account
SET current_debit_balance = current_debit_balance - 50.0000,
    total_debit_created = total_debit_created - 50.0000,
    available_credit = available_credit + 50.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 3
  AND patient_id = 10
  AND current_debit_balance >= 50.0000;

-- 9) Charge header
UPDATE billing_charge
SET gross_amount = 1420.0000,
    discount_amount = 343.7500,
    net_amount = 1076.2500,
    allocated_amount = 100.0000,
    outstanding_amount = 976.2500,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 53
  AND encounter_id = 120
  AND patient_id = 10;

-- 10) Validate then commit
DO $$
DECLARE
    v_cov text;
    v_pi bigint;
    v_pat numeric;
    v_ins numeric;
    v_dexa_pat numeric;
    v_dexa_ins numeric;
    v_dexa_cov text;
    v_dexa_code text;
    v_fd int;
    v_alloc_status text;
    v_txn_status text;
    v_debit_bal numeric;
    v_map316 bigint;
    v_map319 bigint;
    v_diag_code text;
    v_wim_src bigint;
    v_wim_cat bigint;
    v_wim_code text;
    v_pl_map bigint;
BEGIN
    SELECT coverage_type, patient_insurance_id
      INTO v_cov, v_pi
    FROM patient_encounters
    WHERE id = 120 AND patient_id = 10;

    IF v_cov IS DISTINCT FROM 'INSURANCE' OR v_pi IS DISTINCT FROM 8 THEN
        RAISE EXCEPTION 'Encounter header not INSURANCE/8: coverage=% pi=%', v_cov, v_pi;
    END IF;

    SELECT COALESCE(SUM(patient_share_amount), 0),
           COALESCE(SUM(insurance_share_amount), 0)
      INTO v_pat, v_ins
    FROM patient_services_and_products
    WHERE encounter_id = 120
      AND coverage_status = 'COVERED';

    IF v_pat IS DISTINCT FROM 100.0000 THEN
        RAISE EXCEPTION 'Covered patient share=% expected 100', v_pat;
    END IF;
    IF v_ins IS DISTINCT FROM 976.2500 THEN
        RAISE EXCEPTION 'Insurance share=% expected 976.25', v_ins;
    END IF;

    SELECT patient_share_amount, insurance_share_amount,
           coverage_status, waseel_sbs_code
      INTO v_dexa_pat, v_dexa_ins, v_dexa_cov, v_dexa_code
    FROM patient_services_and_products
    WHERE id = 331 AND encounter_id = 120;

    IF v_dexa_cov IS DISTINCT FROM 'COVERED'
       OR v_dexa_pat IS DISTINCT FROM 0.0000
       OR v_dexa_ins IS DISTINCT FROM 150.0000
       OR v_dexa_code IS DISTINCT FROM '12306-00-00' THEN
        RAISE EXCEPTION 'DEXA not corrected pat=% ins=% cov=% code=%',
            v_dexa_pat, v_dexa_ins, v_dexa_cov, v_dexa_code;
    END IF;

    SELECT waseel_sbs_mapping_id INTO v_map316
    FROM patient_services_and_products WHERE id = 316;
    SELECT waseel_sbs_mapping_id INTO v_map319
    FROM patient_services_and_products WHERE id = 319;

    IF v_map316 IS DISTINCT FROM 3494 OR v_map319 IS DISTINCT FROM 3496 THEN
        RAISE EXCEPTION 'Missing SBS mapping refs GP=% CBC=%', v_map316, v_map319;
    END IF;

    SELECT internal_code INTO v_diag_code
    FROM diagnostic_test WHERE id = 209;
    IF v_diag_code IS DISTINCT FROM 'RAD-DEXA-LS' THEN
        RAISE EXCEPTION 'diagnostic_test 209 must stay RAD-DEXA-LS, found %', v_diag_code;
    END IF;

    SELECT source_id, sbs_catalog_id, item_code
      INTO v_wim_src, v_wim_cat, v_wim_code
    FROM waseel_item_mapping WHERE id = 3015;
    SELECT waseel_item_mapping_id INTO v_pl_map
    FROM price_list_setup_item WHERE id = 30;

    IF v_wim_src IS DISTINCT FROM 209
       OR v_wim_cat IS DISTINCT FROM 3029
       OR v_wim_code IS DISTINCT FROM '12306-00-00'
       OR v_pl_map IS DISTINCT FROM 3015 THEN
        RAISE EXCEPTION 'Item mapping not aligned src=% cat=% code=% pl_map=%',
            v_wim_src, v_wim_cat, v_wim_code, v_pl_map;
    END IF;

    SELECT COUNT(*) INTO v_fd
    FROM financial_documents
    WHERE encounter_id = 120;
    IF v_fd <> 0 THEN
        RAISE EXCEPTION 'Unexpected financial documents exist count=%', v_fd;
    END IF;

    SELECT status INTO v_alloc_status FROM billing_allocation WHERE id = 76;
    SELECT status INTO v_txn_status FROM billing_debit_transaction WHERE id = 55;
    SELECT current_debit_balance INTO v_debit_bal
    FROM billing_debit_account WHERE id = 3 AND patient_id = 10;

    IF v_alloc_status IS DISTINCT FROM 'REVERSED' THEN
        RAISE EXCEPTION 'DEXA allocation 76 status=%', v_alloc_status;
    END IF;
    IF v_txn_status IS DISTINCT FROM 'REVERSED' THEN
        RAISE EXCEPTION 'DEXA debit txn 55 status=%', v_txn_status;
    END IF;
    IF v_debit_bal IS DISTINCT FROM 349.0000 THEN
        RAISE EXCEPTION 'Debit balance=% expected 349', v_debit_bal;
    END IF;
END $$;

COMMIT;
