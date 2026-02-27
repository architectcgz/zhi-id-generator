package com.platform.idgen.domain.repository;

import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.model.valueobject.SegmentAllocation;

import java.util.List;
import java.util.Optional;

/**
 * 号段分配仓储接口（领域层定义，基础设施层实现）。
 */
public interface LeafAllocRepository {

    List<BizTag> findAllBizTags();

    Optional<SegmentAllocation> findByBizTag(BizTag bizTag);

    SegmentAllocation updateMaxId(BizTag bizTag);

    SegmentAllocation updateMaxIdByCustomStep(BizTag bizTag, int step);
}
