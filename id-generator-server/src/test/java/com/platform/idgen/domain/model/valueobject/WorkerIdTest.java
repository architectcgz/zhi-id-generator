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
    @DisplayName("WorkerId 是值对象，相同值的实例相等")
    void 相同值的实例相等() {
        WorkerId a = new WorkerId(5);
        WorkerId b = new WorkerId(5);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
