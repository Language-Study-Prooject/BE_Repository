package com.mzc.secondproject.serverless.common.config;

/**
 * RoomToken 관련 환경변수 설정
 * Lambda 환경변수에서 값을 읽어오며, 없을 경우 기본값 사용
 */
public final class RoomTokenConfig {

	// 환경변수 키
	private static final String ENV_TOKEN_TTL_SECONDS = "ROOM_TOKEN_TTL_SECONDS";
	// 기본값: 5분
	private static final long DEFAULT_TOKEN_TTL_SECONDS = 300L;
	// 캐시된 값 (Cold Start 최적화)
	private static final long TOKEN_TTL_SECONDS = EnvConfig.getLongOrDefault(ENV_TOKEN_TTL_SECONDS, DEFAULT_TOKEN_TTL_SECONDS);

	private RoomTokenConfig() {
		// 인스턴스화 방지
	}

	/**
	 * RoomToken TTL (초)
	 */
	public static long tokenTtlSeconds() {
		return TOKEN_TTL_SECONDS;
	}
}
