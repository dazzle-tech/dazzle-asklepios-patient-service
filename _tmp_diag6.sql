\pset pager off

\echo ===== MAPPINGS FOR ENC106 ITEMS =====
SELECT wim.id, wim.item_type, wim.source_id, wim.item_code, left(wim.item_name,70) AS item_name, wim.is_active, wim.sbs_catalog_id
FROM waseel_item_mapping wim
WHERE (wim.item_type = 'SERVICE' AND wim.source_id = 29272)
   OR (wim.item_type = 'LABORATORY' AND wim.source_id IN (138,142,145,143))
   OR (wim.item_type = 'RADIOLOGY' AND wim.source_id = 193)
ORDER BY wim.item_type, wim.source_id, wim.id;

\echo ===== ALL MAPPINGS BY SOURCE regardless of type =====
SELECT wim.id, wim.item_type, wim.source_id, wim.item_code, left(wim.item_name,70) AS item_name, wim.is_active
FROM waseel_item_mapping wim
WHERE wim.source_id IN (29272, 138, 142, 145, 143, 193, 257, 258, 259, 260, 261)
ORDER BY wim.source_id, wim.id;

\echo ===== PRICE LIST ITEMS FOR THESE CODES =====
SELECT pli.id, pli.price_list_setup_id, pli.item_type, pli.source_id, pli.item_code, left(pli.item_name,60) AS item_name,
       pli.unit_price, pli.discount_percentage, pli.is_active, pli.waseel_item_mapping_id, pli.sbs_catalog_id
FROM price_list_setup_item pli
WHERE pli.item_code IN ('Cons003','73100-00-80','73100-08-60','73050-18-50','73100-09-50','57512-03-02')
   OR (pli.item_type = 'RADIOLOGY' AND pli.source_id = 193)
   OR pli.id IN (2684, 691, 730, 445, 735)
ORDER BY pli.item_code, pli.id;

\echo ===== PRICE LIST HEADER =====
SELECT * FROM price_list_setup WHERE id = 1;

\echo ===== DIAGNOSTIC TEST NAMES =====
SELECT id, left(name,80) AS name, code FROM diagnostic_test WHERE id IN (138,142,145,143,193);
