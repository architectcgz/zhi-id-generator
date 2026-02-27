package com.platform.idgen.infrastructure.config;

import com.platform.idgen.domain.repository.LeafAllocRepository;
import com.platform.idgen.domain.service.SegmentDomainService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Segment 领域服务的 Spring 装配配置。
 */
@Configuration
public class SegmentDomainServiceConfig {

    @Bean
    public SegmentDomainService segmentDomainService(
            LeafAllocRepository leafAllocRepository,
            MeterRegistry meterRegistry,
            SegmentProperties segmentProperties) {
        return new SegmentDomainService(
                leafAllocRepository,
                meterRegistry,
                segmentProperties.getCacheUpdateInterval(),
                segmentProperties.getSegmentDuration(),
                segmentProperties.getMaxStep(),
                segmentProperties.getUpdateThreadPoolSize()
        );
    }
}
