-- Additional test data for development and testing
-- This script runs after schema.sql

-- Insert additional business tags for testing
INSERT INTO leaf_alloc (biz_tag, max_id, step, description)
VALUES 
    ('product', 1, 3000, 'Product ID sequence'),
    ('transaction', 1, 10000, 'Transaction ID sequence'),
    ('session', 1, 5000, 'Session ID sequence'),
    ('event', 1, 20000, 'Event ID sequence'),
    ('notification', 1, 8000, 'Notification ID sequence')
ON CONFLICT (biz_tag) DO NOTHING;

-- Display current state
SELECT biz_tag, max_id, step, description, update_time 
FROM leaf_alloc 
ORDER BY biz_tag;
