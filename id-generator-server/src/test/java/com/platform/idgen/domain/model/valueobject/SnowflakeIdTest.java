package com.platform.idgen.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SnowflakeId 值对象单元测试
 *
 * 验证 64 位 ID 各字段的解析正确性：
 * - 41 bits: timestamp
 * - 5 bits:  datacenter ID
 * - 5 bits:  worker ID
 * - 12 bits: sequence
 */
@DisplayName("SnowflakeId 值对象测试")
class SnowflakeIdTest {

    // 位域常量（与 SnowflakeId 内部保持一致）
    private static final long SEQUENCE_BITS       = 12L;
    private static final long WORKER_ID_BITS      = 5L;
    private static final long DATACENTER_ID_BITS  = 5L;

    private static final long WORKER_ID_SHIFT     = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT     = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long MAX_SEQUENCE        = ~(-1L << SEQUENCE_BITS);        // 4095
    private static final long MAX_WORKER_ID       = ~(-1L << WORKER_ID_BITS);       // 31
    private static final long MAX_DATACENTER_ID   = ~(-1L << DATACENTER_ID_BITS);   // 31

    /** 测试用 epoch（任意固定值，用于验证 getTimestamp 偏移计算） */
    private static final long TEST_EPOCH = 1_700_000_000_000L;

    // ---- 辅助方法 ----

    /**
     * 手动组装一个 Snowflake ID，用于构造已知字段的测试数据
     */
    private long compose(long timestamp, long datacenterId, long workerId, long sequence) {
        return (timestamp << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    // ---- 测试用例 ----

    @Test
    @DisplayName("解析 workerId 字段正确")
    void 解析workerId字段正确() {
        long rawId = compose(1000L, 3L, 7L, 100L);
        SnowflakeId id = new SnowflakeId(rawId);
        assertThat(id.getWorkerId()).isEqualTo(7);
    }

    @Test
    @DisplayName("解析 datacenterId 字段正确")
    void 解析datacenterId字段正确() {
        long rawId = compose(1000L, 15L, 7L, 100L);
        SnowflakeId id = new SnowflakeId(rawId);
        assertThat(id.getDatacenterId()).isEqualTo(15);
    }

    @Test
    @DisplayName("解析 sequence 字段正确")
    void 解析sequence字段正确() {
        long rawId = compose(1000L, 3L, 7L, 2048L);
        SnowflakeId id = new SnowflakeId(rawId);
        assertThat(id.getSequence()).isEqualTo(2048);
    }

    @Test
    @DisplayName("解析 timestamp 字段正确（含 epoch 偏移）")
    void 解析timestamp字段正确() {
        long relativeTs = 500_000L; // 相对 epoch 的毫秒数
        long rawId = compose(relativeTs, 1L, 1L, 0L);
        SnowflakeId id = new SnowflakeId(rawId);
        assertThat(id.getTimestamp(TEST_EPOCH)).isEqualTo(TEST_EPOCH + relativeTs);
    }

    @Test
    @DisplayName("所有字段同时解析正确")
    void 所有字段同时解析正确() {
        long relativeTs   = 123_456L;
        long datacenterId = 20L;
        long workerId     = 15L;
        long sequence     = 999L;

        long rawId = compose(relativeTs, datacenterId, workerId, sequence);
        SnowflakeId id = new SnowflakeId(rawId);

        assertThat(id.getTimestamp(TEST_EPOCH)).isEqualTo(TEST_EPOCH + relativeTs);
        assertThat(id.getDatacenterId()).isEqualTo((int) datacenterId);
        assertThat(id.getWorkerId()).isEqualTo((int) workerId);
        assertThat(id.getSequence()).isEqualTo((int) sequence);
    }

    @Test
    @DisplayName("边界值：所有字段取最大值时解析正确")
    void 边界值_所有字段取最大值() {
        long maxTs = ~(-1L << 41);  // 41 bits 最大值
        long rawId = compose(maxTs, MAX_DATACENTER_ID, MAX_WORKER_ID, MAX_SEQUENCE);
        SnowflakeId id = new SnowflakeId(rawId);

        assertThat(id.getDatacenterId()).isEqualTo((int) MAX_DATACENTER_ID);
        assertThat(id.getWorkerId()).isEqualTo((int) MAX_WORKER_ID);
        assertThat(id.getSequence()).isEqualTo((int) MAX_SEQUENCE);
    }

    @Test
    @DisplayName("边界值：所有字段取最小值（0）时解析正确")
    void 边界值_所有字段取最小值() {
        long rawId = compose(0L, 0L, 0L, 0L);
        SnowflakeId id = new SnowflakeId(rawId);

        assertThat(id.getTimestamp(TEST_EPOCH)).isEqualTo(TEST_EPOCH);
        assertThat(id.getDatacenterId()).isEqualTo(0);
        assertThat(id.getWorkerId()).isEqualTo(0);
        assertThat(id.getSequence()).isEqualTo(0);
    }

    @Test
    @DisplayName("SnowflakeId 是值对象，相同 value 的实例相等")
    void 相同value的实例相等() {
        long rawId = compose(1000L, 5L, 10L, 42L);
        SnowflakeId a = new SnowflakeId(rawId);
        SnowflakeId b = new SnowflakeId(rawId);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
