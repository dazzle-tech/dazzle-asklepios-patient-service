\pset pager off

\echo ===== CHARGE =====
SELECT id, patient_id, encounter_id, status, patient_insurance_id,
       gross_amount, discount_amount, exemption_amount, tax_amount, net_amount,
       allocated_amount, outstanding_amount, reserved_amount, line_count
FROM billing_charge WHERE encounter_id = 106;

\echo ===== CHARGE LINES =====
SELECT id, patient_service_product_id, billing_item_type, item_code, left(item_description,50) AS desc,
       quantity, unit_price, gross_amount, discount_amount, net_amount,
       patient_responsibility_amount, insurance_responsibility_amount,
       allocated_amount, outstanding_amount, reserved_amount, status,
       patient_insurance_id, current_pricing_snapshot_id
FROM billing_charge_line WHERE encounter_id = 106 ORDER BY id;

\echo ===== RESPONSIBILITIES =====
SELECT id, charge_line_id, patient_service_product_id, responsible_party_type, responsibility_role,
       responsibility_amount, allocated_amount, outstanding_amount,
       coverage_percentage, deductible_amount, copay_amount, coinsurance_amount, non_covered_amount,
       patient_insurance_id, policy_number, member_number, status
FROM billing_charge_responsibility WHERE encounter_id = 106 ORDER BY charge_line_id, responsible_party_type;

\echo ===== PRICING SNAPSHOTS =====
SELECT id, charge_line_id, patient_service_product_id, pricing_source, price_source,
       price_list_id, price_list_item_id, price_list_name, price_list_item_code,
       base_unit_price, gross_amount, discount_type, discount_rate, discount_amount,
       net_amount, patient_responsibility_amount, insurance_responsibility_amount, status,
       calculation_payload
FROM billing_pricing_snapshot WHERE encounter_id = 106 ORDER BY id;
