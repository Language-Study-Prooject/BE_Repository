package com.mzc.secondproject.serverless.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 이벤트 처리 유틸리티
 * 공통 메서드를 제공하여 핸들러 간 코드 중복 제거
 */
public final class WebSocketEventUtil {
	
	private WebSocketEventUtil() {
		// 유틸리티 클래스 인스턴스화 방지
	}
	
	/**
	 * WebSocket 이벤트에서 connectionId 추출
	 */
	@SuppressWarnings("unchecked")
	public static String extractConnectionId(Map<String, Object> event) {
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		return (String) requestContext.get("connectionId");
	}
	
	/**
	 * WebSocket 이벤트에서 queryStringParameters 추출
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> extractQueryStringParameters(Map<String, Object> event) {
		Map<String, String> params = (Map<String, String>) event.get("queryStringParameters");
		return params != null ? params : new HashMap<>();
	}
	
	/**
	 * WebSocket 이벤트에서 endpoint URL 추출
	 * API Gateway Management API 호출에 사용
	 */
	@SuppressWarnings("unchecked")
	public static String extractWebSocketEndpoint(Map<String, Object> event) {
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		String domainName = (String) requestContext.get("domainName");
		String stage = (String) requestContext.get("stage");
		return "https://" + domainName + "/" + stage;
	}
	
	/**
	 * WebSocket 응답 생성
	 */
	public static Map<String, Object> createResponse(int statusCode, String body) {
		return Map.of("statusCode", statusCode, "body", body);
	}
	
	/**
	 * 성공 응답 생성 (200)
	 */
	public static Map<String, Object> ok(String message) {
		return createResponse(200, message);
	}
	
	/**
	 * 인증 실패 응답 생성 (401)
	 */
	public static Map<String, Object> unauthorized(String message) {
		return createResponse(401, message);
	}
	
	/**
	 * 서버 에러 응답 생성 (500)
	 */
	public static Map<String, Object> serverError(String message) {
		return createResponse(500, message);
	}
	
	/**
	 * 잘못된 요청 응답 생성 (400)
	 */
	public static Map<String, Object> badRequest(String message) {
		return createResponse(400, message);
	}
}
