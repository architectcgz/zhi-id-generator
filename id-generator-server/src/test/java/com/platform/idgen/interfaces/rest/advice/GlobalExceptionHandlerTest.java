package com.platform.idgen.interfaces.rest.advice;

import com.platform.idgen.domain.exception.IdGenerationException;
import com.platform.idgen.interfaces.rest.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 单元测试
 */
@DisplayName("GlobalExceptionHandler 测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("WORKER_ID_INVALID 应映射为 503 SERVICE_UNAVAILABLE")
    void workerIdInvalid应映射为服务不可用() {
        IdGenerationException exception = new IdGenerationException(
                IdGenerationException.ErrorCode.WORKER_ID_INVALID
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleIdGenerationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(50304);
        assertThat(response.getBody().getErrorCode()).isEqualTo("WORKER_ID_INVALID");
        assertThat(response.getBody().getMessage()).isEqualTo("Worker ID is invalid, lease renewal failed");
    }

    @Test
    @DisplayName("BIZ_TAG_NOT_EXISTS 应映射为 404 NOT_FOUND")
    void bizTagNotExists应映射为未找到() {
        IdGenerationException exception = new IdGenerationException(
                IdGenerationException.ErrorCode.BIZ_TAG_NOT_EXISTS,
                "order"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleIdGenerationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(40401);
        assertThat(response.getBody().getErrorCode()).isEqualTo("BIZ_TAG_NOT_EXISTS");
    }

    @Test
    @DisplayName("SEGMENT_UPDATE_FAILED 应映射为 503 SERVICE_UNAVAILABLE")
    void segmentUpdateFailed应映射为服务不可用() {
        IdGenerationException exception = new IdGenerationException(
                IdGenerationException.ErrorCode.SEGMENT_UPDATE_FAILED,
                "order"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleIdGenerationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(50305);
        assertThat(response.getBody().getErrorCode()).isEqualTo("SEGMENT_UPDATE_FAILED");
    }

    @Test
    @DisplayName("SERVICE_SHUTTING_DOWN 应映射为 503 SERVICE_UNAVAILABLE")
    void serviceShuttingDown应映射为服务不可用() {
        IdGenerationException exception = new IdGenerationException(
                IdGenerationException.ErrorCode.SERVICE_SHUTTING_DOWN,
                "shutdown"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleIdGenerationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(50306);
        assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_SHUTTING_DOWN");
    }

    @Test
    @DisplayName("WorkerIdUnavailableException 携带 cause 时应保留详细原因")
    void workerIdUnavailable携带Cause时应保留详细原因() {
        IdGenerationException exception = new com.platform.idgen.domain.exception.WorkerIdUnavailableException(
                "db lease conflict", new RuntimeException("boom"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleIdGenerationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("db lease conflict");
    }

    @Test
    @DisplayName("IllegalStateException 应视为内部状态错误并映射为 500")
    void illegalStateException应映射为内部错误() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalStateException(new IllegalStateException("unexpected state"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(50002);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ILLEGAL_STATE");
        assertThat(response.getBody().getMessage()).isEqualTo("Internal state error");
    }
}
