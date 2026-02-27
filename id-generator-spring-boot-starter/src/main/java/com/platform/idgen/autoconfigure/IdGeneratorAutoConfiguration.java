package com.platform.idgen.autoconfigure;

import com.platform.idgen.client.BufferedIdGeneratorClient;
import com.platform.idgen.client.IdGeneratorClient;
import com.platform.idgen.client.config.IdGeneratorClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for ID Generator Client.
 * 
 * Automatically configures an IdGeneratorClient bean when:
 * - The IdGeneratorClient class is on the classpath
 * - No existing IdGeneratorClient bean is defined
 * - The property id-generator.client.enabled is not set to false
 * 
 * Usage in application.yml:
 * <pre>
 * id-generator:
 *   client:
 *     server-url: http://localhost:8010
 *     buffer-size: 100
 *     async-refill: true
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(IdGeneratorClient.class)
@EnableConfigurationProperties(IdGeneratorProperties.class)
@ConditionalOnProperty(prefix = "id-generator.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdGeneratorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdGeneratorAutoConfiguration.class);

    /**
     * Create an IdGeneratorClient bean if none exists.
     * 
     * @param properties the configuration properties
     * @return the IdGeneratorClient bean
     */
    @Bean
    @ConditionalOnMissingBean
    public IdGeneratorClient idGeneratorClient(IdGeneratorProperties properties) {
        log.info("Creating IdGeneratorClient with server URL: {}", properties.getServerUrl());
        
        IdGeneratorClientConfig config = IdGeneratorClientConfig.builder()
            .serverUrl(properties.getServerUrl())
            .connectTimeoutMs(properties.getConnectTimeoutMs())
            .readTimeoutMs(properties.getReadTimeoutMs())
            .maxRetries(properties.getMaxRetries())
            .bufferSize(properties.getBufferSize())
            .refillThreshold(properties.getRefillThreshold())
            .batchFetchSize(properties.getBatchFetchSize())
            .asyncRefill(properties.isAsyncRefill())
            .bufferEnabled(properties.isBufferEnabled())
            .build();

        return new BufferedIdGeneratorClient(config);
    }
}
