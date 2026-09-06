\pset pager off

SELECT 'FULL_MAP' AS section;
SELECT psp.id AS psp_id,
       psp.billing_item_type,
       psp.diagnostic_test_id AS diag_id,
       psp.service_id,
       left(COALESCE(dt.name, 'Specialist Fees'), 50) AS item_name,
       psp.coverage_status AS cur_cov,
       psp.price_source AS cur_src,
       psp.not_covered_reason,
       psp.waseel_sbs_mapping_id AS psp_map_id,
       psp.waseel_sbs_code AS psp_sbs,
       psp.unit_price AS cur_unit,
       psp.discount_amount AS cur_disc,
       psp.net_amount AS cur_net,
       psp.patient_share_amount AS cur_pat,
       psp.insurance_share_amount AS cur_ins,
       psp.paid_amount AS cur_paid,
       wim.id AS map_id,
       wim.item_code AS sbs,
       left(wim.item_name, 40) AS map_name,
       wim.is_active AS map_active,
       pli.id AS pl_id,
       pli.unit_price AS pl_unit,
       pli.discount_percentage AS pl_disc,
       ROUND(pli.unit_price * (1 - COALESCE(pli.discount_percentage,0)/100.0), 4) AS pl_net,
       CASE WHEN pli.id IS NOT NULL THEN 'ON_PL' ELSE 'NOT_ON_PL' END AS pl_status
FROM patient_services_and_products psp
LEFT JOIN diagnostic_test dt ON dt.id = psp.diagnostic_test_id
LEFT JOIN LATERAL (
    SELECT *
    FROM waseel_item_mapping w
    WHERE w.is_active = TRUE
      AND (
           (psp.billing_item_type = 'SERVICE' AND w.item_type = 'SERVICE' AND w.source_id = psp.service_id)
        OR (psp.billing_item_type = 'LABORATORY' AND w.item_type = 'LABORATORY' AND w.source_id = psp.diagnostic_test_id)
        OR (psp.billing_item_type = 'RADIOLOGY' AND w.item_type = 'RADIOLOGY' AND w.source_id = psp.diagnostic_test_id)
      )
    ORDER BY w.id
    LIMIT 1
) wim ON TRUE
LEFT JOIN LATERAL (
    SELECT *
    FROM price_list_setup_item p
    WHERE p.price_list_setup_id = 1
      AND p.is_active = TRUE
      AND (
           (psp.billing_item_type = 'SERVICE' AND p.item_type = 'SERVICE' AND p.source_id = psp.service_id)
        OR (psp.billing_item_type = 'LABORATORY' AND p.item_type = 'LABORATORY' AND p.source_id = psp.diagnostic_test_id)
        OR (psp.billing_item_type = 'RADIOLOGY' AND p.item_type = 'RADIOLOGY' AND p.source_id = psp.diagnostic_test_id)
        OR (wim.item_code IS NOT NULL AND p.item_code = wim.item_code)
      )
    ORDER BY p.id
    LIMIT 1
) pli ON TRUE
WHERE psp.encounter_id = 35
ORDER BY psp.created_date, psp.id;

SELECT 'ALL_MAPPINGS' AS section;
SELECT wim.id, wim.item_type, wim.source_id, wim.item_code, left(wim.item_name,70) AS item_name, wim.is_active
FROM waseel_item_mapping wim
WHERE (wim.item_type = 'SERVICE' AND wim.source_id = 29272)
   OR (wim.item_type = 'LABORATORY' AND wim.source_id IN (148,151,146,153,144,147,139,138))
ORDER BY wim.source_id, wim.is_active DESC, wim.id;

SELECT 'ALL_PL_ITEMS' AS section;
SELECT pli.id, pli.item_type, pli.source_id, pli.item_code, left(pli.item_name,60) AS item_name,
       pli.unit_price, pli.discount_percentage, pli.is_active, pli.waseel_item_mapping_id, pli.price_list_setup_id
FROM price_list_setup_item pli
WHERE pli.price_list_setup_id = 1
  AND (
    (pli.item_type = 'SERVICE' AND pli.source_id = 29272)
    OR (pli.item_type = 'LABORATORY' AND pli.source_id IN (148,151,146,153,144,147,139,138))
  )
ORDER BY pli.source_id, pli.id;

SELECT 'SNAPSHOTS' AS section;
SELECT id, charge_line_id, patient_service_product_id, pricing_source, price_source,
       price_list_id, price_list_item_id, price_list_name, price_list_item_code,
       base_unit_price, discount_amount, net_amount,
       patient_responsibility_amount, insurance_responsibility_amount, status
FROM billing_pricing_snapshot WHERE encounter_id = 35 ORDER BY id;
