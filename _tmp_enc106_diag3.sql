\pset pager off

SELECT 'RAD_MAPPING' AS section;
SELECT * FROM waseel_item_mapping
WHERE item_code = '57512-03-02'
   OR (item_type = 'RADIOLOGY' AND source_id = 193)
   OR item_name ILIKE '%hand%wrist%'
   OR item_name ILIKE '%Wrist%';

SELECT 'RAD_PRICE' AS section;
SELECT id, item_type, source_id, item_code, left(item_name,80), unit_price, discount_percentage, is_active, waseel_item_mapping_id
FROM price_list_setup_item
WHERE item_code = '57512-03-02'
   OR (item_type = 'RADIOLOGY' AND source_id = 193)
   OR item_name ILIKE '%Hand%Wrist%'
   OR item_name ILIKE '%wrist%';

SELECT 'SBS_57512' AS section;
SELECT id, code, left(name,80) AS name, category
FROM waseel_sbs_catalog
WHERE code ILIKE '%57512%' OR name ILIKE '%hand%wrist%' OR name ILIKE '%Wrist%Hand%'
LIMIT 40;

SELECT 'DIAG_193' AS section;
SELECT id, left(name,80) AS name FROM diagnostic_test WHERE id = 193;

SELECT 'PAYMENTS_COLS' AS section;
SELECT id, payment_number, patient_id, encounter_id, amount, status, created_date
FROM billing_payment WHERE id IN (34, 75);

SELECT 'CHARGE_HDR' AS section;
SELECT id, charge_number, status, patient_insurance_id,
       gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount,
       reserved_amount, line_count
FROM billing_charge WHERE encounter_id = 106;

SELECT 'FD_TOTALS' AS section;
SELECT id, document_subtype, total_amount, status, eligibility_reference
FROM financial_documents WHERE encounter_id = 106;

SELECT 'PSP_MAPPING_GAPS' AS section;
SELECT psp.id, psp.billing_item_type, psp.diagnostic_test_id, psp.service_id, psp.source_id,
       psp.waseel_sbs_mapping_id, psp.waseel_sbs_code, psp.price_source,
       wim.id AS mapping_found_id, wim.item_code AS mapping_code
FROM patient_services_and_products psp
LEFT JOIN waseel_item_mapping wim
  ON wim.is_active = TRUE
 AND (
      (psp.billing_item_type = 'SERVICE' AND wim.item_type = 'SERVICE' AND wim.source_id = psp.service_id)
   OR (psp.billing_item_type = 'LABORATORY' AND wim.item_type = 'LABORATORY' AND wim.source_id = psp.diagnostic_test_id)
   OR (psp.billing_item_type = 'RADIOLOGY' AND wim.item_type = 'RADIOLOGY' AND wim.source_id = psp.diagnostic_test_id)
 )
WHERE psp.encounter_id = 106
ORDER BY psp.id;
