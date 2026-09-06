-- =============================================================================
-- Encounter 106 / Patient 25 — READ-ONLY verification queries (run first)
-- Database: DBOneHealthLocaly
-- DO NOT run the fix section until you review the VERIFY results.
-- =============================================================================

-- 1) Eligibility + visit max
SELECT pe.id AS encounter_id, pe.coverage_type, pe.patient_insurance_id, pe.billing_status,
       pi.id AS insurance_id, pi.max_limit, pi.patient_share, pi.eligibility_status,
       pi.site_eligibility, pi.inforce, pi.payer_name, pi.policy_number, pi.member_card_id,
       pi.last_eligibility_request_id
FROM patient_encounters pe
LEFT JOIN patient_insurances pi ON pi.id = 17
WHERE pe.id = 106;

-- 2) Current shares vs visit max 100
SELECT SUM(patient_share_amount) AS current_patient_total,
       SUM(insurance_share_amount) AS current_insurance_total,
       SUM(net_amount) AS net_total,
       100.00 AS visit_max_limit,
       SUM(patient_share_amount) - 100.00 AS excess_over_max
FROM patient_services_and_products
WHERE encounter_id = 106 AND payment_status <> 'CANCELLED';

-- 3) Line-level + mapping gaps
SELECT psp.id AS psp_id,
       psp.billing_item_type,
       COALESCE(psp.service_id, psp.diagnostic_test_id) AS setup_id,
       psp.waseel_sbs_mapping_id,
       psp.waseel_sbs_code,
       psp.price_source,
       psp.unit_price, psp.discount_amount, psp.net_amount,
       psp.patient_share_amount, psp.insurance_share_amount,
       wim.id AS active_mapping_id,
       wim.item_code AS active_mapping_code
FROM patient_services_and_products psp
LEFT JOIN waseel_item_mapping wim
  ON wim.is_active = TRUE
 AND (
      (psp.billing_item_type = 'SERVICE' AND wim.item_type = 'SERVICE' AND wim.source_id = psp.service_id)
   OR (psp.billing_item_type IN ('LABORATORY','RADIOLOGY') AND wim.item_type = psp.billing_item_type
       AND wim.source_id = psp.diagnostic_test_id)
 )
WHERE psp.encounter_id = 106
ORDER BY psp.id;

-- 4) Financial documents
SELECT id, document_subtype, total_amount, status, eligibility_reference, claim_reference
FROM financial_documents WHERE encounter_id = 106 ORDER BY id;

SELECT f.id, d.document_subtype, f.patient_service_product_id, f.item_code,
       f.net_amount, f.patient_share_amount, f.insurance_share_amount,
       f.paid_amount, f.remaining_amount, f.status
FROM financial_document_items f
JOIN financial_documents d ON d.id = f.document_id
WHERE d.encounter_id = 106
ORDER BY d.document_subtype, f.id;
