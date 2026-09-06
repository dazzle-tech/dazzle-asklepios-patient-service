\pset pager off

SELECT 'IDS' AS section;
SELECT l.id AS line_id, l.patient_service_product_id AS psp_id,
       l.current_pricing_snapshot_id AS snap_id,
       rp.id AS resp_pat_id, ri.id AS resp_ins_id,
       fp.id AS fd_pat_id, fi.id AS fd_ins_id
FROM billing_charge_line l
LEFT JOIN billing_charge_responsibility rp
  ON rp.charge_line_id = l.id AND rp.responsible_party_type = 'PATIENT'
LEFT JOIN billing_charge_responsibility ri
  ON ri.charge_line_id = l.id AND ri.responsible_party_type = 'INSURANCE'
LEFT JOIN financial_document_items fp
  ON fp.billing_charge_line_id = l.id AND fp.document_id = 43
LEFT JOIN financial_document_items fi
  ON fi.billing_charge_line_id = l.id AND fi.document_id = 44
WHERE l.encounter_id = 45
ORDER BY l.patient_service_product_id;

SELECT 'CHARGE_HDR' AS section;
SELECT id, charge_number, status, gross_amount, discount_amount, net_amount,
       allocated_amount, outstanding_amount, line_count
FROM billing_charge WHERE encounter_id = 45;
