package com.platform.idgen.domain.service;

import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.domain.repository.WorkerIdRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("SnowflakeDomainService 测试")
class SnowflakeDomainServiceTest {

    private SnowflakeDomainService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("未初始化时 generateId 应返回 SNOWFLAKE_NOT_INITIALIZED")
    void 未初始化时generateId应返回未初始化错误码() {
        WorkerIdRepository repository = mock(WorkerIdRepository.class);
        service = new SnowflakeDomainService(
                repository,
                new SimpleMeterRegistry(),
                0,
                0L,
                10L,
                5L,
                5000L
        );

        assertThatThrownBy(() -> service.generateId())
                .isInstanceOf(IdGenerationException.class)
                .satisfies(ex -> assertThat(((IdGenerationException) ex).getErrorCode())
                        .isEqualTo(IdGenerationException.ErrorCode.SNOWFLAKE_NOT_INITIALIZED));
    }

    @Test
    @DisplayName("关闭后 generateId 应返回 SERVICE_SHUTTING_DOWN")
    void 关闭后generateId应返回服务关闭错误码() {
        WorkerIdRepository repository = mock(WorkerIdRepository.class);
        service = new SnowflakeDomainService(
                repository,
                new SimpleMeterRegistry(),
                0,
                0L,
                10L,
                5L,
                5000L
        );
        service.shutdown();

        assertThatThrownBy(() -> service.generateId())
                .isInstanceOf(IdGenerationException.class)
                .satisfies(ex -> assertThat(((IdGenerationException) ex).getErrorCode())
                        .isEqualTo(IdGenerationException.ErrorCode.SERVICE_SHUTTING_DOWN));
    }
}
