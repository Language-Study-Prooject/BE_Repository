package com.mzc.secondproject.serverless.common.config;

/**
 * WebSocket 관련 환경변수 설정
 * Lambda 환경변수에서 값을 읽어오며, 없을 경우 기본값 사용
 */
public final class WebSocketConfig {
	
	// 환경변수 키
	private static final String ENV_CONNECTION_TTL_SECONDS = "WEBSOCKET_CONNECTION_TTL_SECONDS";
	private static final String ENV_WEBSOCKET_ENDPOINT = "WEBSOCKET_ENDPOINT";
	// 기본값
	private static final long DEFAULT_CONNECTION_TTL_SECONDS = 600L; // 10분
	// 캐시된 값 (Cold Start 최적화)
	private static final long CONNECTION_TTL_SECONDS = EnvConfig.getLongOrDefault(ENV_CONNECTION_TTL_SECONDS, DEFAULT_CONNECTION_TTL_SECONDS);
	private static final String WEBSOCKET_ENDPOINT = EnvConfig.getRequired(ENV_WEBSOCKET_ENDPOINT);
	
	private WebSocketConfig() {
		// 인스턴스화 방지
	}
	
	/**
	 * WebSocket 연결 TTL (초)
	 */
	public static long connectionTtlSeconds() {
		return CONNECTION_TTL_SECONDS;
	}
	
	/**
	 * WebSocket API Gateway 엔드포인트 URL
	 * 메시지 브로드캐스트 시 사용
	 */
	public static String websocketEndpoint() {
		return WEBSOCKET_ENDPOINT;
	}
}
