package com.mzc.secondproject.serverless.common.dto;

/**
 * 표준 API 응답 래퍼
 *
 * @param isSuccess 성공 여부
 * @param message   응답 메시지
 * @param data      응답 데이터
 * @param error     에러 메시지
 */
public record ApiResponse<T>(
		boolean isSuccess,
		String message,
		T data,
		String error
) {
	
	public static <T> ApiResponse<T> ok(String message, T data) {
		return new ApiResponse<>(true, message, data, null);
	}
	
	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, null, data, null);
	}
	
	public static <T> ApiResponse<T> fail(String errorMessage) {
		return new ApiResponse<>(false, null, null, errorMessage);
	}
}
