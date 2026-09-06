\pset pager off

SELECT 'CHARGE_FULL' AS section;
SELECT id, charge_number, status, coverage_type, patient_insurance_id,
       gross_amount, discount_amount, net_amount, allocated_amount, outstanding_amount,
       reserved_amount, line_count, created_date, last_modified_date
FROM billing_charge WHERE encounter_id = 106;

SELECT 'ALLOCATIONS' AS section;
SELECT id, allocation_number, charge_line_id, charge_responsibility_id, patient_service_product_id,
       allocation_source_type, reservation_id, payment_id, allocated_amount, remaining_allocated_amount,
       reversed_amount, status
FROM billing_allocation WHERE encounter_id = 106 ORDER BY id;

SELECT 'RESERVATIONS' AS section;
SELECT id, reservation_number, payment_id, charge_line_id, patient_service_product_id,
       original_reserved_amount, remaining_reserved_amount, consumed_amount, released_amount, status
FROM billing_reservation WHERE encounter_id = 106 ORDER BY id;

SELECT 'PAYMENTS' AS section;
SELECT id, payment_number, patient_id, encounter_id, amount, status, payment_method, created_date
FROM billing_payment WHERE encounter_id = 106 OR (patient_id = 25 AND created_date::date BETWEEN '2026-08-30' AND '2026-09-04')
ORDER BY id;

SELECT 'DEBIT' AS section;
SELECT * FROM billing_debit_account WHERE patient_id = 25;
SELECT id, encounter_id, amount, status, transaction_type, created_date
FROM billing_debit_transaction WHERE encounter_id = 106 OR patient_id = 25;

SELECT 'WALLET' AS section;
SELECT * FROM billing_wallet WHERE patient_id = 25;

SELECT 'MAPPINGS' AS section;
SELECT wim.id, wim.item_type, wim.source_id, wim.item_code, left(wim.item_name,70) AS item_name, wim.is_active
FROM waseel_item_mapping wim
WHERE (wim.item_type = 'SERVICE' AND wim.source_id = 29272)
   OR (wim.item_type = 'LABORATORY' AND wim.source_id IN (138,142,145,143))
   OR (wim.item_type = 'RADIOLOGY' AND wim.source_id = 193)
ORDER BY wim.item_type, wim.source_id, wim.id;

SELECT 'PRICE_LIST_ITEMS' AS section;
SELECT pli.id, pli.price_list_setup_id, pli.item_type, pli.source_id, pli.item_code, left(pli.item_name,60) AS item_name,
       pli.unit_price, pli.discount_percentage, pli.is_active, pli.waseel_item_mapping_id
FROM price_list_setup_item pli
WHERE pli.price_list_setup_id = 1
  AND (
    pli.item_code IN ('Cons003','73100-00-80','73100-08-60','73050-18-50','73100-09-50','57512-03-02')
    OR (pli.item_type = 'RADIOLOGY' AND pli.source_id = 193)
    OR (pli.item_type = 'LABORATORY' AND pli.source_id IN (138,142,145,143))
    OR (pli.item_type = 'SERVICE' AND pli.source_id = 29272)
  )
ORDER BY pli.item_code, pli.id;

SELECT 'DIAG_NAMES' AS section;
SELECT id, left(name,80) AS name, code FROM diagnostic_test WHERE id IN (138,142,145,143,193);

SELECT 'PSP_SHARE_SUM' AS section;
SELECT SUM(patient_share_amount) AS sum_patient,
       SUM(insurance_share_amount) AS sum_insurance,
       SUM(net_amount) AS sum_net
FROM patient_services_and_products
WHERE encounter_id = 106 AND payment_status <> 'CANCELLED';

SELECT 'ELIG_JSON_MAX' AS section;
SELECT id,
       max_limit,
       patient_share,
       eligibility_status,
       inforce,
       site_eligibility,
       left(eligibility_benefits_json, 800) AS benefits_head
FROM patient_insurances WHERE id = 17;
