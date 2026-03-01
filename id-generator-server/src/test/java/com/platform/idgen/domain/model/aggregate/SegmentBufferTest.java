package com.platform.idgen.domain.model.aggregate;

import com.platform.idgen.domain.model.valueobject.BizTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SegmentBuffer 聚合根单元测试
 *
 * 覆盖：
 * 1. nextId 基本功能：返回递增 ID
 * 2. 并发 nextId：多线程并发调用，验证 ID 唯一
 * 3. segment 切换：当前 segment 耗尽后切换到下一个
 * 4. segment 耗尽且无备用：验证抛异常
 */
@DisplayName("SegmentBuffer 聚合根测试")
class SegmentBufferTest {

    // ---- 测试常量 ----
    private static final String TEST_BIZ_TAG        = "test-biz";
    private static final int    SEGMENT_STEP        = 100;
    private static final long   SEGMENT_START       = 1L;
    private static final int    CONCURRENT_THREADS  = 20;
    private static final int    IDS_PER_THREAD      = 50;

    private SegmentBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SegmentBuffer(new BizTag(TEST_BIZ_TAG));
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 初始化 buffer 的当前 segment，使其可以正常分配 ID
     *
     * @param startValue segment 起始值（value 初始值）
     * @param maxValue   segment 最大值（exclusive，即 value >= max 时耗尽）
     * @param step       segment 步长
     */
    private void initCurrentSegment(long startValue, long maxValue, int step) {
        SegmentBuffer.Segment seg = buffer.getCurrentSegment();
        seg.getValue().set(startValue);
        seg.setMax(maxValue);
        seg.setStep(step);
        buffer.setInitOk(true);
    }

    /**
     * 初始化 buffer 的下一个 segment（备用 segment）
     */
    private void initNextSegment(long startValue, long maxValue, int step) {
        SegmentBuffer.Segment nextSeg = buffer.getNextSegment();
        nextSeg.getValue().set(startValue);
        nextSeg.setMax(maxValue);
        nextSeg.setStep(step);
        buffer.setNextReady(true);
    }

    // ================================================================
    // 1. nextId 基本功能
    // ================================================================

    @Test
    @DisplayName("nextId 返回递增 ID")
    void nextId返回递增ID() {
        initCurrentSegment(SEGMENT_START, SEGMENT_START + SEGMENT_STEP, SEGMENT_STEP);

        long prev = buffer.nextId().getId();
        for (int i = 0; i < 10; i++) {
            long curr = buffer.nextId().getId();
            assertThat(curr)
                    .as("第 %d 次 nextId 应大于前一个", i + 1)
                    .isGreaterThan(prev);
            prev = curr;
        }
    }

    @Test
    @DisplayName("buffer 未初始化时 nextId 应抛出 IllegalStateException")
    void 未初始化时nextId抛出异常() {
        // buffer 默认 initOk=false
        assertThatThrownBy(() -> buffer.nextId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
    }

    // ================================================================
    // 2. 并发 nextId：多线程并发调用，验证 ID 唯一
    // ================================================================

    @Test
    @DisplayName("多线程并发 nextId 生成的 ID 应全部唯一")
    void 并发nextId生成的ID唯一() throws InterruptedException {
        final int totalIds = CONCURRENT_THREADS * IDS_PER_THREAD;
        // segment 容量足够覆盖所有并发请求
        initCurrentSegment(1L, totalIds + 1L, totalIds);

        Set<Long> ids = ConcurrentHashMap.newKeySet(totalIds);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(CONCURRENT_THREADS);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        try {
            for (int t = 0; t < CONCURRENT_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < IDS_PER_THREAD; i++) {
                            long id = buffer.nextId().getId();
                            if (!ids.add(id)) {
                                errorCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 所有线程同时开始
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
            assertThat(finished).as("并发测试应在 10 秒内完成").isTrue();
        } finally {
            executor.shutdown();
        }

        assertThat(errorCount.get()).as("并发生成过程中不应有错误").isEqualTo(0);
        assertThat(ids).as("所有生成的 ID 应唯一").hasSize(totalIds);
    }

    // ================================================================
    // 3. segment 切换：当前 segment 耗尽后切换到下一个
    // ================================================================

    @Test
    @DisplayName("当前 segment 耗尽后自动切换到备用 segment")
    void 当前segment耗尽后切换到备用segment() {
        // 当前 segment：只有 1 个 ID 可用（value=99, max=100）
        initCurrentSegment(99L, 100L, SEGMENT_STEP);
        // 备用 segment：从 200 开始
        initNextSegment(200L, 300L, SEGMENT_STEP);

        // 消耗当前 segment 最后一个 ID
        SegmentBuffer.NextIdResult result1 = buffer.nextId();
        assertThat(result1.getId()).isEqualTo(99L);
        assertThat(result1.isSegmentSwitched()).isFalse();

        // 下一次调用应触发 segment 切换
        SegmentBuffer.NextIdResult result2 = buffer.nextId();
        assertThat(result2.isSegmentSwitched()).isTrue();
        assertThat(result2.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("segment 切换后继续从新 segment 递增分配")
    void segment切换后继续递增分配() {
        // 当前 segment 只剩 2 个 ID
        initCurrentSegment(98L, 100L, SEGMENT_STEP);
        initNextSegment(200L, 300L, SEGMENT_STEP);

        long id1 = buffer.nextId().getId(); // 98，当前 segment
        long id2 = buffer.nextId().getId(); // 99，当前 segment 最后一个
        long id3 = buffer.nextId().getId(); // 200，切换到备用 segment
        long id4 = buffer.nextId().getId(); // 201，继续从备用 segment 分配

        assertThat(id1).isEqualTo(98L);
        assertThat(id2).isEqualTo(99L);
        assertThat(id3).isEqualTo(200L);
        assertThat(id4).isEqualTo(201L);
    }

    // ================================================================
    // 4. segment 耗尽且无备用：验证抛异常
    // ================================================================

    @Test
    @DisplayName("当前 segment 耗尽且备用未就绪时应抛出 IllegalStateException")
    void segment耗尽且无备用时抛出异常() {
        // 当前 segment 只有 1 个 ID，且没有备用 segment
        initCurrentSegment(99L, 100L, SEGMENT_STEP);
        // nextReady 默认为 false，不设置备用 segment

        // 消耗最后一个 ID
        buffer.nextId();

        // 再次调用应抛出异常
        assertThatThrownBy(() -> buffer.nextId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready");
    }

    @Test
    @DisplayName("switchToNextSegment 在备用未就绪时应抛出 IllegalStateException")
    void switchToNextSegment在备用未就绪时抛出异常() {
        initCurrentSegment(1L, 100L, SEGMENT_STEP);
        // nextReady=false

        assertThatThrownBy(() -> buffer.switchToNextSegment())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready");
    }

    // ================================================================
    // 5. shouldLoadNext 触发逻辑
    // ================================================================

    @Test
    @DisplayName("消耗超过 90% 的 segment 后 shouldLoadNext 应为 true")
    void 消耗超过90percent后shouldLoadNext为true() {
        // step=100，消耗 91 个后剩余 9 < 10（10% of 100），应触发加载
        initCurrentSegment(1L, 101L, SEGMENT_STEP);

        SegmentBuffer.NextIdResult lastResult = null;
        for (int i = 0; i < 91; i++) {
            lastResult = buffer.nextId();
        }

        assertThat(lastResult).isNotNull();
        assertThat(lastResult.isShouldLoadNext())
                .as("消耗超过 90%% 后应触发异步加载")
                .isTrue();
    }

    @Test
    @DisplayName("消耗不足 90% 时 shouldLoadNext 应为 false")
    void 消耗不足90percent时shouldLoadNext为false() {
        // step=100，消耗 10 个后剩余 90 >= 10（10% of 100），不触发加载
        initCurrentSegment(1L, 101L, SEGMENT_STEP);

        SegmentBuffer.NextIdResult lastResult = null;
        for (int i = 0; i < 10; i++) {
            lastResult = buffer.nextId();
        }

        assertThat(lastResult).isNotNull();
        assertThat(lastResult.isShouldLoadNext())
                .as("消耗不足 90%% 时不应触发异步加载")
                .isFalse();
    }
}
