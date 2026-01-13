package com.mzc.secondproject.serverless.common.util;

import java.util.Map;

/**
 * WebSocket API Gateway 응답 생성 유틸리티
 */
public final class WebSocketResponseUtil {
	
	private WebSocketResponseUtil() {
	}
	
	public static Map<String, Object> ok(String message) {
		return Map.of("statusCode", 200, "body", message);
	}
	
	public static Map<String, Object> created(String message) {
		return Map.of("statusCode", 201, "body", message);
	}
	
	public static Map<String, Object> badRequest(String message) {
		return Map.of("statusCode", 400, "body", message);
	}
	
	public static Map<String, Object> unauthorized(String message) {
		return Map.of("statusCode", 401, "body", message);
	}
	
	public static Map<String, Object> forbidden(String message) {
		return Map.of("statusCode", 403, "body", message);
	}
	
	public static Map<String, Object> serverError(String message) {
		return Map.of("statusCode", 500, "body", message);
	}
	
	public static Map<String, Object> response(int statusCode, String message) {
		return Map.of("statusCode", statusCode, "body", message);
	}
}
