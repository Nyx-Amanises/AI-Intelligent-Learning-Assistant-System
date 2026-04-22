package com.aiassistant.learning.common.result;

import lombok.Data;

/**
 * 统一接口响应对象。
 *
 * <p>Controller 返回给前端的数据都可以包装成这个结构，前端就能用固定格式读取：
 * code 表示状态码，message 表示提示信息，data 表示真正的业务数据。</p>
 *
 * @param <T> data 字段的数据类型，例如用户信息、分页结果、列表等
 */
@Data
public class ApiResponse<T> {

    /**
     * 业务状态码。这里约定 200 表示成功，其他值通常表示不同错误。
     */
    private Integer code;

    /**
     * 给前端或用户看的提示信息。
     */
    private String message;

    /**
     * 真正返回的数据内容。使用泛型 T 是为了让不同接口可以返回不同类型的数据。
     */
    private T data;

    /**
     * 创建一个成功响应，默认提示信息为 success。
     *
     * @param data 要返回给前端的业务数据
     * @param <T> data 的类型
     * @return 统一格式的成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    /**
     * 创建一个成功响应，并允许自定义成功提示。
     *
     * @param message 自定义提示信息
     * @param data 要返回给前端的业务数据
     * @param <T> data 的类型
     * @return 统一格式的成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    /**
     * 创建一个失败响应。
     *
     * @param code 错误码，例如 400 表示请求参数问题，401 表示未登录
     * @param message 错误提示信息
     * @param <T> data 的类型，失败时通常没有 data
     * @return 统一格式的失败响应
     */
    public static <T> ApiResponse<T> fail(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
