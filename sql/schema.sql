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

-- ============================================================
-- Snowflake Mode: Worker ID 分配表（替代 ZooKeeper）
-- 预填 0-31 共 32 行，通过 SELECT ... FOR UPDATE SKIP LOCKED 抢占
-- ============================================================
CREATE TABLE IF NOT EXISTS worker_id_alloc (
    worker_id   INTEGER     PRIMARY KEY CHECK (worker_id >= 0 AND worker_id <= 31), -- Worker ID，取值范围 0-31
    instance_id VARCHAR(64) NOT NULL DEFAULT '',     -- 实例标识，格式为 IP:port
    lease_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 租约时间，用于过期回收
    status      VARCHAR(16) NOT NULL DEFAULT 'released' CHECK (status IN ('active', 'released')) -- 状态：active / released
);

COMMENT ON TABLE worker_id_alloc IS 'Snowflake Worker ID 分配表，用于数据库方式分配 Worker ID';
COMMENT ON COLUMN worker_id_alloc.worker_id IS 'Worker ID，取值 0-31，对应 Snowflake 算法的 worker 位';
COMMENT ON COLUMN worker_id_alloc.instance_id IS '持有该 Worker ID 的实例标识，格式为 IP:port';
COMMENT ON COLUMN worker_id_alloc.lease_time IS '最近一次租约续期时间，超过阈值视为过期可回收';
COMMENT ON COLUMN worker_id_alloc.status IS '分配状态：active 表示已被占用，released 表示空闲可分配';

-- 预填 0-31 共 32 个 Worker ID
INSERT INTO worker_id_alloc (worker_id, instance_id, lease_time, status)
VALUES
    (0,  '', CURRENT_TIMESTAMP, 'released'),
    (1,  '', CURRENT_TIMESTAMP, 'released'),
    (2,  '', CURRENT_TIMESTAMP, 'released'),
    (3,  '', CURRENT_TIMESTAMP, 'released'),
    (4,  '', CURRENT_TIMESTAMP, 'released'),
    (5,  '', CURRENT_TIMESTAMP, 'released'),
    (6,  '', CURRENT_TIMESTAMP, 'released'),
    (7,  '', CURRENT_TIMESTAMP, 'released'),
    (8,  '', CURRENT_TIMESTAMP, 'released'),
    (9,  '', CURRENT_TIMESTAMP, 'released'),
    (10, '', CURRENT_TIMESTAMP, 'released'),
    (11, '', CURRENT_TIMESTAMP, 'released'),
    (12, '', CURRENT_TIMESTAMP, 'released'),
    (13, '', CURRENT_TIMESTAMP, 'released'),
    (14, '', CURRENT_TIMESTAMP, 'released'),
    (15, '', CURRENT_TIMESTAMP, 'released'),
    (16, '', CURRENT_TIMESTAMP, 'released'),
    (17, '', CURRENT_TIMESTAMP, 'released'),
    (18, '', CURRENT_TIMESTAMP, 'released'),
    (19, '', CURRENT_TIMESTAMP, 'released'),
    (20, '', CURRENT_TIMESTAMP, 'released'),
    (21, '', CURRENT_TIMESTAMP, 'released'),
    (22, '', CURRENT_TIMESTAMP, 'released'),
    (23, '', CURRENT_TIMESTAMP, 'released'),
    (24, '', CURRENT_TIMESTAMP, 'released'),
    (25, '', CURRENT_TIMESTAMP, 'released'),
    (26, '', CURRENT_TIMESTAMP, 'released'),
    (27, '', CURRENT_TIMESTAMP, 'released'),
    (28, '', CURRENT_TIMESTAMP, 'released'),
    (29, '', CURRENT_TIMESTAMP, 'released'),
    (30, '', CURRENT_TIMESTAMP, 'released'),
    (31, '', CURRENT_TIMESTAMP, 'released')
ON CONFLICT (worker_id) DO NOTHING;
