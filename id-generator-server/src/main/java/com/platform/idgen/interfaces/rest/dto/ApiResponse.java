package com.platform.idgen.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 统一 API 响应结构，成功和失败都使用同一格式。
 *
 * @param <T> 响应数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String errorCode;
    private Map<String, Object> extra;

    private ApiResponse() {}

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = 200;
        resp.message = "success";
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String errorCode, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        resp.errorCode = errorCode;
        return resp;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getExtra() { return extra; }

    /**
     * 链式设置额外信息字段，用于携带异常详情等结构化数据。
     */
    public ApiResponse<T> withExtra(Map<String, Object> extra) {
        this.extra = extra;
        return this;
    }
}
