package com.platform.idgen.domain.model.valueobject;

/**
 * BizTag value object representing a business tag identifier for Segment mode.
 * Must be non-null and non-blank.
 */
public record BizTag(String value) {
    
    /**
     * Compact constructor with validation.
     */
    public BizTag {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BizTag cannot be null or empty");
        }
    }
}
