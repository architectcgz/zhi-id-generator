package com.platform.idgen.interfaces.rest;

import com.platform.idgen.application.IdGeneratorApplicationService;
import com.platform.idgen.application.dto.HealthStatus;
import com.platform.idgen.application.dto.SegmentCacheInfo;
import com.platform.idgen.application.dto.SnowflakeInfo;
import com.platform.idgen.application.dto.SnowflakeParseInfo;
import com.platform.idgen.interfaces.rest.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("IdGeneratorController 测试")
class IdGeneratorControllerTest {

    @Test
    @DisplayName("非法 bizTag 应在控制层直接拒绝")
    void 非法bizTag应在控制层直接拒绝() {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        IdGeneratorController controller = new IdGeneratorController(applicationService);

        assertThatThrownBy(() -> controller.getSegmentId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BizTag cannot be null or empty");

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("非法 batch count 应在控制层直接拒绝")
    void 非法batchCount应在控制层直接拒绝() {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        IdGeneratorController controller = new IdGeneratorController(applicationService);

        assertThatThrownBy(() -> controller.getBatchSnowflakeIds(1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Count must not exceed 1000");

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("cache 接口复用统一 bizTag 校验")
    void cache接口复用统一bizTag校验() {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        IdGeneratorController controller = new IdGeneratorController(applicationService);

        assertThatThrownBy(() -> controller.getCacheInfo("x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BizTag length must not exceed 128 characters");

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("health 接口应返回稳定的 ApiResponse 查询结构")
    void health接口应返回稳定查询结构() throws Exception {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        when(applicationService.getHealthStatus()).thenReturn(
                new HealthStatus(
                        "UP",
                        "id-generator-service",
                        123456789L,
                        new HealthStatus.SegmentHealth(true, 2),
                        new HealthStatus.SnowflakeHealth(true, 7, 3)
                )
        );

        MockMvc mockMvc = buildMockMvc(applicationService);

        mockMvc.perform(get("/api/v1/id/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("id-generator-service"))
                .andExpect(jsonPath("$.data.segment.initialized").value(true))
                .andExpect(jsonPath("$.data.segment.bizTagCount").value(2))
                .andExpect(jsonPath("$.data.snowflake.workerId").value(7))
                .andExpect(jsonPath("$.data.snowflake.datacenterId").value(3));
    }

    @Test
    @DisplayName("snowflake info 接口应返回结构化 DTO")
    void snowflakeInfo接口应返回结构化Dto() throws Exception {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        when(applicationService.getSnowflakeInfo()).thenReturn(new SnowflakeInfo(true, 7, 3, 1735689600000L));

        MockMvc mockMvc = buildMockMvc(applicationService);

        mockMvc.perform(get("/api/v1/id/snowflake/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.initialized").value(true))
                .andExpect(jsonPath("$.data.workerId").value(7))
                .andExpect(jsonPath("$.data.datacenterId").value(3))
                .andExpect(jsonPath("$.data.epoch").value(1735689600000L));
    }

    @Test
    @DisplayName("snowflake parse 接口应返回结构化 DTO")
    void snowflakeParse接口应返回结构化Dto() throws Exception {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        when(applicationService.parseSnowflakeId(123L))
                .thenReturn(new SnowflakeParseInfo(123L, 1735689600123L, 3L, 7L, 12L, 1735689600000L));

        MockMvc mockMvc = buildMockMvc(applicationService);

        mockMvc.perform(get("/api/v1/id/snowflake/parse/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(123))
                .andExpect(jsonPath("$.data.timestamp").value(1735689600123L))
                .andExpect(jsonPath("$.data.datacenterId").value(3))
                .andExpect(jsonPath("$.data.workerId").value(7))
                .andExpect(jsonPath("$.data.sequence").value(12))
                .andExpect(jsonPath("$.data.epoch").value(1735689600000L));
    }

    @Test
    @DisplayName("cache 接口应返回结构化 DTO")
    void cache接口应返回结构化Dto() throws Exception {
        IdGeneratorApplicationService applicationService = mock(IdGeneratorApplicationService.class);
        when(applicationService.getSegmentCacheInfo("order")).thenReturn(
                new SegmentCacheInfo(
                        "order",
                        true,
                        true,
                        true,
                        0,
                        true,
                        false,
                        100,
                        123456789L,
                        new SegmentCacheInfo.SegmentState(120L, 200L, 80, 80L),
                        new SegmentCacheInfo.SegmentState(200L, 280L, 80, 80L)
                )
        );

        MockMvc mockMvc = buildMockMvc(applicationService);

        mockMvc.perform(get("/api/v1/id/cache/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bizTag").value("order"))
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.bufferInitialized").value(true))
                .andExpect(jsonPath("$.data.currentSegment.value").value(120))
                .andExpect(jsonPath("$.data.currentSegment.max").value(200))
                .andExpect(jsonPath("$.data.nextSegment.step").value(80));
    }

    private MockMvc buildMockMvc(IdGeneratorApplicationService applicationService) {
        return MockMvcBuilders.standaloneSetup(new IdGeneratorController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
