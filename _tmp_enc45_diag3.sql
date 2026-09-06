\pset pager off

SELECT 'DIAG_NAMES' AS section;
SELECT id, left(name,80) AS name
FROM diagnostic_test
WHERE id IN (165,139,147,144,176,152,175,150,153,138,151,146)
ORDER BY id;

SELECT 'MAPPINGS' AS section;
SELECT wim.id, wim.item_type, wim.source_id, wim.item_code, left(wim.item_name,70) AS item_name, wim.is_active
FROM waseel_item_mapping wim
WHERE (wim.item_type = 'SERVICE' AND wim.source_id = 29272)
   OR (wim.item_type = 'LABORATORY' AND wim.source_id IN (165,139,147,144,176,152,175,150,153,138,151,146))
ORDER BY wim.source_id, wim.is_active DESC, wim.id;

SELECT 'PRICE_LIST' AS section;
SELECT pli.id, pli.item_type, pli.source_id, pli.item_code, left(pli.item_name,60) AS item_name,
       pli.unit_price, pli.discount_percentage, pli.is_active, pli.waseel_item_mapping_id
FROM price_list_setup_item pli
WHERE pli.price_list_setup_id = 1
  AND (
    (pli.item_type = 'SERVICE' AND pli.source_id = 29272)
    OR (pli.item_type = 'LABORATORY' AND pli.source_id IN (165,139,147,144,176,152,175,150,153,138,151,146))
    OR pli.item_code IN ('Cons003','73100-00-80')
  )
ORDER BY pli.source_id, pli.id;

SELECT 'PSP_VS_MAP' AS section;
SELECT psp.id AS psp_id,
       psp.billing_item_type,
       COALESCE(psp.service_id, psp.diagnostic_test_id) AS setup_id,
       psp.coverage_status,
       psp.price_source,
       psp.unit_price,
       psp.discount_amount,
       psp.net_amount,
       psp.patient_share_amount,
       psp.insurance_share_amount,
       psp.waseel_sbs_mapping_id,
       psp.waseel_sbs_code,
       wim.id AS active_mapping_id,
       wim.item_code AS mapping_code,
       pli.id AS pl_item_id,
       pli.unit_price AS pl_unit,
       pli.discount_percentage AS pl_disc
FROM patient_services_and_products psp
LEFT JOIN waseel_item_mapping wim
  ON wim.is_active = TRUE
 AND (
      (psp.billing_item_type = 'SERVICE' AND wim.item_type = 'SERVICE' AND wim.source_id = psp.service_id)
   OR (psp.billing_item_type = 'LABORATORY' AND wim.item_type = 'LABORATORY' AND wim.source_id = psp.diagnostic_test_id)
 )
LEFT JOIN price_list_setup_item pli
  ON pli.price_list_setup_id = 1
 AND pli.is_active = TRUE
 AND (
      (psp.billing_item_type = 'SERVICE' AND pli.item_type = 'SERVICE' AND pli.source_id = psp.service_id)
   OR (psp.billing_item_type = 'LABORATORY' AND pli.item_type = 'LABORATORY' AND pli.source_id = psp.diagnostic_test_id)
 )
WHERE psp.encounter_id = 45
ORDER BY psp.id;

SELECT 'SHARE_SUM' AS section;
SELECT SUM(patient_share_amount) AS sum_patient,
       SUM(insurance_share_amount) AS sum_insurance,
       SUM(net_amount) AS sum_net,
       SUM(paid_amount) AS sum_paid,
       SUM(remaining_amount) AS sum_remaining
FROM patient_services_and_products
WHERE encounter_id = 45 AND payment_status <> 'CANCELLED';

SELECT 'SNAPSHOTS' AS section;
SELECT id, charge_line_id, patient_service_product_id, pricing_source, price_source,
       price_list_id, price_list_item_id, price_list_name, price_list_item_code,
       base_unit_price, discount_amount, net_amount,
       patient_responsibility_amount, insurance_responsibility_amount, status
FROM billing_pricing_snapshot WHERE encounter_id = 45 ORDER BY id;
