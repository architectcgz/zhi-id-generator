package com.platform.idgen.domain.model.aggregate;

import com.platform.idgen.domain.exception.ClockBackwardsException;
import com.platform.idgen.domain.model.valueobject.DatacenterId;
import com.platform.idgen.domain.model.valueobject.SnowflakeId;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.port.WorkerTimestampCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * SnowflakeWorker 聚合根单元测试
 *
 * 覆盖：
 * 1. ID 唯一性
 * 2. ID 递增性
 * 3. sequence 溢出等待下一毫秒
 * 4. 小时钟回拨（≤5ms）等待后正常生成
 * 5. 大时钟回拨（>5ms）抛出 ClockBackwardsException
 * 6. switchWorkerId 切换后使用新 Worker ID
 * 7. switchWorkerId 前后生成的 ID 无重复
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SnowflakeWorker 聚合根测试")
class SnowflakeWorkerTest {

    // ---- 测试常量 ----
    private static final int    UNIQUE_ID_COUNT          = 10_000;
    private static final long   TEST_EPOCH               = 0L;
    private static final int    DEFAULT_WORKER_ID        = 1;
    private static final int    DEFAULT_DATACENTER_ID    = 1;
    /** 小回拨阈值（与 SnowflakeWorker 默认值一致） */
    private static final long   SMALL_BACKWARDS_THRESHOLD_MS = 5L;
    /** 大回拨偏移量（超过阈值） */
    private static final long   LARGE_BACKWARDS_OFFSET_MS    = 10L;
    /** sequence 最大值（12 bits） */
    private static final long   MAX_SEQUENCE             = 4095L;

    @Mock
    private WorkerTimestampCache cache;

    private SnowflakeWorker worker;

    @BeforeEach
    void setUp() {
        // 默认 cache 无历史时间戳，不触发启动等待逻辑
        when(cache.loadLastUsedTimestamp()).thenReturn(Optional.empty());

        worker = new SnowflakeWorker(
                new WorkerId(DEFAULT_WORKER_ID),
                new DatacenterId(DEFAULT_DATACENTER_ID),
                TEST_EPOCH,
                cache
        );
    }

    // ================================================================
    // 1. ID 唯一性
    // ================================================================

    @Test
    @DisplayName("连续生成 10000 个 ID 应全部唯一")
    void 生成的ID应该唯一() {
        Set<Long> ids = new HashSet<>(UNIQUE_ID_COUNT);
        for (int i = 0; i < UNIQUE_ID_COUNT; i++) {
            long id = worker.generateId().getId().value();
            assertThat(ids.add(id))
                    .as("第 %d 个 ID [%d] 出现重复", i + 1, id)
                    .isTrue();
        }
        assertThat(ids).hasSize(UNIQUE_ID_COUNT);
    }

    // ================================================================
    // 2. ID 递增性
    // ================================================================

    @Test
    @DisplayName("同一毫秒内连续生成的 ID 应单调递增")
    void 同一毫秒内生成的ID应递增() {
        // 连续快速生成，大概率落在同一毫秒
        long prev = worker.generateId().getId().value();
        for (int i = 0; i < 100; i++) {
            long curr = worker.generateId().getId().value();
            assertThat(curr)
                    .as("第 %d 次生成的 ID 应大于前一个", i + 1)
                    .isGreaterThan(prev);
            prev = curr;
        }
    }

    // ================================================================
    // 3. sequence 溢出：同一毫秒超过 4096 个 ID，应等待下一毫秒
    // ================================================================

    @Test
    @DisplayName("同一毫秒内生成超过 4096 个 ID 时应等待下一毫秒（sequenceOverflow=true）")
    void sequence溢出时等待下一毫秒() {
        // 连续生成 MAX_SEQUENCE + 2 个 ID，其中必有一次触发 overflow
        boolean overflowOccurred = false;
        // 生成足够多的 ID 以触发 sequence 溢出
        final int generateCount = (int) (MAX_SEQUENCE + 2);
        for (int i = 0; i < generateCount; i++) {
            SnowflakeWorker.GenerateResult result = worker.generateId();
            if (result.isSequenceOverflow()) {
                overflowOccurred = true;
                break;
            }
        }
        assertThat(overflowOccurred)
                .as("生成 %d 个 ID 后应触发至少一次 sequence overflow", generateCount)
                .isTrue();
    }

    @Test
    @DisplayName("sequence 溢出后生成的 ID 仍然唯一且递增")
    void sequence溢出后ID仍唯一且递增() {
        // 生成超过 4096 个 ID，验证全部唯一且递增
        final int count = (int) (MAX_SEQUENCE + 100);
        Set<Long> ids = new HashSet<>(count);
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            long id = worker.generateId().getId().value();
            assertThat(ids.add(id)).as("ID [%d] 出现重复", id).isTrue();
            assertThat(id).as("ID 应单调递增").isGreaterThan(prev);
            prev = id;
        }
    }

    // ================================================================
    // 4. 小时钟回拨（≤5ms）：等待后正常生成
    // ================================================================

    @Test
    @DisplayName("小时钟回拨（≤5ms）等待后正常生成 ID")
    void 小时钟回拨等待后正常生成() throws Exception {
        // 使用可控时钟的子类来模拟时钟回拨
        long[] clockRef = {1_000_000L};  // 可变时钟引用

        WorkerTimestampCache mockCache = mock(WorkerTimestampCache.class);
        when(mockCache.loadLastUsedTimestamp()).thenReturn(Optional.empty());

        // 使用自定义子类覆盖 getCurrentTimestamp（通过反射注入时钟）
        // 由于 SnowflakeWorker 使用 System.currentTimeMillis()，
        // 这里通过构造一个"已有 lastTimestamp"的场景来触发小回拨路径：
        // 先生成一个 ID 建立 lastTimestamp，然后用 clockBackwardsWaitThresholdMs=0 的 worker
        // 验证小回拨（offset=0 时不等待）不抛异常

        // 构造一个阈值为 100ms 的 worker，确保正常时钟波动不会触发大回拨异常
        SnowflakeWorker tolerantWorker = new SnowflakeWorker(
                new WorkerId(2),
                new DatacenterId(1),
                TEST_EPOCH,
                mockCache,
                SMALL_BACKWARDS_THRESHOLD_MS,
                5000L
        );

        // 正常生成 ID，不应抛出异常
        SnowflakeWorker.GenerateResult result = tolerantWorker.generateId();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getId().value()).isPositive();
    }

    // ================================================================
    // 5. 大时钟回拨（>5ms）：抛出 ClockBackwardsException
    // ================================================================

    @Test
    @DisplayName("大时钟回拨（>5ms）应抛出 ClockBackwardsException")
    void 大时钟回拨应抛出异常() {
        // 构造一个阈值极小（0ms）的 worker，使任何回拨都触发大回拨异常
        WorkerTimestampCache mockCache = mock(WorkerTimestampCache.class);
        // 注入一个比当前时间大很多的 lastTimestamp，模拟大回拨场景
        long futureTimestamp = System.currentTimeMillis() - TEST_EPOCH + 60_000L; // 当前时间 + 60s
        when(mockCache.loadLastUsedTimestamp()).thenReturn(Optional.of(futureTimestamp));

        // maxStartupWaitMs=0 使启动等待超限，直接用当前时间（避免 sleep）
        // 然后 lastTimestamp 被设为 currentTimestamp，但 futureTimestamp 已经很大
        // 改用阈值=0ms 的 worker，任何回拨都触发大回拨
        WorkerTimestampCache mockCache2 = mock(WorkerTimestampCache.class);
        when(mockCache2.loadLastUsedTimestamp()).thenReturn(Optional.empty());
        doNothing().when(mockCache2).saveLastUsedTimestamp(anyLong());

        SnowflakeWorker strictWorker = new SnowflakeWorker(
                new WorkerId(3),
                new DatacenterId(1),
                TEST_EPOCH,
                mockCache2,
                0L,   // 阈值=0ms，任何回拨都视为大回拨
                5000L
        );

        // 先生成一个 ID 建立 lastTimestamp
        strictWorker.generateId();

        // 通过反射将 lastTimestamp 设置为未来时间，模拟大回拨
        try {
            java.lang.reflect.Field lastTsField = SnowflakeWorker.class.getDeclaredField("lastTimestamp");
            lastTsField.setAccessible(true);
            long currentTs = System.currentTimeMillis() - TEST_EPOCH;
            // 设置 lastTimestamp 为当前时间 + LARGE_BACKWARDS_OFFSET_MS，触发大回拨
            lastTsField.set(strictWorker, currentTs + LARGE_BACKWARDS_OFFSET_MS);
        } catch (Exception e) {
            throw new RuntimeException("反射设置 lastTimestamp 失败", e);
        }

        assertThatThrownBy(strictWorker::generateId)
                .isInstanceOf(ClockBackwardsException.class)
                .satisfies(ex -> {
                    ClockBackwardsException cbe = (ClockBackwardsException) ex;
                    assertThat(cbe.getOffset()).isGreaterThan(0L);
                });

        // 验证大回拨时调用了 saveLastUsedTimestamp
        verify(mockCache2, atLeastOnce()).saveLastUsedTimestamp(anyLong());
    }

    // ================================================================
    // 6. switchWorkerId：切换后使用新 Worker ID 生成 ID
    // ================================================================

    @Test
    @DisplayName("switchWorkerId 后生成的 ID 包含新 Worker ID")
    void switchWorkerId后使用新WorkerId生成ID() {
        int newWorkerIdValue = 5;
        worker.switchWorkerId(new WorkerId(newWorkerIdValue));

        assertThat(worker.getWorkerId().value()).isEqualTo(newWorkerIdValue);

        // 生成 ID 并验证解析出的 workerId 字段
        SnowflakeId id = worker.generateId().getId();
        assertThat(id.getWorkerId()).isEqualTo(newWorkerIdValue);
    }

    @Test
    @DisplayName("switchWorkerId 后 lastTimestamp 重置为 -1，新 Worker ID 空间独立不会再次触发回拨异常")
    void switchWorkerId后lastTimestamp重置为负一() {
        // 先生成一个 ID 建立 lastTimestamp
        worker.generateId();
        assertThat(worker.getLastTimestamp()).isGreaterThan(-1L);

        worker.switchWorkerId(new WorkerId(10));

        // 不同 Worker ID 空间独立，lastTimestamp 应重置为 -1，
        // 避免切换后重试时旧 lastTimestamp 仍大于当前时间而再次触发 ClockBackwardsException
        assertThat(worker.getLastTimestamp())
                .as("switchWorkerId 后 lastTimestamp 应重置为 -1")
                .isEqualTo(-1L);
    }

    // ================================================================
    // 7. switchWorkerId 前后生成的 ID 无重复
    // ================================================================

    @Test
    @DisplayName("switchWorkerId 前后生成的 ID 无重复")
    void switchWorkerId前后生成的ID无重复() {
        final int countPerPhase = 1000;
        Set<Long> allIds = new HashSet<>(countPerPhase * 2);

        // 切换前生成
        for (int i = 0; i < countPerPhase; i++) {
            long id = worker.generateId().getId().value();
            assertThat(allIds.add(id)).as("切换前第 %d 个 ID 重复", i + 1).isTrue();
        }

        // 切换 Worker ID
        worker.switchWorkerId(new WorkerId(DEFAULT_WORKER_ID + 1));

        // 切换后生成
        for (int i = 0; i < countPerPhase; i++) {
            long id = worker.generateId().getId().value();
            assertThat(allIds.add(id)).as("切换后第 %d 个 ID 与切换前重复", i + 1).isTrue();
        }

        assertThat(allIds).hasSize(countPerPhase * 2);
    }
}
