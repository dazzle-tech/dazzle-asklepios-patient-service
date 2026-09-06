\pset pager off

SELECT 'CHARGE_HDR' AS section;
SELECT * FROM billing_charge WHERE encounter_id = 35;

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
  ON fp.billing_charge_line_id = l.id AND fp.document_id = 17
LEFT JOIN financial_document_items fi
  ON fi.billing_charge_line_id = l.id AND fi.document_id = 18
WHERE l.encounter_id = 35
ORDER BY l.patient_service_product_id;

SELECT 'DEBIT_OTHER' AS section;
SELECT id, encounter_id, amount, status, transaction_type
FROM billing_debit_transaction WHERE patient_id = 14;

SELECT 'DEBIT_ALLOC_OTHER' AS section;
SELECT encounter_id, COUNT(*) AS n, SUM(allocated_amount) AS amt
FROM billing_allocation
WHERE allocation_source_type = 'DEBIT' AND status = 'ACTIVE'
  AND patient_id = 14
GROUP BY encounter_id;

SELECT 'WALLET_RSV' AS section;
SELECT id, encounter_id, payment_id, original_reserved_amount, remaining_reserved_amount,
       consumed_amount, released_amount, status
FROM billing_reservation WHERE patient_id = 14 ORDER BY id;

SELECT 'CLAIMS' AS section;
SELECT * FROM waseel_claim_request WHERE encounter_id = 35;

SELECT 'PRICE_LIST' AS section;
SELECT id, name, payor_id, insurance_company_id, is_active, status
FROM price_list_setup WHERE id = 1;

SELECT 'PAYOR' AS section;
SELECT id, name FROM payor WHERE id = 20;

SELECT 'FD_ITEM_CONS_CLAIM' AS section;
SELECT * FROM financial_document_items WHERE id = 37;
