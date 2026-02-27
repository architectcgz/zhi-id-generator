package com.platform.idgen.infrastructure.persistence.mapper;

import com.platform.idgen.infrastructure.persistence.entity.LeafAlloc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis Mapper for LeafAlloc table.
 */
@Mapper
public interface LeafAllocMapper {

    List<LeafAlloc> findAll();

    LeafAlloc findByBizTag(@Param("bizTag") String bizTag);

    int updateMaxIdWithLock(@Param("bizTag") String bizTag,
                            @Param("step") int step,
                            @Param("version") long version);

    int insert(LeafAlloc leafAlloc);
}
