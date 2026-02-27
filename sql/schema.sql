-- ID Generator Database Schema
-- Database: id_generator

-- Segment Mode: Leaf Alloc Table
CREATE TABLE IF NOT EXISTS leaf_alloc (
    biz_tag VARCHAR(128) PRIMARY KEY,
    max_id BIGINT NOT NULL DEFAULT 1,
    step INT NOT NULL DEFAULT 1000,
    description VARCHAR(256),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0  -- For optimistic locking
);

CREATE INDEX IF NOT EXISTS idx_leaf_alloc_update_time ON leaf_alloc(update_time);

-- Insert some default business tags
INSERT INTO leaf_alloc (biz_tag, max_id, step, description)
VALUES 
    ('default', 1, 1000, 'Default business tag'),
    ('user', 1, 2000, 'User ID sequence'),
    ('order', 1, 5000, 'Order ID sequence'),
    ('message', 1, 10000, 'Message ID sequence')
ON CONFLICT (biz_tag) DO NOTHING;

-- Add comment
COMMENT ON TABLE leaf_alloc IS 'Segment mode ID allocation table';
COMMENT ON COLUMN leaf_alloc.biz_tag IS 'Business tag identifier';
COMMENT ON COLUMN leaf_alloc.max_id IS 'Current maximum allocated ID';
COMMENT ON COLUMN leaf_alloc.step IS 'Allocation step size';
COMMENT ON COLUMN leaf_alloc.version IS 'Optimistic locking version';
