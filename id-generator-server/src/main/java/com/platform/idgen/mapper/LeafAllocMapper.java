package com.platform.idgen.mapper;

import com.platform.idgen.model.LeafAlloc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis Mapper for LeafAlloc table.
 */
@Mapper
public interface LeafAllocMapper {
    
    /**
     * Find all LeafAlloc records.
     * 
     * @return list of all LeafAlloc records
     */
    List<LeafAlloc> findAll();
    
    /**
     * Find LeafAlloc by business tag.
     * 
     * @param bizTag the business tag to find
     * @return LeafAlloc record or null if not found
     */
    LeafAlloc findByBizTag(@Param("bizTag") String bizTag);
    
    /**
     * Update max_id using optimistic locking.
     * Only updates if version matches.
     * 
     * @param bizTag the business tag to update
     * @param step the step to add to max_id
     * @param version the expected version
     * @return number of rows updated (0 or 1)
     */
    int updateMaxIdWithLock(@Param("bizTag") String bizTag, 
                            @Param("step") int step,
                            @Param("version") long version);
    
    /**
     * Insert a new LeafAlloc record.
     * 
     * @param leafAlloc the record to insert
     * @return number of rows inserted
     */
    int insert(LeafAlloc leafAlloc);
}
