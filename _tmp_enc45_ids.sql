\pset pager off

SELECT 'IDS' AS section;
SELECT l.id AS line_id, l.patient_service_product_id AS psp_id,
       l.current_pricing_snapshot_id AS snap_id,
       l.reserved_amount, l.item_description,
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

SELECT 'PSP_EXTRA' AS section;
SELECT id, total_amount, gross_amount, quantity, notes, not_covered_reason,
       waseel_sbs_mapping_id, waseel_sbs_code, price_source, coverage_status
FROM patient_services_and_products WHERE encounter_id = 45 ORDER BY id;

SELECT 'PAY_TX' AS section;
SELECT id, payment_id, amount, status, created_date
FROM billing_payment_transaction WHERE payment_id IN (8,16) OR encounter_id = 45;

SELECT 'WALLET_FULL' AS section;
SELECT * FROM billing_wallet WHERE patient_id = 22;

SELECT 'DEBIT_ACC' AS section;
SELECT id, credit_limit, current_debit_balance, available_credit, total_debit_created, total_debit_settled
FROM billing_debit_account WHERE patient_id = 22;

SELECT 'RSV_FULL' AS section;
SELECT id, reservation_number, wallet_id, payment_id, payment_transaction_id,
       charge_id, charge_line_id, charge_responsibility_id, patient_service_product_id,
       original_reserved_amount, remaining_reserved_amount, consumed_amount, released_amount,
       status, idempotency_key, transaction_group_id
FROM billing_reservation WHERE encounter_id = 45;

SELECT 'ALLOC_FULL' AS section;
SELECT id, allocation_number, charge_id, allocation_source_type, reservation_id, payment_id,
       payment_transaction_id, source_reference_type, source_reference_id, source_reference_number,
       allocated_amount, remaining_allocated_amount, reversed_amount, status,
       idempotency_key, transaction_group_id, patient_service_product_id
FROM billing_allocation WHERE encounter_id = 45 ORDER BY id;

SELECT 'FD_HDR' AS section;
SELECT id, document_number, document_type, document_subtype, status, total_amount,
       eligibility_reference, claim_reference, currency, patient_id, encounter_id
FROM financial_documents WHERE encounter_id = 45;

SELECT 'INS_EXTRA' AS section;
SELECT id, default_copayment_percent, default_maximum_copayment, remaining_benefits,
       plan_id, network_id, member_card_id, policy_number
FROM patient_insurances WHERE id = 16;

SELECT 'MAP_IDS' AS section;
SELECT wim.id, wim.item_type, wim.source_id, wim.item_code, wim.sbs_catalog_id
FROM waseel_item_mapping wim
WHERE (wim.item_type = 'SERVICE' AND wim.source_id = 29272)
   OR (wim.item_type = 'LABORATORY' AND wim.source_id IN (165,139,147,144,176,152,175,150,153,138,151,146))
ORDER BY wim.source_id;

SELECT 'CHARGE_LINE_EXTRA' AS section;
SELECT id, charge_id, item_code, item_description, quantity, reserved_amount, currency, status
FROM billing_charge_line WHERE encounter_id = 45 ORDER BY id;

SELECT 'SNAP_PAYLOAD' AS section;
SELECT id, calculation_payload
FROM billing_pricing_snapshot WHERE encounter_id = 45 AND id = 41;
