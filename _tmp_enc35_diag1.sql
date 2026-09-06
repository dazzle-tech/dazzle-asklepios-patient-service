\pset pager off

SELECT 'PATIENT' AS section;
SELECT id, medical_record_number, first_name, second_name, last_name, date_of_birth
FROM patients WHERE id = 14;

SELECT 'ENCOUNTER' AS section;
SELECT id, encounter_number, patient_id, status, encounter_status, billing_status, coverage_type,
       patient_insurance_id, encounter_date, started_date, completed_at, financially_closed_at
FROM patient_encounters WHERE id = 35;

SELECT 'INSURANCE' AS section;
SELECT id, patient_id, payor_id, plan_id, policy_number, member_card_id, payer_nphies_id,
       payer_name, tpa_name, network_id, coverage_type, policy_class_name,
       patient_share, max_limit, eligibility_status, site_eligibility, inforce,
       gp_visit_copay, specialist_visits_limit, default_copayment_percent,
       last_eligibility_request_id, last_eligibility_synced_at, is_primary,
       remaining_benefits, waseel_new_plan, group_number, group_name, plan_code,
       issue_date, expiration_date
FROM patient_insurances WHERE patient_id = 14 ORDER BY is_primary DESC, id;

SELECT 'ELIGIBILITY' AS section;
SELECT id, encounter_id, patient_insurance_id, api_status, status_code, message,
       eligibility_response_id, request_status, requested_at, responded_at
FROM waseel_eligibility_request
WHERE patient_id = 14
ORDER BY id DESC;

SELECT 'SNAPSHOT' AS section;
SELECT id, encounter_id, patient_id, patient_insurance_id, waseel_eligibility_request_id,
       eligibility_response_id, created_date
FROM billing_eligibility_snapshot
WHERE encounter_id = 35 OR patient_id = 14;

SELECT 'PSP' AS section;
SELECT id, billing_item_type, service_id, diagnostic_test_id, procedure_id,
       service_source, source_id, quantity, unit_price, discount_amount,
       gross_amount, net_amount, patient_share_amount, insurance_share_amount,
       paid_amount, remaining_amount, payment_status, coverage_status, not_covered_reason,
       patient_insurance_id, price_source, is_default_service, is_exempted,
       waseel_sbs_mapping_id, waseel_sbs_code, created_date
FROM patient_services_and_products
WHERE encounter_id = 35
ORDER BY created_date, id;
