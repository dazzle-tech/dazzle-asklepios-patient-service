\pset pager off

SELECT 'BENEFIT_RULES' AS section;
SELECT id, benefit_category, item_name, item_code, network_type,
       patient_copayment_percentage, patient_maximum_copayment, maximum_benefit, approval_limit,
       is_global_default, eligibility_request_id
FROM patient_insurance_benefit_rules
WHERE patient_insurance_id = 12
ORDER BY is_global_default DESC, id;

SELECT 'FD' AS section;
SELECT id, document_number, document_type, document_subtype, status, total_amount,
       eligibility_reference, claim_reference, created_date
FROM financial_documents WHERE encounter_id = 35 ORDER BY id;

SELECT 'FD_ITEMS' AS section;
SELECT f.id, f.document_id, d.document_subtype, f.patient_service_product_id, f.billing_charge_line_id,
       f.item_code, left(f.item_description, 55) AS item_description,
       f.unit_price, f.gross_amount, f.discount_amount, f.net_amount,
       f.patient_share_amount, f.insurance_share_amount,
       f.paid_amount, f.remaining_amount, f.insurance_paid_amount, f.insurance_remaining_amount, f.status
FROM financial_document_items f
JOIN financial_documents d ON d.id = f.document_id
WHERE d.encounter_id = 35
ORDER BY d.document_subtype, f.id;

SELECT 'CHARGE' AS section;
SELECT id, charge_number, status, coverage_type, patient_insurance_id,
       gross_amount, discount_amount, net_amount,
       allocated_amount, outstanding_amount, reserved_amount, line_count
FROM billing_charge WHERE encounter_id = 35;

SELECT 'CHARGE_LINES' AS section;
SELECT id, patient_service_product_id, billing_item_type, item_code, left(item_description,45) AS descr,
       unit_price, gross_amount, discount_amount, net_amount,
       patient_responsibility_amount, insurance_responsibility_amount,
       allocated_amount, outstanding_amount, status, patient_insurance_id, current_pricing_snapshot_id
FROM billing_charge_line WHERE encounter_id = 35 ORDER BY id;

SELECT 'RESP' AS section;
SELECT id, charge_line_id, patient_service_product_id, responsible_party_type,
       responsibility_amount, allocated_amount, outstanding_amount,
       coverage_percentage, copay_amount, non_covered_amount, status, patient_insurance_id
FROM billing_charge_responsibility WHERE encounter_id = 35
ORDER BY charge_line_id, responsible_party_type;

SELECT 'PAYMENTS' AS section;
SELECT id, payment_number, patient_id, encounter_id, amount, status, created_date
FROM billing_payment WHERE encounter_id = 35 OR patient_id = 14
ORDER BY id;

SELECT 'ALLOC' AS section;
SELECT id, allocation_number, charge_line_id, charge_responsibility_id, patient_service_product_id,
       allocation_source_type, reservation_id, payment_id, allocated_amount, remaining_allocated_amount,
       reversed_amount, status
FROM billing_allocation WHERE encounter_id = 35 ORDER BY id;

SELECT 'RESV' AS section;
SELECT id, reservation_number, payment_id, charge_line_id, patient_service_product_id,
       original_reserved_amount, remaining_reserved_amount, consumed_amount, released_amount, status
FROM billing_reservation WHERE encounter_id = 35 ORDER BY id;

SELECT 'DEBIT' AS section;
SELECT id, current_debit_balance, total_debit_created, available_credit, credit_limit
FROM billing_debit_account WHERE patient_id = 14;
SELECT id, encounter_id, amount, status, transaction_type, created_date
FROM billing_debit_transaction WHERE encounter_id = 35 OR patient_id = 14;

SELECT 'WALLET' AS section;
SELECT id, credited_amount, available_balance, reserved_balance, consumed_amount, refunded_amount, status
FROM billing_wallet WHERE patient_id = 14;

SELECT 'SHARE_SUM' AS section;
SELECT SUM(patient_share_amount) AS sum_patient,
       SUM(insurance_share_amount) AS sum_insurance,
       SUM(net_amount) AS sum_net,
       SUM(gross_amount) AS sum_gross,
       SUM(discount_amount) AS sum_disc,
       SUM(paid_amount) AS sum_paid,
       SUM(remaining_amount) AS sum_remaining
FROM patient_services_and_products
WHERE encounter_id = 35 AND payment_status <> 'CANCELLED';

SELECT 'DIAG_NAMES' AS section;
SELECT id, left(name,80) AS name, code
FROM diagnostic_test
WHERE id IN (148,151,146,153,144,147,139,138)
ORDER BY id;
