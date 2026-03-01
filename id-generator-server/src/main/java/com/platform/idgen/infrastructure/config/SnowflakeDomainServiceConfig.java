package com.platform.idgen.infrastructure.config;

import com.platform.idgen.domain.repository.WorkerIdRepository;
import com.platform.idgen.domain.service.SnowflakeDomainService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake 领域服务的 Spring 装配配置。
 * 负责将基础设施层的配置值注入到领域层服务中，保持领域层对基础设施层的解耦。
 *
 * ZooKeeperProperties 为可选依赖：仅在 enable-zookeeper=true 时存在。
 * 禁用 ZK 时，serviceName 回退到 spring.application.name。
 */
@Configuration
public class SnowflakeDomainServiceConfig {

    /**
     * ZK 配置为可选注入，enable-zookeeper=false 时此 Bean 不存在。
     */
    @Autowired(required = false)
    private ZooKeeperProperties zkProperties;

    @Bean
    public SnowflakeDomainService snowflakeDomainService(
            WorkerIdRepository workerIdRepository,
            MeterRegistry meterRegistry,
            SnowflakeProperties snowflakeProperties,
            @Value("${spring.application.name:id-generator}") String applicationName) {
        // ZK 启用时使用 ZK 配置的 serviceName，否则回退到应用名
        String serviceName = (zkProperties != null)
                ? zkProperties.getServiceName()
                : applicationName;
        return new SnowflakeDomainService(
                workerIdRepository,
                meterRegistry,
                snowflakeProperties.getDatacenterId(),
                serviceName,
                snowflakeProperties.getEpoch(),
                snowflakeProperties.getClockBackwards().getAlertThresholdMs(),
                snowflakeProperties.getClockBackwards().getMaxWaitMs(),
                snowflakeProperties.getClockBackwards().getMaxStartupWaitMs()
        );
    }
}
