package com.platform.idgen.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WorkerId 值对象单元测试
 */
@DisplayName("WorkerId 值对象测试")
class WorkerIdTest {

    // ---- 有效值边界 ----
    private static final int VALID_MIN = WorkerId.MIN_VALUE;   // 0
    private static final int VALID_MAX = WorkerId.MAX_VALUE;   // 31
    private static final int MODULO_BASE = VALID_MAX + 1;      // 32

    @Test
    @DisplayName("最小有效值 0 创建成功")
    void 最小有效值创建成功() {
        WorkerId workerId = new WorkerId(VALID_MIN);
        assertThat(workerId.value()).isEqualTo(VALID_MIN);
    }

    @Test
    @DisplayName("最大有效值 31 创建成功")
    void 最大有效值创建成功() {
        WorkerId workerId = new WorkerId(VALID_MAX);
        assertThat(workerId.value()).isEqualTo(VALID_MAX);
    }

    @ParameterizedTest(name = "有效值 {0} 创建成功")
    @ValueSource(ints = {0, 1, 15, 16, 30, 31})
    @DisplayName("有效范围内的值均可创建成功")
    void 有效范围内的值均可创建成功(int value) {
        WorkerId workerId = new WorkerId(value);
        assertThat(workerId.value()).isEqualTo(value);
    }

    @ParameterizedTest(name = "无效值 {0} 应抛出 IllegalArgumentException")
    @ValueSource(ints = {-1, 32, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("无效值应抛出 IllegalArgumentException")
    void 无效值应抛出异常(int invalidValue) {
        assertThatThrownBy(() -> new WorkerId(invalidValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WorkerId must be between");
    }

    @Test
    @DisplayName("fromSequenceNumber 正向序号正确映射到 [0,31]")
    void fromSequenceNumber_正向序号正确映射() {
        // 序号 0 -> workerId 0
        assertThat(WorkerId.fromSequenceNumber(0).value()).isEqualTo(0);
        // 序号 31 -> workerId 31
        assertThat(WorkerId.fromSequenceNumber(VALID_MAX).value()).isEqualTo(VALID_MAX);
        // 序号 32 -> workerId 0（回绕）
        assertThat(WorkerId.fromSequenceNumber(MODULO_BASE).value()).isEqualTo(0);
        // 序号 63 -> workerId 31
        assertThat(WorkerId.fromSequenceNumber(MODULO_BASE + VALID_MAX).value()).isEqualTo(VALID_MAX);
        // 序号 100 -> 100 % 32 = 4
        assertThat(WorkerId.fromSequenceNumber(100).value()).isEqualTo(100 % MODULO_BASE);
    }

    @Test
    @DisplayName("fromSequenceNumber 负序号结果仍在 [0,31] 范围内")
    void fromSequenceNumber_负序号结果非负() {
        // Java 取模对负数可能返回负值，fromSequenceNumber 应修正为非负
        for (int seq = -MODULO_BASE; seq < 0; seq++) {
            WorkerId workerId = WorkerId.fromSequenceNumber(seq);
            assertThat(workerId.value())
                    .as("序号 %d 对应的 workerId 应在 [0,31]", seq)
                    .isBetween(VALID_MIN, VALID_MAX);
        }
    }

    @Test
    @DisplayName("WorkerId 是值对象，相同值的实例相等")
    void 相同值的实例相等() {
        WorkerId a = new WorkerId(5);
        WorkerId b = new WorkerId(5);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
