package com.platform.idgen.infrastructure.repository;

import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.model.valueobject.SegmentAllocation;
import com.platform.idgen.domain.repository.LeafAllocRepository;
import com.platform.idgen.infrastructure.persistence.mapper.LeafAllocMapper;
import com.platform.idgen.infrastructure.persistence.entity.LeafAlloc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LeafAlloc Repository Implementation
 * 
 * Implements database operations with optimistic locking and retry logic.
 */
@Repository
public class LeafAllocRepositoryImpl implements LeafAllocRepository {
    
    private static final Logger log = LoggerFactory.getLogger(LeafAllocRepositoryImpl.class);
    
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 10;
    
    private final LeafAllocMapper mapper;
    
    public LeafAllocRepositoryImpl(LeafAllocMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public List<BizTag> findAllBizTags() {
        List<LeafAlloc> allocs = mapper.findAll();
        return allocs.stream()
                .map(alloc -> new BizTag(alloc.getBizTag()))
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<SegmentAllocation> findByBizTag(BizTag bizTag) {
        LeafAlloc alloc = mapper.findByBizTag(bizTag.value());
        return Optional.ofNullable(alloc).map(this::toSegmentAllocation);
    }
    
    @Override
    public SegmentAllocation updateMaxId(BizTag bizTag) {
        LeafAlloc current = mapper.findByBizTag(bizTag.value());
        if (current == null) {
            throw new RuntimeException("BizTag not found: " + bizTag.value());
        }
        return updateMaxIdByCustomStep(bizTag, current.getStep());
    }
    
    @Override
    public SegmentAllocation updateMaxIdByCustomStep(BizTag bizTag, int step) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            // Read current state
            LeafAlloc current = mapper.findByBizTag(bizTag.value());
            if (current == null) {
                throw new RuntimeException("BizTag not found: " + bizTag.value());
            }
            
            // Try to update with optimistic lock
            int updated = mapper.updateMaxIdWithLock(bizTag.value(), step, current.getVersion());
            
            if (updated == 1) {
                LeafAlloc result = mapper.findByBizTag(bizTag.value());
                log.debug("Updated max_id for bizTag: {} to {}, step: {}",
                         bizTag.value(), result.getMaxId(), step);
                return toSegmentAllocation(result);
            }
            
            // Optimistic lock conflict - retry
            log.warn("Optimistic lock conflict for bizTag: {}, attempt {}/{}",
                    bizTag.value(), attempt, MAX_RETRY_ATTEMPTS);
            
            if (attempt < MAX_RETRY_ATTEMPTS) {
                try {
                    // Exponential backoff
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", e);
                }
            }
        }
        
        throw new RuntimeException("Failed to update max_id after " + MAX_RETRY_ATTEMPTS +
                                   " attempts for bizTag: " + bizTag.value());
    }

    private SegmentAllocation toSegmentAllocation(LeafAlloc alloc) {
        return new SegmentAllocation(alloc.getBizTag(), alloc.getMaxId(), alloc.getStep());
    }
}
