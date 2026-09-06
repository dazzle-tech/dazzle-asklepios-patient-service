\pset pager off

SELECT 'ELIGIBILITY' AS section;
SELECT id, encounter_id, patient_insurance_id, api_status, status_code, message,
       eligibility_response_id, request_status, requested_at, responded_at
FROM waseel_eligibility_request
WHERE patient_id = 25
ORDER BY id DESC;

SELECT 'SNAPSHOT' AS section;
SELECT id, encounter_id, patient_id, patient_insurance_id, waseel_eligibility_request_id,
       eligibility_response_id, created_date
FROM billing_eligibility_snapshot
WHERE encounter_id = 106 OR patient_id = 25;

SELECT 'BENEFIT_RULES' AS section;
SELECT id, benefit_category, item_name, item_code, network_type,
       patient_copayment_percentage, patient_maximum_copayment, maximum_benefit, approval_limit,
       is_global_default, eligibility_request_id
FROM patient_insurance_benefit_rules
WHERE patient_insurance_id = 17
ORDER BY is_global_default DESC, id;

SELECT 'PSP' AS section;
SELECT id, billing_item_type, service_id, diagnostic_test_id, procedure_id,
       service_source, source_id, quantity, unit_price, discount_amount,
       gross_amount, net_amount, patient_share_amount, insurance_share_amount,
       paid_amount, remaining_amount, payment_status, coverage_status, not_covered_reason,
       patient_insurance_id, price_source, is_default_service,
       waseel_sbs_mapping_id, waseel_sbs_code, created_date
FROM patient_services_and_products
WHERE encounter_id = 106
ORDER BY id;

SELECT 'FINANCIAL_DOCS' AS section;
SELECT id, document_number, document_type, document_subtype, status, total_amount,
       eligibility_reference, claim_reference, created_date
FROM financial_documents WHERE encounter_id = 106 ORDER BY id;

SELECT 'FD_ITEMS' AS section;
SELECT f.id, f.document_id, d.document_subtype, f.patient_service_product_id, f.billing_charge_line_id,
       f.item_code, left(f.item_description, 60) AS item_description,
       f.unit_price, f.gross_amount, f.discount_amount, f.net_amount,
       f.patient_share_amount, f.insurance_share_amount,
       f.paid_amount, f.remaining_amount, f.insurance_paid_amount, f.insurance_remaining_amount, f.status
FROM financial_document_items f
JOIN financial_documents d ON d.id = f.document_id
WHERE d.encounter_id = 106
ORDER BY d.document_subtype, f.id;

SELECT 'CHARGE' AS section;
SELECT id, patient_id, encounter_id, status, coverage_type, patient_insurance_id,
       gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount, line_count
FROM billing_charge WHERE encounter_id = 106;

SELECT 'CHARGE_LINES' AS section;
SELECT id, patient_service_product_id, billing_item_type, item_code, left(item_description,50) AS descr,
       quantity, unit_price, gross_amount, discount_amount, net_amount,
       patient_responsibility_amount, insurance_responsibility_amount,
       allocated_amount, outstanding_amount, reserved_amount, status,
       patient_insurance_id, current_pricing_snapshot_id
FROM billing_charge_line WHERE encounter_id = 106 ORDER BY id;

SELECT 'RESPONSIBILITIES' AS section;
SELECT id, charge_line_id, patient_service_product_id, responsible_party_type, responsibility_role,
       responsibility_amount, allocated_amount, outstanding_amount,
       coverage_percentage, deductible_amount, copay_amount, coinsurance_amount, non_covered_amount,
       patient_insurance_id, policy_number, member_number, status
FROM billing_charge_responsibility WHERE encounter_id = 106 ORDER BY charge_line_id, responsible_party_type;

SELECT 'SNAPSHOTS' AS section;
SELECT id, charge_line_id, patient_service_product_id, pricing_source, price_source,
       price_list_id, price_list_item_id, price_list_name, price_list_item_code,
       base_unit_price, gross_amount, discount_type, discount_rate, discount_amount,
       net_amount, patient_responsibility_amount, insurance_responsibility_amount, status,
       calculation_payload
FROM billing_pricing_snapshot WHERE encounter_id = 106 ORDER BY id;
