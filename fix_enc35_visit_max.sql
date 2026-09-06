-- =============================================================================
-- Patient 14 / Encounter 35 (E00134) — REVIEW ONLY, do not auto-apply
-- Database: DBOneHealthLocaly
--
-- Patient: ALI TALAL ALAAEDDINE (P113)
-- Eligibility: insurance 12 Tawuniya, Active / eligible / inforce
--   request 92 SUCCESS, response 343293082, snapshot 8
--   copay 20%, visit max_limit = 100  ← NOT applied today
--   Health Benefit Plan Coverage also has per-service maxcopay 75 (not binding)
--
-- Mapping: ALL 9 items have active SBS mapping AND Tawuniya PL items (PL 1,
--   payer nphies 7000911508). Billing ignored that and marked 8 labs
--   NOT_COVERED / DEFAULT cash (not_covered_reason = NOT_IN_INSURANCE_PRICE_LIST).
--   Visit max is skipped for NOT_COVERED cash items, so patient share became
--   430 instead of 70.50.
--
-- After fix (Tawuniya PL 25% disc, 20% copay; visit pool 100 is NOT hit):
--   Gross 440.00 | Disc 87.50 | Net 352.50 | Patient 70.50 | Insurance 282.00
--   Payment 2 (18) stays on consultation. Remaining patient copay 52.50 unpaid.
--   Reverse all debit (412). Do NOT touch wallet (encounters 61/74/75 share it).
--
-- Financial documents:
--   PATIENT INV 17: 430 → 70.50 (keep 9 lines; labs become copay PENDING)
--   INSURANCE_CLAIM INV 18: 72 → 282.00 (add 8 lab lines; fix cons paid 18→0)
--
-- Claim reference on INV 18: CLM-E00134-1788370828371
--   claim_request is empty (not submitted to Waseel yet). Rebuild / submit
--   from Claims screen AFTER this fix.
--
-- REVIEW THEN RUN. Commits only if the validation block passes.
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS tmp_enc35_map;
CREATE TEMP TABLE tmp_enc35_map AS
SELECT *
FROM (VALUES
-- psp, line, snap, resp_pat, fd_pat, catalog, pl_item, map_id, unit, disc, net, pat, ins, sbs, item_type, keep_paid
-- visit order = PSP created_date. Copay 20% never reaches visit max 100 (remaining 29.50).
(11,  9,  9, 10, 28, 29272, 2684, 4404, 90.0000,  0.0000, 90.0000, 18.0000, 72.0000, 'Cons003',     'SERVICE',    TRUE),
(13, 12, 12, 15, 30,   148,  320, 3515, 40.0000, 10.0000, 30.0000,  6.0000, 24.0000, '73050-00-72', 'LABORATORY', FALSE),
(14, 15, 15, 18, 33,   151,  478, 3526, 50.0000, 12.5000, 37.5000,  7.5000, 30.0000, '73050-23-80', 'LABORATORY', FALSE),
(15, 16, 16, 19, 34,   146,  382, 3521, 30.0000,  7.5000, 22.5000,  4.5000, 18.0000, '73050-09-80', 'LABORATORY', FALSE),
(16, 11, 11, 14, 29,   153,  560, 3535, 45.0000, 11.2500, 33.7500,  6.7500, 27.0000, '73050-36-20', 'LABORATORY', FALSE),
(17, 13, 13, 16, 31,   144,  423, 3523, 20.0000,  5.0000, 15.0000,  3.0000, 12.0000, '73050-15-50', 'LABORATORY', FALSE),
(18, 14, 14, 17, 32,   147,  569, 3537, 45.0000, 11.2500, 33.7500,  6.7500, 27.0000, '73050-37-30', 'LABORATORY', FALSE),
(25, 23, 23, 26, 35,   139,  688, 3544, 45.0000, 11.2500, 33.7500,  6.7500, 27.0000, '73100-00-10', 'LABORATORY', FALSE),
(26, 24, 24, 27, 36,   138,  691, 3496, 75.0000, 18.7500, 56.2500, 11.2500, 45.0000, '73100-00-80', 'LABORATORY', FALSE)
) AS t(
    psp_id, line_id, snap_id, resp_pat_id, fd_pat_id, catalog_id, pl_item_id, map_id,
    unit_price, discount_amount, net_amount, patient_share, insurance_share,
    sbs_code, item_type, keep_patient_paid
);

-- 1) Reverse every debit allocation on this visit (labs 412)
UPDATE billing_allocation a
SET remaining_allocated_amount = 0,
    reversed_amount = a.allocated_amount,
    status = 'REVERSED',
    reversed_date = NOW(),
    reversed_by = 'manual-billing-fix',
    reversal_reason = 'Encounter 35 labs moved to Tawuniya PL; visit max-limit 100 not hit; debit no longer required',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE a.encounter_id = 35
  AND a.allocation_source_type = 'DEBIT'
  AND a.status = 'ACTIVE';

UPDATE billing_debit_transaction
SET status = 'REVERSED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE encounter_id = 35
  AND status = 'COMPLETED';

UPDATE billing_debit_account
SET current_debit_balance = 0,
    total_debit_created = 0,
    total_debit_settled = 0,
    available_credit = credit_limit,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 8
  AND patient_id = 14;

-- 2) PSP: mapping + Tawuniya prices + 20% copay shares
UPDATE patient_services_and_products psp
SET unit_price = m.unit_price,
    discount_amount = m.discount_amount,
    total_amount = m.net_amount,
    gross_amount = m.unit_price,
    net_amount = m.net_amount,
    patient_share_amount = m.patient_share,
    insurance_share_amount = m.insurance_share,
    paid_amount = CASE WHEN m.keep_patient_paid THEN m.patient_share ELSE 0 END,
    remaining_amount = CASE WHEN m.keep_patient_paid THEN 0 ELSE m.patient_share END,
    payment_status = CASE WHEN m.keep_patient_paid THEN 'PAID' ELSE 'PENDING' END,
    coverage_status = 'COVERED',
    not_covered_reason = NULL,
    price_source = 'PRICE_LIST',
    patient_insurance_id = 12,
    waseel_sbs_mapping_id = m.map_id,
    waseel_sbs_code = m.sbs_code,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
WHERE psp.id = m.psp_id
  AND psp.encounter_id = 35
  AND psp.patient_id = 14;

-- 3) Charge lines
UPDATE billing_charge_line l
SET item_code = m.sbs_code,
    unit_price = m.unit_price,
    gross_amount = m.unit_price,
    discount_amount = m.discount_amount,
    net_amount = m.net_amount,
    patient_responsibility_amount = m.patient_share,
    insurance_responsibility_amount = m.insurance_share,
    allocated_amount = CASE WHEN m.keep_patient_paid THEN m.patient_share ELSE 0 END,
    outstanding_amount = CASE
                           WHEN m.keep_patient_paid THEN m.insurance_share
                           ELSE m.net_amount
                         END,
    reserved_amount = 0,
    status = CASE
               WHEN m.keep_patient_paid AND m.insurance_share > 0 THEN 'PARTIALLY_ALLOCATED'
               WHEN m.keep_patient_paid THEN 'ALLOCATED'
               ELSE 'OPEN'
             END,
    patient_insurance_id = 12,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
WHERE l.id = m.line_id
  AND l.encounter_id = 35;

-- 4) Pricing snapshots
UPDATE billing_pricing_snapshot s
SET price_list_id = 1,
    price_list_item_id = m.pl_item_id,
    pricing_source = 'INSURANCE_PRICE_LIST',
    price_source = 'PRICE_LIST',
    price_list_name = 'One Health TAWUNIYA Price List',
    price_list_item_code = m.sbs_code,
    base_unit_price = m.unit_price,
    gross_amount = m.unit_price,
    discount_type = CASE WHEN m.discount_amount > 0 THEN 'PERCENTAGE' ELSE NULL END,
    discount_rate = CASE WHEN m.discount_amount > 0 THEN 25.000000 ELSE 0 END,
    discount_amount = m.discount_amount,
    taxable_amount = m.net_amount,
    net_amount = m.net_amount,
    patient_responsibility_amount = m.patient_share,
    insurance_responsibility_amount = m.insurance_share,
    setup_source_id = m.catalog_id,
    status = 'ACTIVE',
    calculation_payload = jsonb_build_object(
        'quantity', 1,
        'netAmount', m.net_amount,
        'unitPrice', m.unit_price,
        'grossAmount', m.unit_price,
        'priceListId', 1,
        'priceSource', 'PRICE_LIST',
        'pricingSource', 'INSURANCE_PRICE_LIST',
        'discountAmount', m.discount_amount,
        'priceListItemId', m.pl_item_id,
        'priceListItemCode', m.sbs_code,
        'patientResponsibilityAmount', m.patient_share,
        'insuranceResponsibilityAmount', m.insurance_share,
        'visitMaxLimit', 100,
        'visitMaxConsumedAfter', 70.50
    ),
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
WHERE s.id = m.snap_id
  AND s.encounter_id = 35;

-- 5) Patient responsibilities
UPDATE billing_charge_responsibility r
SET responsibility_amount = m.patient_share,
    allocated_amount = CASE WHEN m.keep_patient_paid THEN m.patient_share ELSE 0 END,
    outstanding_amount = CASE WHEN m.keep_patient_paid THEN 0 ELSE m.patient_share END,
    coverage_percentage = 20.000000,
    copay_amount = m.patient_share,
    non_covered_amount = 0,
    patient_insurance_id = 12,
    policy_number = '52332912',
    member_number = '002421981909001',
    status = CASE
               WHEN m.keep_patient_paid THEN 'FULLY_ALLOCATED'
               ELSE 'CALCULATED'
             END,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
WHERE r.id = m.resp_pat_id
  AND r.encounter_id = 35;

-- Existing insurance resp (consultation 11)
UPDATE billing_charge_responsibility r
SET responsibility_amount = m.insurance_share,
    allocated_amount = 0,
    outstanding_amount = m.insurance_share,
    coverage_percentage = 80.000000,
    copay_amount = 0,
    non_covered_amount = 0,
    patient_insurance_id = 12,
    policy_number = '52332912',
    member_number = '002421981909001',
    status = 'CALCULATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
WHERE r.charge_line_id = m.line_id
  AND r.encounter_id = 35
  AND r.responsible_party_type = 'INSURANCE';

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
    l.charge_id,
    m.line_id,
    m.psp_id,
    14,
    35,
    'INSURANCE',
    'PRIMARY',
    12,
    '52332912',
    '002421981909001',
    m.insurance_share,
    0,
    m.insurance_share,
    80.000000,
    0, 0, 0, 0, 0,
    'SAR',
    'CALCULATED',
    FALSE,
    'NOT_REQUIRED',
    NOW(),
    'FIX35:RESPONSIBILITY:INSURANCE:PSP:' || m.psp_id,
    'manual-billing-fix',
    NOW(),
    'manual-billing-fix',
    NOW()
FROM tmp_enc35_map m
JOIN billing_charge_line l
  ON l.id = m.line_id
 AND l.encounter_id = 35
WHERE m.insurance_share > 0
  AND NOT EXISTS (
      SELECT 1
      FROM billing_charge_responsibility x
      WHERE x.charge_line_id = m.line_id
        AND x.responsible_party_type = 'INSURANCE'
  );

-- 6) PATIENT financial document 17
UPDATE financial_document_items f
SET unit_price = m.patient_share,
    gross_amount = CASE
        WHEN m.net_amount = 0 THEN 0
        ELSE ROUND(m.unit_price * m.patient_share / m.net_amount, 4)
    END,
    discount_amount = CASE
        WHEN m.net_amount = 0 THEN 0
        ELSE ROUND(m.discount_amount * m.patient_share / m.net_amount, 4)
    END,
    net_amount = m.patient_share,
    patient_share_amount = m.patient_share,
    insurance_share_amount = 0,
    paid_amount = CASE WHEN m.keep_patient_paid THEN m.patient_share ELSE 0 END,
    remaining_amount = CASE WHEN m.keep_patient_paid THEN 0 ELSE m.patient_share END,
    insurance_paid_amount = 0,
    insurance_remaining_amount = 0,
    item_code = m.sbs_code,
    status = CASE WHEN m.keep_patient_paid THEN 'PAID' ELSE 'PENDING' END,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
WHERE f.id = m.fd_pat_id
  AND f.document_id = 17
  AND m.patient_share > 0;

UPDATE financial_documents
SET total_amount = 70.5000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 17
  AND encounter_id = 35
  AND document_subtype = 'PATIENT';

-- 7) INSURANCE_CLAIM financial document 18
UPDATE financial_document_items f
SET unit_price = m.insurance_share,
    gross_amount = CASE
        WHEN m.net_amount = 0 THEN 0
        ELSE ROUND(m.unit_price * m.insurance_share / m.net_amount, 4)
    END,
    discount_amount = CASE
        WHEN m.net_amount = 0 THEN 0
        ELSE ROUND(m.discount_amount * m.insurance_share / m.net_amount, 4)
    END,
    net_amount = m.insurance_share,
    patient_share_amount = 0,
    insurance_share_amount = m.insurance_share,
    paid_amount = 0,
    remaining_amount = m.insurance_share,
    insurance_paid_amount = 0,
    insurance_remaining_amount = m.insurance_share,
    item_code = m.sbs_code,
    status = 'PENDING',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc35_map m
JOIN financial_document_items x
  ON x.document_id = 18
 AND x.patient_service_product_id = m.psp_id
WHERE f.id = x.id
  AND m.insurance_share > 0;

INSERT INTO financial_document_items (
    document_id, patient_service_product_id, billing_charge_line_id,
    item_code, item_description, quantity,
    unit_price, gross_amount, discount_amount, tax_amount, net_amount,
    patient_share_amount, insurance_share_amount,
    paid_amount, remaining_amount,
    insurance_paid_amount, insurance_remaining_amount,
    status, currency, created_by, created_date, last_modified_by, last_modified_date
)
SELECT
    18,
    m.psp_id,
    m.line_id,
    m.sbs_code,
    l.item_description,
    1,
    m.insurance_share,
    CASE WHEN m.net_amount = 0 THEN 0
         ELSE ROUND(m.unit_price * m.insurance_share / m.net_amount, 4) END,
    CASE WHEN m.net_amount = 0 THEN 0
         ELSE ROUND(m.discount_amount * m.insurance_share / m.net_amount, 4) END,
    0,
    m.insurance_share,
    0,
    m.insurance_share,
    0,
    m.insurance_share,
    0,
    m.insurance_share,
    'PENDING',
    'SAR',
    'manual-billing-fix',
    NOW(),
    'manual-billing-fix',
    NOW()
FROM tmp_enc35_map m
JOIN billing_charge_line l ON l.id = m.line_id
WHERE m.insurance_share > 0
  AND NOT EXISTS (
      SELECT 1
      FROM financial_document_items x
      WHERE x.document_id = 18
        AND x.patient_service_product_id = m.psp_id
  );

UPDATE financial_documents
SET total_amount = 282.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 18
  AND encounter_id = 35
  AND document_subtype = 'INSURANCE_CLAIM';

-- 8) Charge header
UPDATE billing_charge
SET gross_amount = 440.0000,
    discount_amount = 87.5000,
    net_amount = 352.5000,
    allocated_amount = 18.0000,
    outstanding_amount = 334.5000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 8
  AND encounter_id = 35
  AND patient_id = 14;

-- =============================================================================
-- Validation — rolls back automatically if any check fails
-- =============================================================================
DO $$
DECLARE
    v_psp_pat numeric;
    v_psp_ins numeric;
    v_psp_net numeric;
    v_pat_fd numeric;
    v_ins_fd numeric;
    v_pat_fd_items int;
    v_ins_fd_items int;
    v_gross numeric;
    v_disc numeric;
    v_net numeric;
    v_alloc numeric;
    v_out numeric;
    v_debit numeric;
    v_active_debit int;
    v_unmapped int;
    v_not_covered int;
    v_cons_pat numeric;
    v_cons_ins numeric;
    v_egfr_pat numeric;
    v_egfr_ins numeric;
    v_wallet_rsv_other int;
BEGIN
    SELECT COALESCE(SUM(patient_share_amount),0),
           COALESCE(SUM(insurance_share_amount),0),
           COALESCE(SUM(net_amount),0)
      INTO v_psp_pat, v_psp_ins, v_psp_net
    FROM patient_services_and_products
    WHERE encounter_id = 35 AND payment_status <> 'CANCELLED';

    SELECT total_amount INTO v_pat_fd FROM financial_documents WHERE id = 17;
    SELECT total_amount INTO v_ins_fd FROM financial_documents WHERE id = 18;

    SELECT COUNT(*) INTO v_pat_fd_items
    FROM financial_document_items WHERE document_id = 17;
    SELECT COUNT(*) INTO v_ins_fd_items
    FROM financial_document_items WHERE document_id = 18;

    SELECT gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount
      INTO v_gross, v_disc, v_net, v_alloc, v_out
    FROM billing_charge WHERE id = 8;

    SELECT current_debit_balance INTO v_debit
    FROM billing_debit_account WHERE patient_id = 14;

    SELECT COUNT(*) INTO v_active_debit
    FROM billing_allocation
    WHERE encounter_id = 35 AND allocation_source_type = 'DEBIT' AND status = 'ACTIVE';

    SELECT COUNT(*) INTO v_unmapped
    FROM patient_services_and_products
    WHERE encounter_id = 35
      AND (waseel_sbs_mapping_id IS NULL OR waseel_sbs_code IS NULL);

    SELECT COUNT(*) INTO v_not_covered
    FROM patient_services_and_products
    WHERE encounter_id = 35 AND coverage_status <> 'COVERED';

    SELECT patient_share_amount, insurance_share_amount
      INTO v_cons_pat, v_cons_ins
    FROM patient_services_and_products WHERE id = 11;

    SELECT patient_share_amount, insurance_share_amount
      INTO v_egfr_pat, v_egfr_ins
    FROM patient_services_and_products WHERE id = 13;

    SELECT COUNT(*) INTO v_wallet_rsv_other
    FROM billing_reservation
    WHERE patient_id = 14
      AND encounter_id <> 35
      AND status IN ('ACTIVE', 'CONSUMED');

    IF v_psp_pat IS DISTINCT FROM 70.5000 OR v_psp_ins IS DISTINCT FROM 282.0000 THEN
        RAISE EXCEPTION 'PSP share mismatch patient=% insurance=%', v_psp_pat, v_psp_ins;
    END IF;
    IF v_psp_net IS DISTINCT FROM 352.5000 THEN
        RAISE EXCEPTION 'PSP net mismatch %', v_psp_net;
    END IF;
    IF v_pat_fd IS DISTINCT FROM 70.5000 OR v_ins_fd IS DISTINCT FROM 282.0000 THEN
        RAISE EXCEPTION 'FD totals mismatch patient=% insurance=%', v_pat_fd, v_ins_fd;
    END IF;
    IF v_pat_fd_items IS DISTINCT FROM 9 OR v_ins_fd_items IS DISTINCT FROM 9 THEN
        RAISE EXCEPTION 'FD item counts patient=% insurance=%', v_pat_fd_items, v_ins_fd_items;
    END IF;
    IF v_gross IS DISTINCT FROM 440.0000
       OR v_disc IS DISTINCT FROM 87.5000
       OR v_net IS DISTINCT FROM 352.5000
       OR v_alloc IS DISTINCT FROM 18.0000
       OR v_out IS DISTINCT FROM 334.5000 THEN
        RAISE EXCEPTION 'Charge totals mismatch gross=% disc=% net=% alloc=% out=%',
            v_gross, v_disc, v_net, v_alloc, v_out;
    END IF;
    IF v_debit IS DISTINCT FROM 0 OR v_active_debit <> 0 THEN
        RAISE EXCEPTION 'Debit not cleared debit=% active_alloc=%', v_debit, v_active_debit;
    END IF;
    IF v_unmapped <> 0 THEN
        RAISE EXCEPTION 'PSP mapping still missing on % rows', v_unmapped;
    END IF;
    IF v_not_covered <> 0 THEN
        RAISE EXCEPTION 'PSP still not COVERED on % rows', v_not_covered;
    END IF;
    IF v_cons_pat IS DISTINCT FROM 18.0000 OR v_cons_ins IS DISTINCT FROM 72.0000 THEN
        RAISE EXCEPTION 'Consultation split wrong pat=% ins=%', v_cons_pat, v_cons_ins;
    END IF;
    IF v_egfr_pat IS DISTINCT FROM 6.0000 OR v_egfr_ins IS DISTINCT FROM 24.0000 THEN
        RAISE EXCEPTION 'eGFR split wrong pat=% ins=%', v_egfr_pat, v_egfr_ins;
    END IF;
    IF v_wallet_rsv_other < 3 THEN
        RAISE EXCEPTION 'Wallet reservations for other encounters were disturbed';
    END IF;
END $$;

-- Post-check selects
SELECT id, coverage_type, patient_insurance_id, billing_status
FROM patient_encounters WHERE id = 35;

SELECT id, waseel_sbs_mapping_id, waseel_sbs_code, coverage_status, price_source,
       unit_price, discount_amount, net_amount,
       patient_share_amount, insurance_share_amount, paid_amount, remaining_amount, payment_status
FROM patient_services_and_products
WHERE encounter_id = 35
ORDER BY created_date, id;

SELECT id, document_subtype, total_amount, status, claim_reference
FROM financial_documents WHERE encounter_id = 35 ORDER BY id;

SELECT SUM(patient_share_amount) AS sum_pat, SUM(insurance_share_amount) AS sum_ins, SUM(net_amount) AS sum_net
FROM patient_services_and_products
WHERE encounter_id = 35 AND payment_status <> 'CANCELLED';

COMMIT;
