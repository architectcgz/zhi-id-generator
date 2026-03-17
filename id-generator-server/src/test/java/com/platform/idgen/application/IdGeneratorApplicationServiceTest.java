package com.platform.idgen.application;

import com.platform.idgen.application.dto.HealthStatus;
import com.platform.idgen.application.dto.SegmentCacheInfo;
import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.model.valueobject.DatacenterId;
import com.platform.idgen.domain.model.valueobject.WorkerId;
import com.platform.idgen.domain.service.SegmentDomainService;
import com.platform.idgen.domain.service.SnowflakeDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IdGeneratorApplicationService 测试")
class IdGeneratorApplicationServiceTest {

    @Test
    @DisplayName("缓存命中时应返回完整的 segment cache 信息")
    void 缓存命中时应返回完整segmentCache信息() {
        SegmentDomainService segmentService = mock(SegmentDomainService.class);
        SnowflakeDomainService snowflakeService = mock(SnowflakeDomainService.class);
        IdGeneratorApplicationService applicationService =
                new IdGeneratorApplicationService(segmentService, snowflakeService);

        when(segmentService.isInitialized()).thenReturn(true);
        when(segmentService.getCacheSnapshot(new BizTag("order"))).thenReturn(Optional.of(
                new SegmentDomainService.SegmentCacheSnapshot(
                        "order",
                        true,
                        0,
                        true,
                        false,
                        100,
                        123456789L,
                        new SegmentDomainService.SegmentStateSnapshot(120L, 200L, 80, 80L),
                        new SegmentDomainService.SegmentStateSnapshot(200L, 280L, 80, 80L)
                )
        ));

        SegmentCacheInfo info = applicationService.getSegmentCacheInfo("order");

        assertThat(info.bizTag()).isEqualTo("order");
        assertThat(info.initialized()).isTrue();
        assertThat(info.cached()).isTrue();
        assertThat(info.bufferInitialized()).isTrue();
        assertThat(info.currentPos()).isEqualTo(0);
        assertThat(info.nextReady()).isTrue();
        assertThat(info.loadingNextSegment()).isFalse();
        assertThat(info.minStep()).isEqualTo(100);
        assertThat(info.updateTimestamp()).isEqualTo(123456789L);
        assertThat(info.currentSegment())
                .extracting(
                        SegmentCacheInfo.SegmentState::value,
                        SegmentCacheInfo.SegmentState::max,
                        SegmentCacheInfo.SegmentState::step,
                        SegmentCacheInfo.SegmentState::idle
                )
                .containsExactly(120L, 200L, 80, 80L);
    }

    @Test
    @DisplayName("缓存未命中时应明确返回 cached=false")
    void 缓存未命中时应返回cachedFalse() {
        SegmentDomainService segmentService = mock(SegmentDomainService.class);
        SnowflakeDomainService snowflakeService = mock(SnowflakeDomainService.class);
        IdGeneratorApplicationService applicationService =
                new IdGeneratorApplicationService(segmentService, snowflakeService);

        when(segmentService.isInitialized()).thenReturn(true);
        when(segmentService.getCacheSnapshot(new BizTag("missing"))).thenReturn(Optional.empty());

        SegmentCacheInfo info = applicationService.getSegmentCacheInfo("missing");

        assertThat(info.bizTag()).isEqualTo("missing");
        assertThat(info.initialized()).isTrue();
        assertThat(info.cached()).isFalse();
        assertThat(info.currentSegment()).isNull();
        assertThat(info.nextSegment()).isNull();
    }

    @Test
    @DisplayName("health 查询应保留客户端依赖的 status 字段并返回结构化子状态")
    void health查询应返回结构化状态() {
        SegmentDomainService segmentService = mock(SegmentDomainService.class);
        SnowflakeDomainService snowflakeService = mock(SnowflakeDomainService.class);
        IdGeneratorApplicationService applicationService =
                new IdGeneratorApplicationService(segmentService, snowflakeService);

        when(segmentService.isInitialized()).thenReturn(true);
        when(segmentService.getAllBizTags()).thenReturn(List.of("order", "payment"));
        when(snowflakeService.isInitialized()).thenReturn(true);
        when(snowflakeService.getWorkerId()).thenReturn(Optional.of(new WorkerId(7)));
        when(snowflakeService.getDatacenterId()).thenReturn(Optional.of(new DatacenterId(3)));

        HealthStatus health = applicationService.getHealthStatus();

        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.service()).isEqualTo("id-generator-service");
        assertThat(health.timestamp()).isPositive();
        assertThat(health.segment().initialized()).isTrue();
        assertThat(health.segment().bizTagCount()).isEqualTo(2);
        assertThat(health.snowflake().initialized()).isTrue();
        assertThat(health.snowflake().workerId()).isEqualTo(7);
        assertThat(health.snowflake().datacenterId()).isEqualTo(3);
    }
}
