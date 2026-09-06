-- =============================================================================
-- Patient 36 / Encounter 76 (E00175) — FULL billing + claim, one shot
-- Database: DBOneHealthLocaly
--
-- الحسبة:
--   Visit = INSURANCE from 27 Aug, Tawuniya 21, copay 20%, visit max-limit 100
--   Cancel 27 Aug duplicates: TSH 205, Ferritin 206, Vitamin D 207 (keep 30 Aug)
--   Cover remaining 27 Aug labs + all 30 Aug labs from Tawuniya PL 25%
--   Cons003 on consultation is kept (not 83600-00-10)
--   Patient 100.00 | Insurance 860.00 | Net 960.00 | Gross 1250 | Disc 290
--
-- Visit-max pool (chronological):
--   27 Aug copays 18+11.25+18+4.50+3+4.50+6.75+7.50+7.50+4.50 = 85.50
--   30 Aug B12 remaining 14.50 (20% would be 30.75) then TSH/VitD/Ferritin = 0
--
-- الكلِيم:
--   Rebuild claim 15 from INV 48 (14 lines), both claims FAILED
--   After COMMIT: resubmit from Claims on invoice 48
-- =============================================================================

BEGIN;

UPDATE patient_encounters
SET coverage_type = 'INSURANCE',
    patient_insurance_id = 21,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 76
  AND patient_id = 36;

DROP TABLE IF EXISTS tmp_enc76_map;
CREATE TEMP TABLE tmp_enc76_map AS
SELECT *
FROM (VALUES
-- psp, line, snap, resp_pat, fd_pat, catalog, pl, map, unit, disc, net, pat, ins, sbs, type, cancel
(197, 195, 195, 210, 142, 29272, 2684, 4404,  90.0000,  0.0000,  90.0000, 18.0000,  72.0000, 'Cons003',     'SERVICE',    FALSE),
(198, 196, 196, 212, 143,   138,  691, 3496,  75.0000, 18.7500,  56.2500, 11.2500,  45.0000, '73100-00-80', 'LABORATORY', FALSE),
(199, 197, 197, 213, 144,   145,  445, 3524, 120.0000, 30.0000,  90.0000, 18.0000,  72.0000, '73050-18-50', 'LABORATORY', FALSE),
(200, 198, 198, 214, 145,   146,  382, 3521,  30.0000,  7.5000,  22.5000,  4.5000,  18.0000, '73050-09-80', 'LABORATORY', FALSE),
(201, 199, 199, 215, 146,   144,  423, 3523,  20.0000,  5.0000,  15.0000,  3.0000,  12.0000, '73050-15-50', 'LABORATORY', FALSE),
(202, 201, 201, 217, 148,   154,  558, 3534,  30.0000,  7.5000,  22.5000,  4.5000,  18.0000, '73050-36-00', 'LABORATORY', FALSE),
(203, 200, 200, 216, 147,   153,  560, 3535,  45.0000, 11.2500,  33.7500,  6.7500,  27.0000, '73050-36-20', 'LABORATORY', FALSE),
(204, 202, 202, 218, 149,   152,  476, 3525,  50.0000, 12.5000,  37.5000,  7.5000,  30.0000, '73050-23-60', 'LABORATORY', FALSE),
(205, 204, 204, 220, 151,   165, NULL, NULL,   0.0000,  0.0000,   0.0000,  0.0000,   0.0000, NULL,          'LABORATORY', TRUE),
(206, 203, 203, 219, 150,   176, NULL, NULL,   0.0000,  0.0000,   0.0000,  0.0000,   0.0000, NULL,          'LABORATORY', TRUE),
(207, 205, 205, 221, 152,   175, NULL, NULL,   0.0000,  0.0000,   0.0000,  0.0000,   0.0000, NULL,          'LABORATORY', TRUE),
(208, 206, 206, 222, 153,   151,  478, 3526,  50.0000, 12.5000,  37.5000,  7.5000,  30.0000, '73050-23-80', 'LABORATORY', FALSE),
(209, 207, 207, 223, 154,   155,  557, 3533,  30.0000,  7.5000,  22.5000,  4.5000,  18.0000, '73050-35-90', 'LABORATORY', FALSE),
(290, 288, 288, 354, 155,   363,  386, 3631, 205.0000, 51.2500, 153.7500, 14.5000, 139.2500, '73050-10-40', 'LABORATORY', FALSE),
(291, 289, 289, 356, 156,   165,  554, 3532, 140.0000, 35.0000, 105.0000,  0.0000, 105.0000, '73050-35-50', 'LABORATORY', FALSE),
(292, 290, 290, 358, 157,   175,  350, 3519, 205.0000, 51.2500, 153.7500,  0.0000, 153.7500, '73050-05-30', 'LABORATORY', FALSE),
(293, 291, 291, 360, 158,   176,  405, 3522, 160.0000, 40.0000, 120.0000,  0.0000, 120.0000, '73050-13-10', 'LABORATORY', FALSE)
) AS t(
    psp_id, line_id, snap_id, resp_pat_id, fd_pat_id, catalog_id, pl_item_id, map_id,
    unit_price, discount_amount, net_amount, patient_share, insurance_share,
    sbs_code, item_type, cancel
);

UPDATE billing_allocation a
SET remaining_allocated_amount = 0,
    reversed_amount = a.allocated_amount,
    status = 'REVERSED',
    reversed_date = NOW(),
    reversed_by = 'manual-billing-fix',
    reversal_reason = 'Encounter 76 insurance + visit max 100; debit no longer required',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE a.encounter_id = 76
  AND a.allocation_source_type = 'DEBIT'
  AND a.status = 'ACTIVE';

UPDATE billing_allocation
SET allocated_amount = 11.2500,
    remaining_allocated_amount = 11.2500,
    reversed_amount = 0,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 124
  AND encounter_id = 76
  AND status = 'ACTIVE';

UPDATE billing_reservation
SET consumed_amount = 11.2500,
    released_amount = 70.7500,
    remaining_reserved_amount = 0,
    status = 'PARTIALLY_RELEASED',
    released_date = NOW(),
    released_by = 'manual-billing-fix',
    release_reason = 'AMOUNT_REDUCED',
    release_notes = 'CBC copay reduced after Tawuniya PL + visit max 100; remainder moved to other copays',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 52
  AND encounter_id = 76;

UPDATE billing_debit_transaction
SET status = 'REVERSED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE encounter_id = 76
  AND status = 'COMPLETED';

UPDATE billing_debit_account
SET current_debit_balance = 0,
    total_debit_created = 0,
    total_debit_settled = 0,
    available_credit = credit_limit,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 12
  AND patient_id = 36;

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
    payment_status = CASE WHEN m.cancel THEN 'CANCELLED' ELSE 'PAID' END,
    coverage_status = CASE WHEN m.cancel THEN 'NOT_COVERED' ELSE 'COVERED' END,
    not_covered_reason = CASE WHEN m.cancel THEN 'DUPLICATE_CANCELLED' ELSE NULL END,
    price_source = CASE WHEN m.cancel THEN 'DEFAULT' ELSE 'PRICE_LIST' END,
    patient_insurance_id = 21,
    waseel_sbs_mapping_id = CASE WHEN m.cancel THEN NULL ELSE m.map_id END,
    waseel_sbs_code = CASE WHEN m.cancel THEN NULL ELSE m.sbs_code END,
    notes = CASE
              WHEN m.cancel AND COALESCE(psp.notes, '') NOT LIKE '%duplicate of 30 Aug%'
              THEN COALESCE(psp.notes, '') || ' | Cancelled: duplicate of 30 Aug insurance-billed test'
              ELSE psp.notes
            END,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc76_map m
WHERE psp.id = m.psp_id
  AND psp.encounter_id = 76
  AND psp.patient_id = 36;

UPDATE billing_charge_line l
SET item_code = CASE WHEN m.cancel THEN l.item_code ELSE m.sbs_code END,
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
               WHEN m.cancel THEN 'CANCELLED'
               WHEN m.patient_share > 0 AND m.insurance_share > 0 THEN 'PARTIALLY_ALLOCATED'
               WHEN m.patient_share > 0 THEN 'ALLOCATED'
               ELSE 'OPEN'
             END,
    cancelled_date = CASE WHEN m.cancel THEN COALESCE(l.cancelled_date, NOW()) ELSE l.cancelled_date END,
    cancelled_by = CASE WHEN m.cancel THEN COALESCE(l.cancelled_by, 'manual-billing-fix') ELSE l.cancelled_by END,
    cancellation_reason = CASE
                            WHEN m.cancel THEN 'Duplicate of 30 Aug insurance-billed test; billed as cash on 27 Aug in error'
                            ELSE l.cancellation_reason
                          END,
    patient_insurance_id = 21,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc76_map m
WHERE l.id = m.line_id
  AND l.encounter_id = 76;

UPDATE billing_pricing_snapshot s
SET price_list_id = CASE WHEN m.cancel OR m.pl_item_id IS NULL THEN NULL ELSE 1 END,
    price_list_item_id = m.pl_item_id,
    pricing_source = CASE WHEN m.cancel THEN 'DEFAULT_ITEM_PRICE' ELSE 'INSURANCE_PRICE_LIST' END,
    price_source = CASE WHEN m.cancel THEN 'SETUP_FALLBACK' ELSE 'PRICE_LIST' END,
    price_list_name = CASE WHEN m.cancel THEN NULL ELSE 'One Health TAWUNIYA Price List' END,
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
    status = CASE WHEN m.cancel THEN 'CANCELLED' ELSE 'ACTIVE' END,
    calculation_payload = jsonb_build_object(
        'quantity', 1,
        'netAmount', m.net_amount,
        'unitPrice', m.unit_price,
        'grossAmount', m.unit_price,
        'priceListId', CASE WHEN m.cancel THEN NULL ELSE 1 END,
        'priceSource', CASE WHEN m.cancel THEN 'SETUP_FALLBACK' ELSE 'PRICE_LIST' END,
        'pricingSource', CASE WHEN m.cancel THEN 'DEFAULT_ITEM_PRICE' ELSE 'INSURANCE_PRICE_LIST' END,
        'discountAmount', m.discount_amount,
        'priceListItemId', m.pl_item_id,
        'priceListItemCode', m.sbs_code,
        'patientResponsibilityAmount', m.patient_share,
        'insuranceResponsibilityAmount', m.insurance_share,
        'visitMaxLimit', 100,
        'cancelledDuplicate', m.cancel
    ),
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc76_map m
WHERE s.id = m.snap_id
  AND s.encounter_id = 76;

UPDATE billing_charge_responsibility r
SET responsibility_amount = m.patient_share,
    allocated_amount = m.patient_share,
    outstanding_amount = 0,
    coverage_percentage = CASE WHEN m.cancel OR m.patient_share = 0 THEN 0 ELSE 20.000000 END,
    copay_amount = m.patient_share,
    non_covered_amount = 0,
    patient_insurance_id = 21,
    policy_number = '54768391',
    member_number = '001072394479001',
    status = CASE
               WHEN m.cancel THEN 'CANCELLED'
               WHEN m.patient_share > 0 THEN 'FULLY_ALLOCATED'
               ELSE 'CALCULATED'
             END,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc76_map m
WHERE r.id = m.resp_pat_id
  AND r.encounter_id = 76;

UPDATE billing_charge_responsibility r
SET responsibility_amount = m.insurance_share,
    allocated_amount = 0,
    outstanding_amount = m.insurance_share,
    coverage_percentage = 80.000000,
    copay_amount = 0,
    non_covered_amount = 0,
    patient_insurance_id = 21,
    policy_number = '54768391',
    member_number = '001072394479001',
    status = 'CALCULATED',
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
FROM tmp_enc76_map m
WHERE r.charge_line_id = m.line_id
  AND r.encounter_id = 76
  AND r.responsible_party_type = 'INSURANCE'
  AND NOT m.cancel;

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
    l.charge_id, m.line_id, m.psp_id, 36, 76,
    'INSURANCE', 'PRIMARY', 21, '54768391', '001072394479001',
    m.insurance_share, 0, m.insurance_share,
    80.000000, 0, 0, 0, 0, 0,
    'SAR', 'CALCULATED', FALSE, 'NOT_REQUIRED', NOW(),
    'BILLING:CREATE:PSP:' || m.psp_id || ':DIAG-ORDER:' || m.psp_id || ':RESPONSIBILITY:INSURANCE',
    'manual-billing-fix', NOW(), 'manual-billing-fix', NOW()
FROM tmp_enc76_map m
JOIN billing_charge_line l ON l.id = m.line_id AND l.encounter_id = 76
WHERE m.insurance_share > 0
  AND NOT m.cancel
  AND NOT EXISTS (
      SELECT 1 FROM billing_charge_responsibility x
      WHERE x.charge_line_id = m.line_id AND x.responsible_party_type = 'INSURANCE'
  );

DELETE FROM billing_allocation
WHERE encounter_id = 76
  AND allocation_number LIKE 'ALC-FIX76-%';

DELETE FROM billing_reservation
WHERE encounter_id = 76
  AND reservation_number LIKE 'RSV-FIX76-%';

WITH new_rsv AS (
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
        'RSV-FIX76-P' || m.psp_id,
        16, 41, 41, 36, 76, 36, m.line_id, m.resp_pat_id, m.psp_id,
        m.patient_share, 0, m.patient_share, 0,
        'SAR', 'CONSUMED', NOW(), NOW(),
        'FIX76:RESERVATION:PSP:' || m.psp_id || ':PAYMENT:41',
        '57ee1ec8-0132-4f57-90a1-88d0eec4374d'::uuid,
        'manual-billing-fix', NOW(), 'manual-billing-fix', NOW()
    FROM tmp_enc76_map m
    WHERE NOT m.cancel
      AND m.patient_share > 0
      AND m.psp_id NOT IN (197, 198)
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
    'ALC-FIX76-P' || r.patient_service_product_id,
    36, r.charge_line_id, r.charge_responsibility_id, r.patient_service_product_id,
    36, 76, 'RESERVATION', r.id, 41, 41,
    'BILLING_RESERVATION', r.id, r.reservation_number,
    m.patient_share, m.patient_share, 0,
    'SAR', 'ACTIVE', NOW(),
    'FIX76:ALLOCATION:PSP:' || r.patient_service_product_id || ':PAYMENT:41',
    '57ee1ec8-0132-4f57-90a1-88d0eec4374d'::uuid,
    'manual-billing-fix', NOW(), 'manual-billing-fix', NOW()
FROM new_rsv r
JOIN tmp_enc76_map m ON m.psp_id = r.patient_service_product_id;

DELETE FROM financial_document_items f
USING tmp_enc76_map m
WHERE f.id = m.fd_pat_id
  AND f.document_id = 47
  AND (m.cancel OR m.patient_share = 0);

UPDATE financial_document_items f
SET unit_price = m.patient_share,
    gross_amount = CASE WHEN m.net_amount = 0 THEN 0
                        ELSE ROUND(m.unit_price * m.patient_share / m.net_amount, 4) END,
    discount_amount = CASE WHEN m.net_amount = 0 THEN 0
                           ELSE ROUND(m.discount_amount * m.patient_share / m.net_amount, 4) END,
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
FROM tmp_enc76_map m
WHERE f.id = m.fd_pat_id
  AND f.document_id = 47
  AND NOT m.cancel
  AND m.patient_share > 0;

UPDATE financial_document_items f
SET unit_price = m.insurance_share,
    gross_amount = CASE WHEN m.net_amount = 0 THEN 0
                        ELSE ROUND(m.unit_price * m.insurance_share / m.net_amount, 4) END,
    discount_amount = CASE WHEN m.net_amount = 0 THEN 0
                           ELSE ROUND(m.discount_amount * m.insurance_share / m.net_amount, 4) END,
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
FROM tmp_enc76_map m
JOIN financial_document_items x
  ON x.document_id = 48
 AND x.patient_service_product_id = m.psp_id
WHERE f.id = x.id
  AND NOT m.cancel
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
    48, m.psp_id, m.line_id, m.sbs_code, l.item_description, 1,
    m.insurance_share,
    CASE WHEN m.net_amount = 0 THEN 0
         ELSE ROUND(m.unit_price * m.insurance_share / m.net_amount, 4) END,
    CASE WHEN m.net_amount = 0 THEN 0
         ELSE ROUND(m.discount_amount * m.insurance_share / m.net_amount, 4) END,
    0, m.insurance_share, 0, m.insurance_share, 0, m.insurance_share,
    0, m.insurance_share, 'PENDING', 'SAR',
    'manual-billing-fix', NOW(), 'manual-billing-fix', NOW()
FROM tmp_enc76_map m
JOIN billing_charge_line l ON l.id = m.line_id
WHERE m.insurance_share > 0
  AND NOT m.cancel
  AND NOT EXISTS (
      SELECT 1 FROM financial_document_items x
      WHERE x.document_id = 48 AND x.patient_service_product_id = m.psp_id
  );

UPDATE financial_documents
SET total_amount = 100.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 47 AND encounter_id = 76;

UPDATE financial_documents
SET total_amount = 860.0000,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 48 AND encounter_id = 76;

UPDATE billing_charge
SET gross_amount = 1250.0000,
    discount_amount = 290.0000,
    net_amount = 960.0000,
    allocated_amount = 100.0000,
    outstanding_amount = 860.0000,
    line_count = 14,
    last_modified_by = 'manual-billing-fix',
    last_modified_date = NOW()
WHERE id = 36 AND encounter_id = 76 AND patient_id = 36;

-- Claim: rebuild latest (15) from INV 48 after billing, fail 9 and 15
DELETE FROM claim_item WHERE claim_request_id = 15;

INSERT INTO claim_item (
    claim_request_id, sequence, patient_service_product_id,
    financial_document_item_id, billing_charge_line_id,
    item_type, item_code, item_description, invoice_no, quantity,
    unit_price, net, patient_share, payer_share,
    created_by, created_date, last_modified_by, last_modified_date
)
SELECT
    15,
    ROW_NUMBER() OVER (ORDER BY psp.id)::int,
    psp.id,
    f.id,
    f.billing_charge_line_id,
    CASE psp.billing_item_type
        WHEN 'SERVICE' THEN 'SERVICES'
        WHEN 'CONSULTATION' THEN 'SERVICES'
        WHEN 'LABORATORY' THEN 'LABORATORY'
        ELSE psp.billing_item_type
    END,
    COALESCE(l.item_code, psp.waseel_sbs_code, f.item_code),
    COALESCE(f.item_description, l.item_description),
    d.document_number,
    1,
    ROUND(psp.unit_price, 2),
    ROUND(psp.net_amount, 2),
    ROUND(psp.patient_share_amount, 2),
    ROUND(psp.insurance_share_amount, 2),
    'manual-claim-rebuild', NOW(), 'manual-claim-rebuild', NOW()
FROM financial_documents d
JOIN financial_document_items f ON f.document_id = d.id
JOIN patient_services_and_products psp
  ON psp.id = f.patient_service_product_id
 AND psp.encounter_id = 76
 AND psp.coverage_status = 'COVERED'
 AND psp.payment_status <> 'CANCELLED'
 AND psp.insurance_share_amount > 0
LEFT JOIN billing_charge_line l ON l.id = f.billing_charge_line_id
WHERE d.id = 48
  AND d.encounter_id = 76
  AND d.document_subtype = 'INSURANCE_CLAIM';

UPDATE claim_request
SET financial_document_id = 48,
    patient_id = 36,
    encounter_id = 76,
    patient_insurance_id = 21,
    claim_type = 'PROFESSIONAL',
    claim_sub_type = 'OUTPATIENT',
    claim_reference = 'CLM-E00175-1788456392040',
    total_net = 960.00,
    status = 'FAILED',
    outcome = NULL,
    message = 'Billing + claim rebuilt: insurance from 27 Aug, visit max 100, duplicates cancelled, Cons003 kept. Resubmit from Claims.',
    request_json = NULL,
    response_json = NULL,
    validation_errors_json = NULL,
    upload_id = NULL,
    submitted_at = NULL,
    last_modified_by = 'manual-claim-rebuild',
    last_modified_date = NOW()
WHERE id = 15 AND encounter_id = 76;

UPDATE claim_request
SET status = 'FAILED',
    message = COALESCE(message, '') || ' | superseded by full billing+claim rebuild on claim 15',
    last_modified_by = 'manual-claim-rebuild',
    last_modified_date = NOW()
WHERE id = 9 AND encounter_id = 76;

DO $$
DECLARE
    v_net numeric;
    v_alloc numeric;
    v_out numeric;
    v_pat_fd numeric;
    v_ins_fd numeric;
    v_psp_pat numeric;
    v_psp_ins numeric;
    v_cov text;
    v_ins_id bigint;
    v_cancelled int;
    v_debit numeric;
    v_active_debit_alloc int;
    v_cons_line text;
    v_cons_claim text;
    v_items int;
    v_cpat numeric;
    v_cpay numeric;
    v_cnet numeric;
    v_hdr numeric;
    v_status15 text;
    v_status9 text;
    v_b12_pat numeric;
BEGIN
    SELECT net_amount, allocated_amount, outstanding_amount
      INTO v_net, v_alloc, v_out
    FROM billing_charge WHERE id = 36;

    SELECT total_amount INTO v_pat_fd FROM financial_documents WHERE id = 47;
    SELECT total_amount INTO v_ins_fd FROM financial_documents WHERE id = 48;

    SELECT COALESCE(SUM(patient_share_amount),0), COALESCE(SUM(insurance_share_amount),0)
      INTO v_psp_pat, v_psp_ins
    FROM patient_services_and_products
    WHERE encounter_id = 76 AND payment_status <> 'CANCELLED';

    SELECT coverage_type, patient_insurance_id INTO v_cov, v_ins_id
    FROM patient_encounters WHERE id = 76;

    SELECT COUNT(*) INTO v_cancelled
    FROM patient_services_and_products
    WHERE encounter_id = 76 AND id IN (205,206,207) AND payment_status = 'CANCELLED';

    SELECT patient_share_amount INTO v_b12_pat
    FROM patient_services_and_products WHERE id = 290 AND encounter_id = 76;

    SELECT current_debit_balance INTO v_debit
    FROM billing_debit_account WHERE patient_id = 36;

    SELECT COUNT(*) INTO v_active_debit_alloc
    FROM billing_allocation
    WHERE encounter_id = 76 AND allocation_source_type = 'DEBIT' AND status = 'ACTIVE';

    SELECT item_code INTO v_cons_line
    FROM billing_charge_line WHERE id = 195 AND encounter_id = 76;

    SELECT COUNT(*), COALESCE(SUM(patient_share),0), COALESCE(SUM(payer_share),0), COALESCE(SUM(net),0)
      INTO v_items, v_cpat, v_cpay, v_cnet
    FROM claim_item WHERE claim_request_id = 15;

    SELECT item_code INTO v_cons_claim
    FROM claim_item WHERE claim_request_id = 15 AND patient_service_product_id = 197;

    SELECT total_net, status INTO v_hdr, v_status15 FROM claim_request WHERE id = 15;
    SELECT status INTO v_status9 FROM claim_request WHERE id = 9;

    IF v_cov IS DISTINCT FROM 'INSURANCE' OR v_ins_id IS DISTINCT FROM 21 THEN
        RAISE EXCEPTION 'Encounter coverage not switched: % / %', v_cov, v_ins_id;
    END IF;
    IF v_net IS DISTINCT FROM 960.0000 OR v_alloc IS DISTINCT FROM 100.0000 OR v_out IS DISTINCT FROM 860.0000 THEN
        RAISE EXCEPTION 'Charge totals mismatch net=% alloc=% out=%', v_net, v_alloc, v_out;
    END IF;
    IF v_pat_fd IS DISTINCT FROM 100.0000 OR v_ins_fd IS DISTINCT FROM 860.0000 THEN
        RAISE EXCEPTION 'Invoice totals mismatch patient=% insurance=%', v_pat_fd, v_ins_fd;
    END IF;
    IF v_psp_pat IS DISTINCT FROM 100.0000 OR v_psp_ins IS DISTINCT FROM 860.0000 THEN
        RAISE EXCEPTION 'PSP share mismatch patient=% insurance=%', v_psp_pat, v_psp_ins;
    END IF;
    IF v_b12_pat IS DISTINCT FROM 14.5000 THEN
        RAISE EXCEPTION 'Visit max not applied on B12, patient share=%', v_b12_pat;
    END IF;
    IF v_cancelled IS DISTINCT FROM 3 THEN
        RAISE EXCEPTION 'Expected 3 cancelled duplicate PSPs, got %', v_cancelled;
    END IF;
    IF v_debit IS DISTINCT FROM 0 THEN
        RAISE EXCEPTION 'Debit balance not cleared: %', v_debit;
    END IF;
    IF v_active_debit_alloc <> 0 THEN
        RAISE EXCEPTION 'Active debit allocations remain: %', v_active_debit_alloc;
    END IF;
    IF v_cons_line IS DISTINCT FROM 'Cons003' OR v_cons_claim IS DISTINCT FROM 'Cons003' THEN
        RAISE EXCEPTION 'Cons003 changed: line=% claim=%', v_cons_line, v_cons_claim;
    END IF;
    IF v_items IS DISTINCT FROM 14 THEN
        RAISE EXCEPTION 'Expected 14 claim items, got %', v_items;
    END IF;
    IF v_cpat IS DISTINCT FROM 100.00 OR v_cpay IS DISTINCT FROM 860.00 THEN
        RAISE EXCEPTION 'Claim shares mismatch patient=% payer=%', v_cpat, v_cpay;
    END IF;
    IF v_cnet IS DISTINCT FROM 960.00 OR v_hdr IS DISTINCT FROM 960.00 THEN
        RAISE EXCEPTION 'Claim net mismatch items=% header=%', v_cnet, v_hdr;
    END IF;
    IF v_status15 IS DISTINCT FROM 'FAILED' OR v_status9 IS DISTINCT FROM 'FAILED' THEN
        RAISE EXCEPTION 'Claims not FAILED: 15=% 9=%', v_status15, v_status9;
    END IF;
END $$;

SELECT id, coverage_type, patient_insurance_id, billing_status
FROM patient_encounters WHERE id = 76;

SELECT id, payment_status, coverage_status, waseel_sbs_code, unit_price, discount_amount,
       net_amount, patient_share_amount, insurance_share_amount
FROM patient_services_and_products
WHERE encounter_id = 76
ORDER BY id;

SELECT id, document_subtype, total_amount, claim_reference
FROM financial_documents WHERE encounter_id = 76 ORDER BY id;

SELECT id, status, total_net, claim_reference
FROM claim_request WHERE id IN (9, 15) ORDER BY id;

SELECT sequence, patient_service_product_id, item_code, unit_price, net, patient_share, payer_share
FROM claim_item WHERE claim_request_id = 15 ORDER BY sequence;

COMMIT;
