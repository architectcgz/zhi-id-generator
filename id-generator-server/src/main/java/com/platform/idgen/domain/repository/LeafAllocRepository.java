package com.platform.idgen.domain.repository;

import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.model.LeafAlloc;

import java.util.List;
import java.util.Optional;

/**
 * LeafAlloc Repository Interface
 * 
 * Abstracts database operations for Leaf Segment allocation.
 */
public interface LeafAllocRepository {
    
    /**
     * Find all business tags in the database.
     * 
     * @return list of BizTag value objects
     */
    List<BizTag> findAllBizTags();
    
    /**
     * Find LeafAlloc by business tag.
     * 
     * @param bizTag the business tag to find
     * @return Optional containing LeafAlloc, empty if not found
     */
    Optional<LeafAlloc> findByBizTag(BizTag bizTag);
    
    /**
     * Update max_id for the given business tag using default step.
     * Uses optimistic locking with retry.
     * 
     * @param bizTag the business tag to update
     * @return updated LeafAlloc with new max_id
     * @throws RuntimeException if update fails after retries
     */
    LeafAlloc updateMaxId(BizTag bizTag);
    
    /**
     * Update max_id for the given business tag using custom step.
     * Uses optimistic locking with retry.
     * 
     * @param bizTag the business tag to update
     * @param step custom step size
     * @return updated LeafAlloc with new max_id
     * @throws RuntimeException if update fails after retries
     */
    LeafAlloc updateMaxIdByCustomStep(BizTag bizTag, int step);
}
