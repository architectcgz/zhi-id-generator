package com.platform.idgen.domain.model.valueobject;

/**
 * 号段分配结果值对象（领域层），用于隔离领域层对基础设施层持久化实体的依赖。
 * 由 Repository 实现层负责从数据库实体转换为此对象。
 */
public class SegmentAllocation {

    private final String bizTag;
    private final long maxId;
    private final int step;

    public SegmentAllocation(String bizTag, long maxId, int step) {
        this.bizTag = bizTag;
        this.maxId = maxId;
        this.step = step;
    }

    public String getBizTag() { return bizTag; }
    public long getMaxId() { return maxId; }
    public int getStep() { return step; }

    @Override
    public String toString() {
        return "SegmentAllocation{bizTag='" + bizTag + "', maxId=" + maxId + ", step=" + step + '}';
    }
}
