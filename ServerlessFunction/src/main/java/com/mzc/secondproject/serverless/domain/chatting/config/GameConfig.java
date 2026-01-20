package com.mzc.secondproject.serverless.domain.chatting.config;

import com.mzc.secondproject.serverless.common.config.EnvConfig;

/**
 * 게임 관련 설정값
 * 환경 변수로 오버라이드 가능
 */
public final class GameConfig {
	
	private static final int DEFAULT_TOTAL_ROUNDS = 5;
	private static final int DEFAULT_ROUND_TIME_LIMIT = 60;
	private static final long DEFAULT_QUICK_GUESS_THRESHOLD_MS = 5000L;
	
	private static final int TOTAL_ROUNDS = EnvConfig.getIntOrDefault("GAME_TOTAL_ROUNDS", DEFAULT_TOTAL_ROUNDS);
	private static final int ROUND_TIME_LIMIT = EnvConfig.getIntOrDefault("GAME_ROUND_TIME_LIMIT", DEFAULT_ROUND_TIME_LIMIT);
	private static final long QUICK_GUESS_THRESHOLD_MS = EnvConfig.getLongOrDefault("GAME_QUICK_GUESS_THRESHOLD_MS", DEFAULT_QUICK_GUESS_THRESHOLD_MS);
	
	private GameConfig() {
	}
	
	public static int totalRounds() {
		return TOTAL_ROUNDS;
	}
	
	public static int roundTimeLimit() {
		return ROUND_TIME_LIMIT;
	}
	
	public static long quickGuessThresholdMs() {
		return QUICK_GUESS_THRESHOLD_MS;
	}
}
