\pset pager off

SELECT 'FULL_MAP' AS section;
SELECT psp.id AS psp_id,
       psp.diagnostic_test_id AS diag_id,
       psp.service_id,
       left(COALESCE(dt.name, 'Specilaist Fees'), 40) AS item_name,
       psp.coverage_status AS cur_cov,
       psp.price_source AS cur_src,
       psp.unit_price AS cur_unit,
       psp.net_amount AS cur_net,
       psp.patient_share_amount AS cur_pat,
       psp.insurance_share_amount AS cur_ins,
       psp.paid_amount AS cur_paid,
       wim.id AS map_id,
       wim.item_code AS sbs,
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
        OR (wim.item_code IS NOT NULL AND p.item_code = wim.item_code)
      )
    ORDER BY p.id
    LIMIT 1
) pli ON TRUE
WHERE psp.encounter_id = 45
ORDER BY psp.id;

SELECT 'CHARGE_LINES_FULL' AS section;
SELECT id, patient_service_product_id, item_code, unit_price, discount_amount, net_amount,
       patient_responsibility_amount, insurance_responsibility_amount,
       allocated_amount, outstanding_amount, status
FROM billing_charge_line WHERE encounter_id = 45 ORDER BY id;

SELECT 'FD_VS_PSP_PAID' AS section;
SELECT psp.id AS psp_id,
       psp.paid_amount AS psp_paid,
       psp.remaining_amount AS psp_rem,
       psp.payment_status AS psp_status,
       f.paid_amount AS fd_paid,
       f.remaining_amount AS fd_rem,
       f.status AS fd_status
FROM patient_services_and_products psp
LEFT JOIN financial_document_items f
  ON f.patient_service_product_id = psp.id
 AND f.document_id = 43
WHERE psp.encounter_id = 45
ORDER BY psp.id;
