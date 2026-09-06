-- =============================================================================
-- Patient 14 / Encounter 75 (E00174) — REVIEW ONLY, do not auto-apply
-- Database: DBOneHealthLocaly
--
-- Patient: ALI TALAL ALAAEDDINE (P113), insurance 12 Tawuniya
-- Eligibility: Active / eligible / inforce (request 92, response 343293082)
--   No billing_eligibility_snapshot yet — visit is still billing_status OPEN
--   copay 20%, visit max_limit = 100
--
-- Mapping: ALL 10 active items have SBS mapping AND Tawuniya PL (PL 1).
--   Billing marked 9 labs NOT_COVERED / DEFAULT cash
--   (not_covered_reason = NOT_IN_INSURANCE_PRICE_LIST). 13 cancelled dupes
--   left untouched.
--
-- Visit pool (charge-line id order; cons already reserved 18):
--   Cap hits LDL (remaining 0.25). Patient total = 100.00
--
-- After fix (Tawuniya PL 25% disc, 20% then visit max 100):
--   Gross 685.00 | Disc 148.75 | Net 536.25 | Patient 100.00 | Insurance 436.25
--   Payment 22 / reservation 22 stays ACTIVE 18 on consultation.
--   No debit. No financial documents yet (not invoiced).
--   Do NOT touch wallet (shared with encounters 61/74).
--
-- REVIEW THEN RUN. Commits only if the validation block passes.
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS tmp_enc75_map;
CREATE TEMP TABLE tmp_enc75_map AS
SELECT *
FROM (VALUES
-- psp, line, snap, resp_pat, catalog, pl_item, map_id, unit, disc, net, pat, ins, sbs, item_type, keep_reserved
(174, 172, 172, 186, 29272, 2684, 4404,  90.0000,  0.0000,  90.0000, 18.0000, 72.0000, 'Cons003',     'SERVICE',    TRUE),
(176, 173, 173, 188,   150,  308, 3514, 160.0000, 40.0000, 120.0000, 24.0000, 96.0000, '73000-00-60', 'LABORATORY', FALSE),
(175, 174, 174, 189,   152,  476, 3525,  50.0000, 12.5000,  37.5000,  7.5000, 30.0000, '73050-23-60', 'LABORATORY', FALSE),
(178, 175, 175, 190,   147,  569, 3537,  45.0000, 11.2500,  33.7500,  6.7500, 27.0000, '73050-37-30', 'LABORATORY', FALSE),
(177, 176, 176, 191,   138,  691, 3496,  75.0000, 18.7500,  56.2500, 11.2500, 45.0000, '73100-00-80', 'LABORATORY', FALSE),
(179, 177, 177, 192,   146,  382, 3521,  30.0000,  7.5000,  22.5000,  4.5000, 18.0000, '73050-09-80', 'LABORATORY', FALSE),
(180, 178, 178, 193,   153,  560, 3535,  45.0000, 11.2500,  33.7500,  6.7500, 27.0000, '73050-36-20', 'LABORATORY', FALSE),
(181, 179, 179, 194,   144,  423, 3523,  20.0000,  5.0000,  15.0000,  3.0000, 12.0000, '73050-15-50', 'LABORATORY', FALSE),
(182, 180, 180, 195,   145,  445, 3524, 120.0000, 30.0000,  90.0000, 18.0000, 72.0000, '73050-18-50', 'LABORATORY', FALSE),
(183, 181, 181, 196,   151,  478, 3526,  50.0000, 12.5000,  37.5000,  0.2500, 37.2500, '73050-23-80', 'LABORATORY', FALSE)
) AS t(
    psp_id, line_id, snap_id, resp_pat_id, catalog_id, pl_item_id, map_id,
    unit_price, discount_amount, net_amount, patient_share, insurance_share,
    sbs_code, item_type, keep_reserved
);

-- 1) PSP: mapping + Tawuniya prices + 20% copay + visit-max shares
UPDATE patient_services_and_products psp
SET unit_price = m.unit_price,
    discount_amount = m.discount_amount,
    total_amount = m.net_amount,
    gross_amount = m.unit_price,
    net_amount = m.net_amount,
    patient_share_amount = m.patient_share,
    insurance_share_amount = m.insurance_share,
    paid_amount = CASE WHEN m.keep_reserved THEN m.patient_share ELSE 0 END,
    remaining_amount = CASE WHEN m.keep_reserved THEN 0 ELSE m.patient_share END,
    payment_status = 'PENDING',
    coverage_status = 'COVERED',
    not_covered_reason = NULL,
    price_source = 'PRICE_LIST',
    patient_insurance_id = 12,
    waseel_sbs_mapping_id = m.map_id,
    waseel_sbs_code = m.sbs_code,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc75_map m
WHERE psp.id = m.psp_id
  AND psp.encounter_id = 75
  AND psp.patient_id = 14
  AND psp.payment_status <> 'CANCELLED';

-- 2) Charge lines
UPDATE billing_charge_line l
SET item_code = m.sbs_code,
    unit_price = m.unit_price,
    gross_amount = m.unit_price,
    discount_amount = m.discount_amount,
    net_amount = m.net_amount,
    patient_responsibility_amount = m.patient_share,
    insurance_responsibility_amount = m.insurance_share,
    allocated_amount = 0,
    outstanding_amount = m.net_amount,
    reserved_amount = CASE WHEN m.keep_reserved THEN m.patient_share ELSE 0 END,
    status = CASE WHEN m.keep_reserved THEN 'RESERVED' ELSE 'OPEN' END,
    patient_insurance_id = 12,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc75_map m
WHERE l.id = m.line_id
  AND l.encounter_id = 75
  AND l.status <> 'CANCELLED';

-- 3) Pricing snapshots
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
FROM tmp_enc75_map m
WHERE s.id = m.snap_id
  AND s.encounter_id = 75
  AND s.status <> 'CANCELLED';

-- 4) Patient responsibilities
UPDATE billing_charge_responsibility r
SET responsibility_amount = m.patient_share,
    allocated_amount = 0,
    outstanding_amount = m.patient_share,
    coverage_percentage = CASE WHEN m.patient_share > 0 THEN 20.000000 ELSE 0 END,
    copay_amount = m.patient_share,
    non_covered_amount = 0,
    patient_insurance_id = 12,
    policy_number = '52332912',
    member_number = '002421981909001',
    status = 'CALCULATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc75_map m
WHERE r.id = m.resp_pat_id
  AND r.encounter_id = 75;

-- Existing insurance resp (consultation 187)
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
FROM tmp_enc75_map m
WHERE r.charge_line_id = m.line_id
  AND r.encounter_id = 75
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
    75,
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
    'FIX75:RESPONSIBILITY:INSURANCE:PSP:' || m.psp_id,
    'manual-billing-fix',
    NOW(),
    'manual-billing-fix',
    NOW()
FROM tmp_enc75_map m
JOIN billing_charge_line l
  ON l.id = m.line_id
 AND l.encounter_id = 75
WHERE m.insurance_share > 0
  AND NOT EXISTS (
      SELECT 1
      FROM billing_charge_responsibility x
      WHERE x.charge_line_id = m.line_id
        AND x.responsible_party_type = 'INSURANCE'
  );

-- 5) Charge header (still OPEN, not invoiced)
UPDATE billing_charge
SET gross_amount = 685.0000,
    discount_amount = 148.7500,
    net_amount = 536.2500,
    allocated_amount = 0,
    outstanding_amount = 536.2500,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 35
  AND encounter_id = 75
  AND patient_id = 14;

-- =============================================================================
-- Validation — rolls back automatically if any check fails
-- =============================================================================
DO $$
DECLARE
    v_psp_pat numeric;
    v_psp_ins numeric;
    v_psp_net numeric;
    v_fd int;
    v_gross numeric;
    v_disc numeric;
    v_net numeric;
    v_alloc numeric;
    v_out numeric;
    v_unmapped int;
    v_not_covered int;
    v_cons_pat numeric;
    v_cons_ins numeric;
    v_ldl_pat numeric;
    v_ldl_ins numeric;
    v_rsv_amt numeric;
    v_rsv_status text;
    v_cancelled int;
    v_active_debit int;
BEGIN
    SELECT COALESCE(SUM(patient_share_amount),0),
           COALESCE(SUM(insurance_share_amount),0),
           COALESCE(SUM(net_amount),0)
      INTO v_psp_pat, v_psp_ins, v_psp_net
    FROM patient_services_and_products
    WHERE encounter_id = 75 AND payment_status <> 'CANCELLED';

    SELECT COUNT(*) INTO v_fd FROM financial_documents WHERE encounter_id = 75;

    SELECT gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount
      INTO v_gross, v_disc, v_net, v_alloc, v_out
    FROM billing_charge WHERE id = 35;

    SELECT COUNT(*) INTO v_unmapped
    FROM patient_services_and_products
    WHERE encounter_id = 75
      AND payment_status <> 'CANCELLED'
      AND (waseel_sbs_mapping_id IS NULL OR waseel_sbs_code IS NULL);

    SELECT COUNT(*) INTO v_not_covered
    FROM patient_services_and_products
    WHERE encounter_id = 75
      AND payment_status <> 'CANCELLED'
      AND coverage_status <> 'COVERED';

    SELECT patient_share_amount, insurance_share_amount
      INTO v_cons_pat, v_cons_ins
    FROM patient_services_and_products WHERE id = 174;

    SELECT patient_share_amount, insurance_share_amount
      INTO v_ldl_pat, v_ldl_ins
    FROM patient_services_and_products WHERE id = 183;

    SELECT remaining_reserved_amount, status
      INTO v_rsv_amt, v_rsv_status
    FROM billing_reservation WHERE id = 22 AND encounter_id = 75;

    SELECT COUNT(*) INTO v_cancelled
    FROM patient_services_and_products
    WHERE encounter_id = 75 AND payment_status = 'CANCELLED';

    SELECT COUNT(*) INTO v_active_debit
    FROM billing_allocation
    WHERE encounter_id = 75 AND allocation_source_type = 'DEBIT' AND status = 'ACTIVE';

    IF v_psp_pat IS DISTINCT FROM 100.0000 OR v_psp_ins IS DISTINCT FROM 436.2500 THEN
        RAISE EXCEPTION 'PSP share mismatch patient=% insurance=%', v_psp_pat, v_psp_ins;
    END IF;
    IF v_psp_net IS DISTINCT FROM 536.2500 THEN
        RAISE EXCEPTION 'PSP net mismatch %', v_psp_net;
    END IF;
    IF v_fd <> 0 THEN
        RAISE EXCEPTION 'Unexpected financial documents on OPEN visit: %', v_fd;
    END IF;
    IF v_gross IS DISTINCT FROM 685.0000
       OR v_disc IS DISTINCT FROM 148.7500
       OR v_net IS DISTINCT FROM 536.2500
       OR v_alloc IS DISTINCT FROM 0
       OR v_out IS DISTINCT FROM 536.2500 THEN
        RAISE EXCEPTION 'Charge totals mismatch gross=% disc=% net=% alloc=% out=%',
            v_gross, v_disc, v_net, v_alloc, v_out;
    END IF;
    IF v_unmapped <> 0 THEN
        RAISE EXCEPTION 'PSP mapping still missing on % rows', v_unmapped;
    END IF;
    IF v_not_covered <> 0 THEN
        RAISE EXCEPTION 'PSP still not COVERED on % active rows', v_not_covered;
    END IF;
    IF v_cons_pat IS DISTINCT FROM 18.0000 OR v_cons_ins IS DISTINCT FROM 72.0000 THEN
        RAISE EXCEPTION 'Consultation split wrong pat=% ins=%', v_cons_pat, v_cons_ins;
    END IF;
    IF v_ldl_pat IS DISTINCT FROM 0.2500 OR v_ldl_ins IS DISTINCT FROM 37.2500 THEN
        RAISE EXCEPTION 'LDL visit-max split wrong pat=% ins=%', v_ldl_pat, v_ldl_ins;
    END IF;
    IF v_rsv_status IS DISTINCT FROM 'ACTIVE' OR v_rsv_amt IS DISTINCT FROM 18.0000 THEN
        RAISE EXCEPTION 'Consultation reservation disturbed status=% remaining=%',
            v_rsv_status, v_rsv_amt;
    END IF;
    IF v_cancelled IS DISTINCT FROM 13 THEN
        RAISE EXCEPTION 'Cancelled duplicate count changed: %', v_cancelled;
    END IF;
    IF v_active_debit <> 0 THEN
        RAISE EXCEPTION 'Unexpected debit allocations: %', v_active_debit;
    END IF;
END $$;

SELECT id, coverage_type, patient_insurance_id, billing_status
FROM patient_encounters WHERE id = 75;

SELECT id, waseel_sbs_mapping_id, waseel_sbs_code, coverage_status, price_source,
       unit_price, discount_amount, net_amount,
       patient_share_amount, insurance_share_amount, paid_amount, remaining_amount, payment_status
FROM patient_services_and_products
WHERE encounter_id = 75 AND payment_status <> 'CANCELLED'
ORDER BY id;

SELECT SUM(patient_share_amount) AS sum_pat, SUM(insurance_share_amount) AS sum_ins, SUM(net_amount) AS sum_net
FROM patient_services_and_products
WHERE encounter_id = 75 AND payment_status <> 'CANCELLED';

COMMIT;
