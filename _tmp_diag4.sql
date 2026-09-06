\pset pager off

\echo ===== BENEFIT RULES INS 17 =====
SELECT id, benefit_category, item_name, item_code, network_type,
       patient_copayment_percentage, patient_maximum_copayment, maximum_benefit, approval_limit,
       is_global_default, eligibility_request_id
FROM patient_insurance_benefit_rules
WHERE patient_insurance_id = 17
ORDER BY is_global_default DESC, id;

\echo ===== MANUAL COVERAGES INS 17 =====
SELECT * FROM patient_insurance_coverages WHERE insurance_id = 17;

\echo ===== INSURANCE ELIGIBILITY JSON HEAD =====
SELECT id, max_limit, patient_share, eligibility_status, inforce, site_eligibility,
       last_eligibility_request_id, left(eligibility_benefits_json, 500) AS benefits_head
FROM patient_insurances WHERE id = 17;

\echo ===== FINANCIAL DOCS ENC 106 =====
SELECT id, document_number, document_type, document_subtype, status, total_amount,
       eligibility_reference, claim_reference, created_date, last_modified_date, created_by
FROM financial_documents WHERE encounter_id = 106 ORDER BY id;

\echo ===== FINANCIAL DOC ITEMS ENC 106 =====
SELECT f.id, f.document_id, d.document_subtype, f.patient_service_product_id, f.billing_charge_line_id,
       f.item_code, left(f.item_description, 60) AS item_description,
       f.unit_price, f.gross_amount, f.discount_amount, f.net_amount,
       f.patient_share_amount, f.insurance_share_amount,
       f.paid_amount, f.remaining_amount, f.insurance_paid_amount, f.insurance_remaining_amount, f.status
FROM financial_document_items f
JOIN financial_documents d ON d.id = f.document_id
WHERE d.encounter_id = 106
ORDER BY d.document_subtype, f.id;

\echo ===== BILLING CHARGE ENC 106 =====
SELECT id, patient_id, encounter_id, status, coverage_type, patient_insurance_id,
       gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount, line_count,
       created_date, last_modified_date
FROM billing_charge WHERE encounter_id = 106;
