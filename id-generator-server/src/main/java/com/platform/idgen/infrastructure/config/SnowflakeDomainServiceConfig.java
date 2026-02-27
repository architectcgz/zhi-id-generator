package com.platform.idgen.infrastructure.config;

import com.platform.idgen.domain.repository.WorkerIdRepository;
import com.platform.idgen.domain.service.SnowflakeDomainService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake 领域服务的 Spring 装配配置。
 * 负责将基础设施层的配置值注入到领域层服务中，保持领域层对基础设施层的解耦。
 */
@Configuration
public class SnowflakeDomainServiceConfig {

    @Bean
    public SnowflakeDomainService snowflakeDomainService(
            WorkerIdRepository workerIdRepository,
            MeterRegistry meterRegistry,
            SnowflakeProperties snowflakeProperties,
            ZooKeeperProperties zkProperties) {
        return new SnowflakeDomainService(
                workerIdRepository,
                meterRegistry,
                snowflakeProperties.getDatacenterId(),
                zkProperties.getServiceName(),
                snowflakeProperties.getEpoch(),
                snowflakeProperties.getClockBackwards().getAlertThresholdMs(),
                snowflakeProperties.getClockBackwards().getMaxWaitMs(),
                snowflakeProperties.getClockBackwards().getMaxStartupWaitMs()
        );
    }
}
