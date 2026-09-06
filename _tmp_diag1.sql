\pset pager off
\pset format aligned
\pset tuples_only off

\echo ===== PATIENT =====
SELECT id, first_name, last_name, mrn, date_of_birth
FROM patients WHERE id = 25;

\echo ===== ENCOUNTER =====
SELECT id, patient_id, coverage_type, patient_insurance_id, billing_status, status,
       start_date, end_date, encounter_type, created_date, last_modified_date
FROM patient_encounters WHERE id = 106 AND patient_id = 25;

\echo ===== PATIENT INSURANCE =====
SELECT id, patient_id, payor_id, plan_id, policy_number, member_card_id, payer_nphies_id,
       payer_name, tpa_name, tpa_nphies_id, network_id, coverage_type, policy_class_name,
       patient_share, max_limit, eligibility_status, site_eligibility, inforce,
       gp_visit_copay, specialist_visits_limit, default_copayment_percent,
       last_eligibility_request_id, last_eligibility_synced_at, is_primary,
       remaining_benefits, remaining_deductibles, issue_date, expiration_date,
       waseel_new_plan, group_number, group_name, plan_code
FROM patient_insurances
WHERE patient_id = 25
ORDER BY is_primary DESC, id;

\echo ===== ENCOUNTER INSURANCE LINK =====
SELECT pe.id AS encounter_id, pe.coverage_type, pe.patient_insurance_id,
       pi.policy_number, pi.member_card_id, pi.max_limit, pi.patient_share,
       pi.eligibility_status, pi.inforce, pi.payer_name
FROM patient_encounters pe
LEFT JOIN patient_insurances pi ON pi.id = pe.patient_insurance_id
WHERE pe.id = 106;

