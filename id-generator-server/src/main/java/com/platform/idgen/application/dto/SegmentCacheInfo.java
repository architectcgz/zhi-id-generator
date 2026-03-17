package com.platform.idgen.application.dto;

/**
 * Segment 缓存信息查询结果。
 */
public record SegmentCacheInfo(
        String bizTag,
        boolean initialized,
        boolean cached,
        Boolean bufferInitialized,
        Integer currentPos,
        Boolean nextReady,
        Boolean loadingNextSegment,
        Integer minStep,
        Long updateTimestamp,
        SegmentState currentSegment,
        SegmentState nextSegment) {

    public record SegmentState(long value, long max, int step, long idle) {}
}
