package com.platform.idgen.model;

import java.time.LocalDateTime;

/**
 * LeafAlloc entity for Segment mode ID allocation.
 * 
 * Maps to the leaf_alloc database table.
 */
public class LeafAlloc {
    
    /**
     * Business tag identifier (primary key)
     */
    private String bizTag;
    
    /**
     * Current maximum allocated ID
     */
    private long maxId;
    
    /**
     * Allocation step size
     */
    private int step;
    
    /**
     * Description of the business tag
     */
    private String description;
    
    /**
     * Last update timestamp
     */
    private LocalDateTime updateTime;
    
    /**
     * Version for optimistic locking
     */
    private long version;
    
    // Constructors
    
    public LeafAlloc() {
    }
    
    public LeafAlloc(String bizTag, long maxId, int step) {
        this.bizTag = bizTag;
        this.maxId = maxId;
        this.step = step;
    }
    
    // Getters and Setters
    
    public String getBizTag() {
        return bizTag;
    }
    
    public void setBizTag(String bizTag) {
        this.bizTag = bizTag;
    }
    
    public long getMaxId() {
        return maxId;
    }
    
    public void setMaxId(long maxId) {
        this.maxId = maxId;
    }
    
    public int getStep() {
        return step;
    }
    
    public void setStep(int step) {
        this.step = step;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public long getVersion() {
        return version;
    }
    
    public void setVersion(long version) {
        this.version = version;
    }
    
    @Override
    public String toString() {
        return "LeafAlloc{" +
               "bizTag='" + bizTag + '\'' +
               ", maxId=" + maxId +
               ", step=" + step +
               ", version=" + version +
               '}';
    }
}
