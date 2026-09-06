-- =============================================================================
-- Encounter 45 / Patient 22 — rebuild claim_item from corrected billing
-- Database: DBOneHealthLocaly
--
-- Run AFTER fix_enc45_visit_max.sql (FD 44 must have 13 insurance lines,
-- total 758.75, PSP patient share 100 / insurance 758.75).
--
-- What this does:
--   1) Shows current claim_request / claim_item
--   2) Deletes old claim_item rows
--   3) Inserts 13 items from insurance invoice 44 + corrected PSP
--      (unit/net/patient/payer = real post-fix shares)
--   4) Sets claim_request to FAILED so Claims screen Submit is allowed
--      (active statuses SUBMITTING/SUBMITTED/ACCEPTED block resubmit)
--
-- After this SQL: submit from UI
--   POST /api/patient/internal/waseel/invoices/44/claims/submit
-- The API builds a NEW claim from FD 44 (does not reuse these rows).
-- These rows keep the old request aligned with the corrected bill.
--
-- REVIEW THEN RUN. Does not auto-apply.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0) Inspect current claim (run this first if you only want to look)
-- -----------------------------------------------------------------------------
SELECT cr.id, cr.encounter_id, cr.financial_document_id, cr.patient_insurance_id,
       cr.prov_claim_no, cr.claim_reference, cr.claim_type, cr.claim_sub_type,
       cr.total_net, cr.status, cr.outcome, cr.upload_id, cr.submitted_at, cr.message
FROM claim_request cr
WHERE cr.encounter_id = 45
   OR cr.financial_document_id = 44
ORDER BY cr.id;

SELECT ci.id, ci.claim_request_id, ci.sequence,
       ci.patient_service_product_id, ci.financial_document_item_id, ci.billing_charge_line_id,
       ci.item_type, ci.item_code, left(ci.item_description, 50) AS item_description,
       ci.invoice_no, ci.quantity, ci.unit_price, ci.net, ci.patient_share, ci.payer_share
FROM claim_item ci
JOIN claim_request cr ON cr.id = ci.claim_request_id
WHERE cr.encounter_id = 45
   OR cr.financial_document_id = 44
ORDER BY ci.claim_request_id, ci.sequence;

BEGIN;

-- Guard: billing fix must already be applied
DO $$
DECLARE
    v_psp_pat numeric;
    v_psp_ins numeric;
    v_ins_fd numeric;
    v_ins_items int;
    v_claim_id bigint;
BEGIN
    SELECT COALESCE(SUM(patient_share_amount),0), COALESCE(SUM(insurance_share_amount),0)
      INTO v_psp_pat, v_psp_ins
    FROM patient_services_and_products
    WHERE encounter_id = 45 AND payment_status <> 'CANCELLED';

    SELECT total_amount INTO v_ins_fd
    FROM financial_documents
    WHERE id = 44 AND encounter_id = 45 AND document_subtype = 'INSURANCE_CLAIM';

    SELECT COUNT(*) INTO v_ins_items
    FROM financial_document_items WHERE document_id = 44;

    SELECT id INTO v_claim_id
    FROM claim_request
    WHERE encounter_id = 45
       OR financial_document_id = 44
    ORDER BY id DESC
    LIMIT 1;

    IF v_psp_pat IS DISTINCT FROM 100.0000 OR v_psp_ins IS DISTINCT FROM 758.7500 THEN
        RAISE EXCEPTION
            'Run fix_enc45_visit_max.sql first. PSP patient=% insurance=%',
            v_psp_pat, v_psp_ins;
    END IF;
    IF v_ins_fd IS DISTINCT FROM 758.7500 OR v_ins_items IS DISTINCT FROM 13 THEN
        RAISE EXCEPTION
            'Insurance invoice 44 not corrected yet. total=% items=%',
            v_ins_fd, v_ins_items;
    END IF;
    IF v_claim_id IS NULL THEN
        RAISE EXCEPTION
            'No claim_request for encounter 45 / invoice 44. Submit from Claims screen after billing fix — no claim_item to rebuild.';
    END IF;
END $$;

-- Latest claim on this visit / insurance invoice
DROP TABLE IF EXISTS tmp_enc45_claim;
CREATE TEMP TABLE tmp_enc45_claim AS
SELECT id AS claim_request_id
FROM claim_request
WHERE encounter_id = 45
   OR financial_document_id = 44
ORDER BY id DESC
LIMIT 1;

DELETE FROM claim_item ci
USING tmp_enc45_claim t
WHERE ci.claim_request_id = t.claim_request_id;

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
    t.claim_request_id,
    ROW_NUMBER() OVER (ORDER BY psp.id)::int AS sequence,
    psp.id,
    f.id,
    f.billing_charge_line_id,
    CASE psp.billing_item_type
        WHEN 'SERVICE' THEN 'SERVICES'
        WHEN 'CONSULTATION' THEN 'SERVICES'
        WHEN 'LABORATORY' THEN 'LABORATORY'
        WHEN 'RADIOLOGY' THEN 'IMAGING'
        ELSE psp.billing_item_type
    END,
    COALESCE(psp.waseel_sbs_code, f.item_code),
    COALESCE(f.item_description, l.item_description),
    d.document_number,
    COALESCE(f.quantity, psp.quantity, 1),
    ROUND(psp.unit_price, 2),
    ROUND(psp.net_amount, 2),
    ROUND(psp.patient_share_amount, 2),
    ROUND(psp.insurance_share_amount, 2),
    'manual-claim-rebuild',
    NOW(),
    'manual-claim-rebuild',
    NOW()
FROM tmp_enc45_claim t
JOIN financial_documents d
  ON d.id = 44
 AND d.encounter_id = 45
 AND d.document_subtype = 'INSURANCE_CLAIM'
JOIN financial_document_items f
  ON f.document_id = d.id
JOIN patient_services_and_products psp
  ON psp.id = f.patient_service_product_id
 AND psp.encounter_id = 45
 AND psp.coverage_status = 'COVERED'
LEFT JOIN billing_charge_line l
  ON l.id = f.billing_charge_line_id
WHERE psp.insurance_share_amount > 0;

-- Unlock resubmit: ACTIVE = SUBMITTING / SUBMITTED / ACCEPTED
UPDATE claim_request cr
SET financial_document_id = 44,
    patient_id = 22,
    encounter_id = 45,
    patient_insurance_id = 16,
    claim_type = COALESCE(cr.claim_type, 'PROFESSIONAL'),
    claim_sub_type = COALESCE(cr.claim_sub_type, 'OUTPATIENT'),
    claim_reference = COALESCE(d.claim_reference, cr.claim_reference),
    total_net = src.total_net,
    status = 'FAILED',
    outcome = NULL,
    message = 'Billing corrected (visit max 100, Tawuniya PL). Claim items rebuilt. Resubmit from Claims screen.',
    request_json = NULL,
    response_json = NULL,
    validation_errors_json = NULL,
    upload_id = NULL,
    submitted_at = NULL,
    last_modified_by = 'manual-claim-rebuild',
    last_modified_date = NOW()
FROM tmp_enc45_claim t
JOIN financial_documents d ON d.id = 44
JOIN LATERAL (
    SELECT ROUND(SUM(ci.net), 2) AS total_net
    FROM claim_item ci
    WHERE ci.claim_request_id = t.claim_request_id
) src ON TRUE
WHERE cr.id = t.claim_request_id;

-- Also fail any older active claims on this invoice so submit is not skipped
UPDATE claim_request
SET status = 'FAILED',
    message = COALESCE(message, '') || ' | superseded by billing-corrected rebuild',
    last_modified_by = 'manual-claim-rebuild',
    last_modified_date = NOW()
WHERE (encounter_id = 45 OR financial_document_id = 44)
  AND status IN ('SUBMITTING', 'SUBMITTED', 'ACCEPTED')
  AND id NOT IN (SELECT claim_request_id FROM tmp_enc45_claim);

DO $$
DECLARE
    v_items int;
    v_pat numeric;
    v_pay numeric;
    v_net numeric;
    v_hdr numeric;
    v_status text;
BEGIN
    SELECT COUNT(*),
           COALESCE(SUM(patient_share),0),
           COALESCE(SUM(payer_share),0),
           COALESCE(SUM(net),0)
      INTO v_items, v_pat, v_pay, v_net
    FROM claim_item
    WHERE claim_request_id = (SELECT claim_request_id FROM tmp_enc45_claim);

    SELECT total_net, status INTO v_hdr, v_status
    FROM claim_request
    WHERE id = (SELECT claim_request_id FROM tmp_enc45_claim);

    IF v_items IS DISTINCT FROM 13 THEN
        RAISE EXCEPTION 'Expected 13 claim_item rows, got %', v_items;
    END IF;
    IF v_pat IS DISTINCT FROM 100.00 OR v_pay IS DISTINCT FROM 758.75 THEN
        RAISE EXCEPTION 'Claim item shares mismatch patient=% payer=%', v_pat, v_pay;
    END IF;
    IF v_net IS DISTINCT FROM 858.75 OR v_hdr IS DISTINCT FROM 858.75 THEN
        RAISE EXCEPTION 'Claim net mismatch items=% header=%', v_net, v_hdr;
    END IF;
    IF v_status IS DISTINCT FROM 'FAILED' THEN
        RAISE EXCEPTION 'claim_request status should be FAILED for resubmit, got %', v_status;
    END IF;
END $$;

SELECT cr.id, cr.status, cr.total_net, cr.claim_reference, cr.financial_document_id,
       cr.patient_insurance_id, cr.message
FROM claim_request cr
JOIN tmp_enc45_claim t ON t.claim_request_id = cr.id;

SELECT sequence, patient_service_product_id, item_type, item_code,
       unit_price, net, patient_share, payer_share,
       financial_document_item_id, billing_charge_line_id
FROM claim_item
WHERE claim_request_id = (SELECT claim_request_id FROM tmp_enc45_claim)
ORDER BY sequence;

COMMIT;
