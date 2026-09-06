\pset pager off

\echo ===== PATIENT =====
SELECT id, medical_record_number, first_name, second_name, last_name, date_of_birth, document_id, sex_at_birth
FROM patients WHERE id = 25;

\echo ===== ENCOUNTER =====
SELECT id, encounter_number, patient_id, facility_id, department_id, practitioner_id, appointment_id,
       encounter_type, encounter_reason, status, encounter_status, billing_status, coverage_type,
       patient_insurance_id, encounter_date, started_date, completed_at, financially_closed_at, financially_closed_by
FROM patient_encounters WHERE id = 106;

\echo ===== ELIGIBILITY REQUESTS FOR PATIENT 25 / ENC 106 =====
SELECT id, encounter_id, patient_insurance_id, api_status, status_code, message,
       eligibility_response_id, request_status, requested_at, responded_at,
       left(response_json, 200) AS response_json_head
FROM waseel_eligibility_request
WHERE patient_id = 25
ORDER BY id DESC;

\echo ===== BILLING ELIGIBILITY SNAPSHOT ENC 106 =====
SELECT *
FROM billing_eligibility_snapshot
WHERE encounter_id = 106 OR patient_id = 25;

\echo ===== BENEFIT RULES FOR INSURANCE 17 =====
SELECT id, patient_insurance_id, benefit_category, item_name, item_code, network_type,
       patient_copayment_percentage, patient_maximum_copayment, maximum_benefit, approval_limit,
       global_default, term, unit
FROM patient_insurance_benefit_rules
WHERE patient_insurance_id = 17
ORDER BY id;

\echo ===== MANUAL COVERAGES =====
SELECT *
FROM patient_insurance_coverages
WHERE insurance_id = 17 OR patient_insurance_id = 17;

\echo ===== PSP ITEMS ENC 106 =====
SELECT id, billing_item_type, service_id, diagnostic_test_id, procedure_id, brand_medication_id,
       service_source, source_id, quantity, unit_price, discount_amount, total_amount,
       gross_amount, net_amount, patient_share_amount, insurance_share_amount,
       paid_amount, remaining_amount, payment_status, coverage_status, not_covered_reason,
       patient_insurance_id, price_source, is_default_service, is_exempted,
       waseel_sbs_mapping_id, waseel_sbs_code, pre_authorization_required, pre_authorization_status,
       created_date, last_modified_date
FROM patient_services_and_products
WHERE encounter_id = 106
ORDER BY id;
