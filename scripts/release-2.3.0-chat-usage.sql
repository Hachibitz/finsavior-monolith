-- Apply once to the correct MySQL database if Hibernate has not added this column.
-- Existing rows remain NULL because their payment method cannot be reconstructed.
ALTER TABLE chat_message_history ADD COLUMN paid_with_coins BOOLEAN NULL;
