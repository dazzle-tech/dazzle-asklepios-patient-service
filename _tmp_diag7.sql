\pset pager off

\echo ===== SBS 57512-03-02 =====
SELECT id, item_code, left(item_name,80) AS item_name, item_type
FROM waseel_sbs_catalog
WHERE item_code = '57512-03-02' OR item_code ILIKE '%57512%';

\echo ===== MAPPINGS FOR 57512-03-02 =====
SELECT * FROM waseel_item_mapping WHERE item_code = '57512-03-02';

\echo ===== RADIOLOGY MAPPINGS / PRICE LIST FOR TEST 193 =====
SELECT * FROM waseel_item_mapping WHERE item_type = 'RADIOLOGY' AND (source_id = 193 OR item_code ILIKE '%57512%' OR item_name ILIKE '%wrist%' OR item_name ILIKE '%hand%');
SELECT id, item_type, source_id, item_code, left(item_name,80), unit_price, discount_percentage, is_active
FROM price_list_setup_item
WHERE item_type = 'RADIOLOGY' AND (source_id = 193 OR item_code ILIKE '%57512%' OR item_name ILIKE '%wrist%' OR item_name ILIKE '%Hand%');

\echo ===== DIAGNOSTIC TEST 193 =====
SELECT * FROM diagnostic_test WHERE id = 193;

\echo ===== ALLOCATIONS ENC 106 =====
SELECT id, allocation_number, charge_line_id, charge_responsibility_id, patient_service_product_id,
       allocation_source_type, reservation_id, payment_id, allocated_amount, remaining_allocated_amount,
       reversed_amount, status, allocation_date
FROM billing_allocation WHERE encounter_id = 106 ORDER BY id;

\echo ===== RESERVATIONS ENC 106 =====
SELECT id, reservation_number, payment_id, charge_line_id, patient_service_product_id,
       original_reserved_amount, remaining_reserved_amount, consumed_amount, released_amount, status
FROM billing_reservation WHERE encounter_id = 106 ORDER BY id;

\echo ===== PAYMENTS ENC 106 =====
SELECT * FROM billing_payment WHERE encounter_id = 106 OR patient_id = 25;
