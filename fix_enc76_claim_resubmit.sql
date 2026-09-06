-- =============================================================================
-- Patient 36 / Encounter 76 (E00175) — claim items only, one shot
-- Database: DBOneHealthLocaly
--
-- Does NOT touch Cons003 / PSP / invoices / shares / mapping.
-- Rebuilds claim_request 15 from INV 48 (14 covered lines) and sets both
-- claims FAILED so Claims Submit is allowed.
--
-- After COMMIT: resubmit from UI
--   POST /api/patient/internal/waseel/invoices/48/claims/submit
-- =============================================================================

BEGIN;

-- Guard: billing must already be insurance + visit max 100
DO $$
DECLARE
    v_cov text;
    v_ins_id bigint;
    v_psp_pat numeric;
    v_psp_ins numeric;
    v_fd numeric;
    v_fd_items int;
    v_cons text;
BEGIN
    SELECT coverage_type, patient_insurance_id
      INTO v_cov, v_ins_id
    FROM patient_encounters WHERE id = 76 AND patient_id = 36;

    SELECT COALESCE(SUM(patient_share_amount),0),
           COALESCE(SUM(insurance_share_amount),0)
      INTO v_psp_pat, v_psp_ins
    FROM patient_services_and_products
    WHERE encounter_id = 76 AND payment_status <> 'CANCELLED';

    SELECT total_amount INTO v_fd
    FROM financial_documents
    WHERE id = 48 AND encounter_id = 76 AND document_subtype = 'INSURANCE_CLAIM';

    SELECT COUNT(*) INTO v_fd_items
    FROM financial_document_items WHERE document_id = 48;

    SELECT item_code INTO v_cons
    FROM billing_charge_line WHERE id = 195 AND encounter_id = 76;

    IF v_cov IS DISTINCT FROM 'INSURANCE' OR v_ins_id IS DISTINCT FROM 21 THEN
        RAISE EXCEPTION 'Encounter 76 is not insurance/21: % / %', v_cov, v_ins_id;
    END IF;
    IF v_psp_pat IS DISTINCT FROM 100.0000 OR v_psp_ins IS DISTINCT FROM 860.0000 THEN
        RAISE EXCEPTION 'PSP shares not 100/860: pat=% ins=%', v_psp_pat, v_psp_ins;
    END IF;
    IF v_fd IS DISTINCT FROM 860.0000 OR v_fd_items IS DISTINCT FROM 14 THEN
        RAISE EXCEPTION 'INV 48 not 860 / 14 items: total=% items=%', v_fd, v_fd_items;
    END IF;
    IF v_cons IS DISTINCT FROM 'Cons003' THEN
        RAISE EXCEPTION 'Charge line 195 is %, expected Cons003 — abort, do not change mapping', v_cons;
    END IF;
END $$;

DROP TABLE IF EXISTS tmp_enc76_claim_lines;
CREATE TEMP TABLE tmp_enc76_claim_lines AS
SELECT *
FROM (VALUES
-- seq, psp, fd_item, line, type, code, description, unit, net, patient, payer
( 1, 197, 159, 195, 'SERVICES',    'Cons003',     'Specilaist Fees',                               90.00,  90.00, 18.00,  72.00),
( 2, 198, 191, 196, 'LABORATORY',  '73100-00-80', 'Complete Blood Count (CBC)',                    75.00,  56.25, 11.25,  45.00),
( 3, 199, 187, 197, 'LABORATORY',  '73050-18-50', 'HbA1c (Glycated Haemoglobin)',                 120.00,  90.00, 18.00,  72.00),
( 4, 200, 188, 198, 'LABORATORY',  '73050-09-80', 'Serum Creatinine',                              30.00,  22.50,  4.50,  18.00),
( 5, 201, 189, 199, 'LABORATORY',  '73050-15-50', 'Fasting Blood Sugar (FBS)',                     20.00,  15.00,  3.00,  12.00),
( 6, 202, 192, 201, 'LABORATORY',  '73050-36-00', 'ALT (Alanine Aminotransferase)',                30.00,  22.50,  4.50,  18.00),
( 7, 203, 190, 200, 'LABORATORY',  '73050-36-20', 'Triglycerides',                                 45.00,  33.75,  6.75,  27.00),
( 8, 204, 193, 202, 'LABORATORY',  '73050-23-60', 'HDL Cholesterol',                               50.00,  37.50,  7.50,  30.00),
( 9, 208, 194, 206, 'LABORATORY',  '73050-23-80', 'LDL Cholesterol',                               50.00,  37.50,  7.50,  30.00),
(10, 209, 195, 207, 'LABORATORY',  '73050-35-90', 'AST (Aspartate Aminotransferase)',              30.00,  22.50,  4.50,  18.00),
(11, 290, 160, 288, 'LABORATORY',  '73050-10-40', 'Measurement of vitamin B12 (cyanocobalamin)',  205.00, 153.75, 14.50, 139.25),
(12, 291, 161, 289, 'LABORATORY',  '73050-35-50', 'Measurement of thyroid stimulating hormone (TSH)', 140.00, 105.00,  0.00, 105.00),
(13, 292, 162, 290, 'LABORATORY',  '73050-05-30', 'Measurement of vitamin D 25-hydroxy',          205.00, 153.75,  0.00, 153.75),
(14, 293, 163, 291, 'LABORATORY',  '73050-13-10', 'Measurement of serum ferritin',                160.00, 120.00,  0.00, 120.00)
) AS t(
    seq, psp_id, fd_item_id, line_id, item_type, item_code, item_description,
    unit_price, net_amount, patient_share, payer_share
);

DELETE FROM claim_item WHERE claim_request_id = 15;

INSERT INTO claim_item (
    claim_request_id,
    sequence,
    patient_service_product_id,
    financial_document_item_id,
    billing_charge_line_id,
    item_type,
    item_code,
    item_description,
    invoice_no,
    quantity,
    unit_price,
    net,
    patient_share,
    payer_share,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
SELECT
    15,
    m.seq,
    m.psp_id,
    m.fd_item_id,
    m.line_id,
    m.item_type,
    m.item_code,
    m.item_description,
    'INV-2026-120261120261000057',
    1,
    m.unit_price,
    m.net_amount,
    m.patient_share,
    m.payer_share,
    'manual-claim-rebuild',
    NOW(),
    'manual-claim-rebuild',
    NOW()
FROM tmp_enc76_claim_lines m;

-- Latest claim: unlock resubmit
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
    message = 'Claim items rebuilt from INV 48 (14 lines, visit max 100, Cons003 unchanged). Resubmit from Claims screen.',
    request_json = NULL,
    response_json = NULL,
    validation_errors_json = NULL,
    upload_id = NULL,
    submitted_at = NULL,
    last_modified_by = 'manual-claim-rebuild',
    last_modified_date = NOW()
WHERE id = 15
  AND encounter_id = 76;

-- Older rejected claim: keep history, block reuse
UPDATE claim_request
SET status = 'FAILED',
    message = COALESCE(message, '') || ' | superseded by INV 48 14-line rebuild on claim 15',
    last_modified_by = 'manual-claim-rebuild',
    last_modified_date = NOW()
WHERE id = 9
  AND encounter_id = 76;

DO $$
DECLARE
    v_items int;
    v_pat numeric;
    v_pay numeric;
    v_net numeric;
    v_cons text;
    v_status15 text;
    v_status9 text;
    v_hdr numeric;
    v_sbs_psp text;
BEGIN
    SELECT COUNT(*),
           COALESCE(SUM(patient_share),0),
           COALESCE(SUM(payer_share),0),
           COALESCE(SUM(net),0)
      INTO v_items, v_pat, v_pay, v_net
    FROM claim_item WHERE claim_request_id = 15;

    SELECT item_code INTO v_cons
    FROM claim_item
    WHERE claim_request_id = 15 AND patient_service_product_id = 197;

    SELECT total_net, status INTO v_hdr, v_status15
    FROM claim_request WHERE id = 15;

    SELECT status INTO v_status9 FROM claim_request WHERE id = 9;

    SELECT waseel_sbs_code INTO v_sbs_psp
    FROM patient_services_and_products WHERE id = 197 AND encounter_id = 76;

    IF v_items IS DISTINCT FROM 14 THEN
        RAISE EXCEPTION 'Expected 14 claim_item rows on claim 15, got %', v_items;
    END IF;
    IF v_pat IS DISTINCT FROM 100.00 OR v_pay IS DISTINCT FROM 860.00 THEN
        RAISE EXCEPTION 'Claim 15 shares mismatch patient=% payer=%', v_pat, v_pay;
    END IF;
    IF v_net IS DISTINCT FROM 960.00 OR v_hdr IS DISTINCT FROM 960.00 THEN
        RAISE EXCEPTION 'Claim 15 net mismatch items=% header=%', v_net, v_hdr;
    END IF;
    IF v_cons IS DISTINCT FROM 'Cons003' THEN
        RAISE EXCEPTION 'Claim consultation code %, expected Cons003', v_cons;
    END IF;
    IF v_status15 IS DISTINCT FROM 'FAILED' OR v_status9 IS DISTINCT FROM 'FAILED' THEN
        RAISE EXCEPTION 'Claims not FAILED: 15=% 9=%', v_status15, v_status9;
    END IF;

    -- PSP consultation code is intentionally not changed
    RAISE NOTICE 'PSP 197 waseel_sbs_code left unchanged: %', v_sbs_psp;
END $$;

SELECT id, status, total_net, claim_reference, financial_document_id, patient_insurance_id, message
FROM claim_request
WHERE id IN (9, 15)
ORDER BY id;

SELECT sequence, patient_service_product_id, item_type, item_code,
       unit_price, net, patient_share, payer_share
FROM claim_item
WHERE claim_request_id = 15
ORDER BY sequence;

COMMIT;
