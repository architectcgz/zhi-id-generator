package com.platform.idgen.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Segment ID 生成配置项，统一管理 segment 模式的所有参数。
 */
@Component
@ConfigurationProperties(prefix = "id-generator.segment")
@Validated
public class SegmentProperties {

    /** 缓存刷新间隔（秒） */
    @NotNull
    @Min(1)
    private Integer cacheUpdateInterval = 60;

    /** segment 消费时长基准（毫秒），用于动态步长调整 */
    @NotNull
    @Min(1000)
    private Long segmentDuration = 900000L;

    /** 最大步长 */
    @NotNull
    @Min(1)
    private Integer maxStep = 1000000;

    /** 异步更新线程池大小 */
    @NotNull
    @Min(1)
    private Integer updateThreadPoolSize = 5;

    public Integer getCacheUpdateInterval() { return cacheUpdateInterval; }
    public void setCacheUpdateInterval(Integer cacheUpdateInterval) { this.cacheUpdateInterval = cacheUpdateInterval; }

    public Long getSegmentDuration() { return segmentDuration; }
    public void setSegmentDuration(Long segmentDuration) { this.segmentDuration = segmentDuration; }

    public Integer getMaxStep() { return maxStep; }
    public void setMaxStep(Integer maxStep) { this.maxStep = maxStep; }

    public Integer getUpdateThreadPoolSize() { return updateThreadPoolSize; }
    public void setUpdateThreadPoolSize(Integer updateThreadPoolSize) { this.updateThreadPoolSize = updateThreadPoolSize; }
}
