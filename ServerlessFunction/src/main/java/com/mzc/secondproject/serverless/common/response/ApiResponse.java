package com.mzc.secondproject.serverless.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 표준 API 응답 래퍼
 *
 * 모든 API 응답의 일관된 형식을 제공합니다.
 * 성공/실패 여부와 관계없이 동일한 구조를 유지합니다.
 *
 * 성공 응답 예시:
 * {
 *   "success": true,
 *   "data": { ... },
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 *
 * 실패 응답 예시:
 * {
 *   "success": false,
 *   "error": { ... },
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 *
 * @param success 성공 여부
 * @param data 응답 데이터 (성공 시)
 * @param error 에러 정보 (실패 시)
 * @param timestamp 응답 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorInfo error,
        String timestamp
) {

    /**
     * 성공 응답 생성 (데이터 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now().toString());
    }

    /**
     * 성공 응답 생성 (데이터 없음)
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null, Instant.now().toString());
    }

    /**
     * 실패 응답 생성 (ErrorInfo 직접 전달)
     */
    public static <T> ApiResponse<T> error(ErrorInfo errorInfo) {
        return new ApiResponse<>(false, null, errorInfo, Instant.now().toString());
    }

    /**
     * 실패 응답 생성 (에러 코드, 메시지 전달)
     */
    public static <T> ApiResponse<T> error(String code, String message, int status) {
        return new ApiResponse<>(false, null,
                ErrorInfo.of(code, message, status), Instant.now().toString());
    }

    /**
     * 실패 응답 생성 (ServerlessException에서 변환)
     */
    public static <T> ApiResponse<T> error(com.mzc.secondproject.serverless.common.exception.ServerlessException ex) {
        return new ApiResponse<>(false, null, ErrorInfo.from(ex), Instant.now().toString());
    }
}
