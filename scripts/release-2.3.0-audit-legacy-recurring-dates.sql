-- Diagnostic query (read-only) to inspect legacy recurring bill rows that have a purchase_date
-- after the initial month of the fixed_bill template. Review before manually adjusting or clearing.
SELECT b.id, b.user_id, b.bill_name, b.bill_date, b.purchase_date, fb.start_bill_date
FROM bill_table_data b
JOIN fixed_bill fb ON b.fixed_bill_id = fb.id
WHERE b.is_recurrent = true
  AND b.purchase_date IS NOT NULL
  AND b.bill_date <> fb.start_bill_date
ORDER BY b.user_id, b.id;
