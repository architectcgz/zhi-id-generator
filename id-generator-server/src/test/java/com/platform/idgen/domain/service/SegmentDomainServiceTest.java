package com.platform.idgen.domain.service;

import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.domain.model.valueobject.BizTag;
import com.platform.idgen.domain.model.valueobject.SegmentAllocation;
import com.platform.idgen.domain.repository.LeafAllocRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * SegmentDomainService 单元测试
 */
@DisplayName("SegmentDomainService 测试")
class SegmentDomainServiceTest {

    private SegmentDomainService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("缓存未预热时应懒创建 buffer 并成功生成 segment ID")
    void 缓存未预热时应懒创建buffer并成功生成segmentId() {
        LeafAllocRepository repository = mock(LeafAllocRepository.class);
        when(repository.findAllBizTags()).thenReturn(List.of());

        BizTag bizTag = new BizTag("order");
        when(repository.findByBizTag(bizTag))
                .thenReturn(Optional.of(new SegmentAllocation("order", 100L, 10)))
                .thenReturn(Optional.of(new SegmentAllocation("order", 100L, 10)));
        when(repository.updateMaxId(bizTag))
                .thenReturn(new SegmentAllocation("order", 100L, 10));

        service = new SegmentDomainService(
                repository,
                new SimpleMeterRegistry(),
                60,
                900_000L,
                1_000_000,
                1
        );
        service.init();

        long id = service.generateId(bizTag);

        assertThat(id).isEqualTo(90L);
        assertThat(service.getAllBizTags()).contains("order");
        verify(repository, atLeastOnce()).findByBizTag(bizTag);
        verify(repository).updateMaxId(bizTag);
    }

    @Test
    @DisplayName("懒创建初始化失败时不应把未初始化 buffer 留在缓存中")
    void 懒创建初始化失败时不应保留脏缓存() {
        LeafAllocRepository repository = mock(LeafAllocRepository.class);
        when(repository.findAllBizTags()).thenReturn(List.of());

        BizTag bizTag = new BizTag("missing-tag");
        when(repository.findByBizTag(bizTag)).thenReturn(Optional.empty());

        service = new SegmentDomainService(
                repository,
                new SimpleMeterRegistry(),
                60,
                900_000L,
                1_000_000,
                1
        );
        service.init();

        assertThatThrownBy(() -> service.generateId(bizTag))
                .isInstanceOf(IdGenerationException.class)
                .satisfies(ex -> assertThat(((IdGenerationException) ex).getErrorCode())
                        .isEqualTo(IdGenerationException.ErrorCode.BIZ_TAG_NOT_EXISTS));

        assertThat(service.getAllBizTags()).doesNotContain("missing-tag");
        verify(repository).findByBizTag(bizTag);
        verify(repository, never()).updateMaxId(bizTag);
    }
}
