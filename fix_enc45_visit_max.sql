-- =============================================================================
-- Patient 22 / Encounter 45 (E00144) — REVIEW ONLY, do not auto-apply
-- Database: DBOneHealthLocaly
--
-- Eligibility: insurance 16 Tawuniya, Active / eligible / inforce
--   request 141 SUCCESS, response 345276567, snapshot 23
--   copay 20%, visit max_limit = 100  ← NOT applied today
--
-- Mapping: ALL 13 items have active SBS mapping AND Tawuniya PL items.
--   Billing ignored that and marked 12 labs NOT_COVERED / DEFAULT cash
--   (not_covered_reason = NOT_IN_INSURANCE_PRICE_LIST). Visit max is skipped
--   for NOT_COVERED cash items, so patient share became 1203 instead of 100.
--
-- After fix (chronological visit pool, 20% then cap 100):
--   Patient 100.00 | Insurance 758.75 | Net 858.75 | Gross 1115.00 | Disc 256.25
--   Wallet: release 78 from payment 16 (consumed 178 → 100, available 0 → 78)
--   Reverse all debit (1025). Re-allocate 61 of payment 16 onto remaining copays.
--
-- Financial documents:
--   PATIENT INV 43: 1203 → 100 (drop 5 fully-insured lab lines)
--   INSURANCE_CLAIM INV 44: 72 → 758.75 (add 12 lab lines; fix cons paid 18→0)
--
-- Claim reference on INV 44: CLM-E00144-1788456165826
--   If already submitted to Waseel, resubmit / replace after this fix.
--
-- REVIEW THEN RUN. Commits only if the validation block passes.
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS tmp_enc45_map;
CREATE TEMP TABLE tmp_enc45_map AS
SELECT *
FROM (VALUES
-- psp, line, snap, resp_pat, fd_pat, catalog, pl_item, map_id, unit, disc, net, pat, ins, sbs, item_type, realloc_pay16
-- visit order = PSP created_date. Cap hits on Vitamin D (remaining 13).
(43, 41, 41, 46, 116, 29272, 2684, 4404,  90.0000,  0.0000,  90.0000, 18.0000,  72.0000, 'Cons003',     'SERVICE',    FALSE),
(45, 43, 43, 49, 117,   165,  554, 3532, 140.0000, 35.0000, 105.0000, 21.0000,  84.0000, '73050-35-50', 'LABORATORY', FALSE),
(46, 45, 45, 51, 119,   139,  688, 3544,  45.0000, 11.2500,  33.7500,  6.7500,  27.0000, '73100-00-10', 'LABORATORY', TRUE),
(47, 44, 44, 50, 118,   147,  569, 3537,  45.0000, 11.2500,  33.7500,  6.7500,  27.0000, '73050-37-30', 'LABORATORY', TRUE),
(48, 46, 46, 52, 120,   144,  423, 3523,  20.0000,  5.0000,  15.0000,  3.0000,  12.0000, '73050-15-50', 'LABORATORY', TRUE),
(49, 48, 48, 54, 122,   176,  405, 3522, 160.0000, 40.0000, 120.0000, 24.0000,  96.0000, '73050-13-10', 'LABORATORY', TRUE),
(50, 47, 47, 53, 121,   152,  476, 3525,  50.0000, 12.5000,  37.5000,  7.5000,  30.0000, '73050-23-60', 'LABORATORY', TRUE),
(51, 49, 49, 55, 123,   175,  350, 3519, 205.0000, 51.2500, 153.7500, 13.0000, 140.7500, '73050-05-30', 'LABORATORY', TRUE),
(52, 50, 50, 56, 124,   150,  308, 3514, 160.0000, 40.0000, 120.0000,  0.0000, 120.0000, '73000-00-60', 'LABORATORY', FALSE),
(53, 51, 51, 57, 125,   153,  560, 3535,  45.0000, 11.2500,  33.7500,  0.0000,  33.7500, '73050-36-20', 'LABORATORY', FALSE),
(54, 52, 52, 58, 126,   138,  691, 3496,  75.0000, 18.7500,  56.2500,  0.0000,  56.2500, '73100-00-80', 'LABORATORY', FALSE),
(55, 53, 53, 59, 127,   151,  478, 3526,  50.0000, 12.5000,  37.5000,  0.0000,  37.5000, '73050-23-80', 'LABORATORY', FALSE),
(56, 54, 54, 60, 128,   146,  382, 3521,  30.0000,  7.5000,  22.5000,  0.0000,  22.5000, '73050-09-80', 'LABORATORY', FALSE)
) AS t(
    psp_id, line_id, snap_id, resp_pat_id, fd_pat_id, catalog_id, pl_item_id, map_id,
    unit_price, discount_amount, net_amount, patient_share, insurance_share,
    sbs_code, item_type, realloc_pay16
);

-- 1) Reverse every debit allocation on this visit
UPDATE billing_allocation a
SET remaining_allocated_amount = 0,
    reversed_amount = a.allocated_amount,
    status = 'REVERSED',
    reversed_date = NOW(),
    reversed_by = 'manual-billing-fix',
    reversal_reason = 'Encounter 45 labs moved to Tawuniya PL; visit max-limit 100; debit no longer required',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE a.encounter_id = 45
  AND a.allocation_source_type = 'DEBIT'
  AND a.status = 'ACTIVE';

UPDATE billing_debit_transaction
SET status = 'REVERSED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE encounter_id = 45
  AND status = 'COMPLETED';

UPDATE billing_debit_account
SET current_debit_balance = 0,
    total_debit_created = 0,
    total_debit_settled = 0,
    available_credit = credit_limit,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 14
  AND patient_id = 22;

-- 2) Shrink TSH payment 16 allocation 160 → 21 (release 139; 61 re-reserved below, 78 to wallet)
UPDATE billing_allocation
SET allocated_amount = 21.0000,
    remaining_allocated_amount = 21.0000,
    reversed_amount = 0,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 154
  AND encounter_id = 45
  AND status = 'ACTIVE';

UPDATE billing_reservation
SET consumed_amount = 21.0000,
    released_amount = 139.0000,
    remaining_reserved_amount = 0,
    status = 'PARTIALLY_RELEASED',
    released_date = NOW(),
    released_by = 'manual-billing-fix',
    release_reason = 'AMOUNT_REDUCED',
    release_notes = 'TSH copay 160 cash → 21 after Tawuniya PL + visit max 100; remainder moved to other copays / wallet',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 16
  AND encounter_id = 45;

-- 3) PSP: mapping + Tawuniya prices + visit-max shares
UPDATE patient_services_and_products psp
SET unit_price = m.unit_price,
    discount_amount = m.discount_amount,
    total_amount = m.net_amount,
    gross_amount = m.unit_price,
    net_amount = m.net_amount,
    patient_share_amount = m.patient_share,
    insurance_share_amount = m.insurance_share,
    paid_amount = m.patient_share,
    remaining_amount = 0,
    payment_status = 'PAID',
    coverage_status = 'COVERED',
    not_covered_reason = NULL,
    price_source = 'PRICE_LIST',
    patient_insurance_id = 16,
    waseel_sbs_mapping_id = m.map_id,
    waseel_sbs_code = m.sbs_code,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc45_map m
WHERE psp.id = m.psp_id
  AND psp.encounter_id = 45
  AND psp.patient_id = 22;

-- 4) Charge lines
UPDATE billing_charge_line l
SET item_code = m.sbs_code,
    unit_price = m.unit_price,
    gross_amount = m.unit_price,
    discount_amount = m.discount_amount,
    net_amount = m.net_amount,
    patient_responsibility_amount = m.patient_share,
    insurance_responsibility_amount = m.insurance_share,
    allocated_amount = m.patient_share,
    outstanding_amount = m.insurance_share,
    reserved_amount = 0,
    status = CASE
               WHEN m.patient_share > 0 AND m.insurance_share > 0 THEN 'PARTIALLY_ALLOCATED'
               WHEN m.patient_share > 0 THEN 'ALLOCATED'
               ELSE 'OPEN'
             END,
    patient_insurance_id = 16,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc45_map m
WHERE l.id = m.line_id
  AND l.encounter_id = 45;

-- 5) Pricing snapshots
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
        'visitMaxLimit', 100
    ),
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc45_map m
WHERE s.id = m.snap_id
  AND s.encounter_id = 45;

-- 6) Patient responsibilities
UPDATE billing_charge_responsibility r
SET responsibility_amount = m.patient_share,
    allocated_amount = m.patient_share,
    outstanding_amount = 0,
    coverage_percentage = CASE WHEN m.patient_share > 0 THEN 20.000000 ELSE 0 END,
    copay_amount = m.patient_share,
    non_covered_amount = 0,
    patient_insurance_id = 16,
    policy_number = '52332912',
    member_number = '002327279291001',
    status = CASE
               WHEN m.patient_share > 0 THEN 'FULLY_ALLOCATED'
               ELSE 'CALCULATED'
             END,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc45_map m
WHERE r.id = m.resp_pat_id
  AND r.encounter_id = 45;

-- Existing insurance resp (consultation 47)
UPDATE billing_charge_responsibility r
SET responsibility_amount = m.insurance_share,
    allocated_amount = 0,
    outstanding_amount = m.insurance_share,
    coverage_percentage = 80.000000,
    copay_amount = 0,
    non_covered_amount = 0,
    patient_insurance_id = 16,
    policy_number = '52332912',
    member_number = '002327279291001',
    status = 'CALCULATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc45_map m
WHERE r.charge_line_id = m.line_id
  AND r.encounter_id = 45
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
    22,
    45,
    'INSURANCE',
    'PRIMARY',
    16,
    '52332912',
    '002327279291001',
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
    'FIX45:RESPONSIBILITY:INSURANCE:PSP:' || m.psp_id,
    'manual-billing-fix',
    NOW(),
    'manual-billing-fix',
    NOW()
FROM tmp_enc45_map m
JOIN billing_charge_line l
  ON l.id = m.line_id
 AND l.encounter_id = 45
WHERE m.insurance_share > 0
  AND NOT EXISTS (
      SELECT 1
      FROM billing_charge_responsibility x
      WHERE x.charge_line_id = m.line_id
        AND x.responsible_party_type = 'INSURANCE'
  );

-- 7) Re-apply 61 of payment 16 onto remaining copays (WBC/BUN/FBS/Ferritin/HDL/VitD)
WITH src AS (
    SELECT rsv.wallet_id,
           rsv.payment_id,
           rsv.payment_transaction_id,
           rsv.transaction_group_id
    FROM billing_reservation rsv
    WHERE rsv.id = 16
      AND rsv.encounter_id = 45
),
new_rsv AS (
    INSERT INTO billing_reservation (
        reservation_number, wallet_id, payment_id, payment_transaction_id,
        patient_id, encounter_id, charge_id, charge_line_id, charge_responsibility_id,
        patient_service_product_id,
        original_reserved_amount, remaining_reserved_amount, consumed_amount, released_amount,
        currency, status, reserved_date, consumed_date,
        idempotency_key, transaction_group_id,
        created_by, created_date, last_modified_by, last_modified_date
    )
    SELECT
        'RSV-FIX45-P' || m.psp_id,
        src.wallet_id,
        src.payment_id,
        src.payment_transaction_id,
        22,
        45,
        14,
        m.line_id,
        m.resp_pat_id,
        m.psp_id,
        m.patient_share,
        0,
        m.patient_share,
        0,
        'SAR',
        'CONSUMED',
        NOW(),
        NOW(),
        'FIX45:RESERVATION:PSP:' || m.psp_id || ':PAYMENT:16',
        src.transaction_group_id,
        'manual-billing-fix',
        NOW(),
        'manual-billing-fix',
        NOW()
    FROM tmp_enc45_map m
    CROSS JOIN src
    WHERE m.realloc_pay16
      AND m.patient_share > 0
    RETURNING id, patient_service_product_id, reservation_number, charge_line_id, charge_responsibility_id
)
INSERT INTO billing_allocation (
    allocation_number, charge_id, charge_line_id, charge_responsibility_id,
    patient_service_product_id, patient_id, encounter_id,
    allocation_source_type, reservation_id, payment_id, payment_transaction_id,
    source_reference_type, source_reference_id, source_reference_number,
    allocated_amount, remaining_allocated_amount, reversed_amount,
    currency, status, allocation_date,
    idempotency_key, transaction_group_id,
    created_by, created_date, last_modified_by, last_modified_date
)
SELECT
    'ALC-FIX45-P' || r.patient_service_product_id,
    14,
    r.charge_line_id,
    r.charge_responsibility_id,
    r.patient_service_product_id,
    22,
    45,
    'RESERVATION',
    r.id,
    src.payment_id,
    src.payment_transaction_id,
    'BILLING_RESERVATION',
    r.id,
    r.reservation_number,
    m.patient_share,
    m.patient_share,
    0,
    'SAR',
    'ACTIVE',
    NOW(),
    'FIX45:ALLOCATION:PSP:' || r.patient_service_product_id || ':PAYMENT:16',
    src.transaction_group_id,
    'manual-billing-fix',
    NOW(),
    'manual-billing-fix',
    NOW()
FROM new_rsv r
JOIN tmp_enc45_map m ON m.psp_id = r.patient_service_product_id
CROSS JOIN src;

-- 8) Wallet: payment 16 leftover 78 (160 - 21 TSH - 61 other copays)
UPDATE billing_wallet
SET available_balance = available_balance + 78.0000,
    consumed_amount = consumed_amount - 78.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 7
  AND patient_id = 22;

-- 9) PATIENT financial document 43
DELETE FROM financial_document_items f
USING tmp_enc45_map m
WHERE f.id = m.fd_pat_id
  AND f.document_id = 43
  AND m.patient_share = 0;

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
    paid_amount = m.patient_share,
    remaining_amount = 0,
    insurance_paid_amount = 0,
    insurance_remaining_amount = 0,
    item_code = m.sbs_code,
    status = 'PAID',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc45_map m
WHERE f.id = m.fd_pat_id
  AND f.document_id = 43
  AND m.patient_share > 0;

UPDATE financial_documents
SET total_amount = 100.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 43
  AND encounter_id = 45
  AND document_subtype = 'PATIENT';

-- 10) INSURANCE_CLAIM financial document 44
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
FROM tmp_enc45_map m
JOIN financial_document_items x
  ON x.document_id = 44
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
    44,
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
FROM tmp_enc45_map m
JOIN billing_charge_line l ON l.id = m.line_id
WHERE m.insurance_share > 0
  AND NOT EXISTS (
      SELECT 1
      FROM financial_document_items x
      WHERE x.document_id = 44
        AND x.patient_service_product_id = m.psp_id
  );

UPDATE financial_documents
SET total_amount = 758.7500,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 44
  AND encounter_id = 45
  AND document_subtype = 'INSURANCE_CLAIM';

-- 11) Charge header
UPDATE billing_charge
SET gross_amount = 1115.0000,
    discount_amount = 256.2500,
    net_amount = 858.7500,
    allocated_amount = 100.0000,
    outstanding_amount = 758.7500,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 14
  AND encounter_id = 45
  AND patient_id = 22;

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
    v_wallet_avail numeric;
    v_wallet_cons numeric;
    v_unmapped int;
    v_not_covered int;
    v_vitd_pat numeric;
    v_vitd_ins numeric;
BEGIN
    SELECT COALESCE(SUM(patient_share_amount),0),
           COALESCE(SUM(insurance_share_amount),0),
           COALESCE(SUM(net_amount),0)
      INTO v_psp_pat, v_psp_ins, v_psp_net
    FROM patient_services_and_products
    WHERE encounter_id = 45 AND payment_status <> 'CANCELLED';

    SELECT total_amount INTO v_pat_fd FROM financial_documents WHERE id = 43;
    SELECT total_amount INTO v_ins_fd FROM financial_documents WHERE id = 44;

    SELECT COUNT(*) INTO v_pat_fd_items
    FROM financial_document_items WHERE document_id = 43;
    SELECT COUNT(*) INTO v_ins_fd_items
    FROM financial_document_items WHERE document_id = 44;

    SELECT gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount
      INTO v_gross, v_disc, v_net, v_alloc, v_out
    FROM billing_charge WHERE id = 14;

    SELECT current_debit_balance INTO v_debit
    FROM billing_debit_account WHERE patient_id = 22;

    SELECT COUNT(*) INTO v_active_debit
    FROM billing_allocation
    WHERE encounter_id = 45 AND allocation_source_type = 'DEBIT' AND status = 'ACTIVE';

    SELECT available_balance, consumed_amount INTO v_wallet_avail, v_wallet_cons
    FROM billing_wallet WHERE id = 7;

    SELECT COUNT(*) INTO v_unmapped
    FROM patient_services_and_products
    WHERE encounter_id = 45
      AND (waseel_sbs_mapping_id IS NULL OR waseel_sbs_code IS NULL);

    SELECT COUNT(*) INTO v_not_covered
    FROM patient_services_and_products
    WHERE encounter_id = 45 AND coverage_status <> 'COVERED';

    SELECT patient_share_amount, insurance_share_amount
      INTO v_vitd_pat, v_vitd_ins
    FROM patient_services_and_products WHERE id = 51;

    IF v_psp_pat IS DISTINCT FROM 100.0000 OR v_psp_ins IS DISTINCT FROM 758.7500 THEN
        RAISE EXCEPTION 'PSP share mismatch patient=% insurance=%', v_psp_pat, v_psp_ins;
    END IF;
    IF v_psp_net IS DISTINCT FROM 858.7500 THEN
        RAISE EXCEPTION 'PSP net mismatch %', v_psp_net;
    END IF;
    IF v_pat_fd IS DISTINCT FROM 100.0000 OR v_ins_fd IS DISTINCT FROM 758.7500 THEN
        RAISE EXCEPTION 'FD totals mismatch patient=% insurance=%', v_pat_fd, v_ins_fd;
    END IF;
    IF v_pat_fd_items IS DISTINCT FROM 8 OR v_ins_fd_items IS DISTINCT FROM 13 THEN
        RAISE EXCEPTION 'FD item counts patient=% insurance=%', v_pat_fd_items, v_ins_fd_items;
    END IF;
    IF v_gross IS DISTINCT FROM 1115.0000
       OR v_disc IS DISTINCT FROM 256.2500
       OR v_net IS DISTINCT FROM 858.7500
       OR v_alloc IS DISTINCT FROM 100.0000
       OR v_out IS DISTINCT FROM 758.7500 THEN
        RAISE EXCEPTION 'Charge totals mismatch gross=% disc=% net=% alloc=% out=%',
            v_gross, v_disc, v_net, v_alloc, v_out;
    END IF;
    IF v_debit IS DISTINCT FROM 0 OR v_active_debit <> 0 THEN
        RAISE EXCEPTION 'Debit not cleared debit=% active_alloc=%', v_debit, v_active_debit;
    END IF;
    IF v_wallet_cons IS DISTINCT FROM 100.0000 OR v_wallet_avail IS DISTINCT FROM 78.0000 THEN
        RAISE EXCEPTION 'Wallet expected consumed=100 available=78 got cons=% avail=%',
            v_wallet_cons, v_wallet_avail;
    END IF;
    IF v_unmapped <> 0 THEN
        RAISE EXCEPTION 'PSP mapping still missing on % rows', v_unmapped;
    END IF;
    IF v_not_covered <> 0 THEN
        RAISE EXCEPTION 'PSP still not COVERED on % rows', v_not_covered;
    END IF;
    IF v_vitd_pat IS DISTINCT FROM 13.0000 OR v_vitd_ins IS DISTINCT FROM 140.7500 THEN
        RAISE EXCEPTION 'Vitamin D visit-max split wrong pat=% ins=%', v_vitd_pat, v_vitd_ins;
    END IF;
END $$;

-- Post-check selects
SELECT id, coverage_type, patient_insurance_id, billing_status
FROM patient_encounters WHERE id = 45;

SELECT id, waseel_sbs_mapping_id, waseel_sbs_code, coverage_status, price_source,
       unit_price, discount_amount, net_amount,
       patient_share_amount, insurance_share_amount, payment_status
FROM patient_services_and_products
WHERE encounter_id = 45
ORDER BY id;

SELECT id, document_subtype, total_amount, eligibility_reference, claim_reference
FROM financial_documents WHERE encounter_id = 45 ORDER BY id;

SELECT id, gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount
FROM billing_charge WHERE id = 14;

SELECT current_debit_balance, total_debit_created, available_credit
FROM billing_debit_account WHERE patient_id = 22;

SELECT available_balance, consumed_amount, credited_amount
FROM billing_wallet WHERE id = 7;

COMMIT;
