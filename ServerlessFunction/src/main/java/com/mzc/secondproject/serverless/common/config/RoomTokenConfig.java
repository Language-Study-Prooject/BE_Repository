package com.mzc.secondproject.serverless.common.config;

/**
 * RoomToken 관련 환경변수 설정
 * Lambda 환경변수에서 값을 읽어오며, 없을 경우 기본값 사용
 */
public final class RoomTokenConfig {

    private RoomTokenConfig() {
        // 인스턴스화 방지
    }

    // 환경변수 키
    private static final String ENV_TOKEN_TTL_SECONDS = "ROOM_TOKEN_TTL_SECONDS";

    // 기본값: 5분
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 300L;

    // 캐시된 값 (Cold Start 최적화)
    private static final long TOKEN_TTL_SECONDS = parseTokenTtl();

    private static long parseTokenTtl() {
        String value = System.getenv(ENV_TOKEN_TTL_SECONDS);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // 파싱 실패 시 기본값 사용
            }
        }
        return DEFAULT_TOKEN_TTL_SECONDS;
    }

    /**
     * RoomToken TTL (초)
     */
    public static long tokenTtlSeconds() {
        return TOKEN_TTL_SECONDS;
    }
}
