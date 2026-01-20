package com.mzc.secondproject.serverless.domain.grammar.config;

import com.mzc.secondproject.serverless.common.config.EnvConfig;

/**
 * Grammar 도메인 설정값
 * 환경 변수로 오버라이드 가능
 */
public final class GrammarConfig {

	private static final int DEFAULT_SESSION_TTL_DAYS = 30;
	private static final int DEFAULT_MAX_HISTORY_MESSAGES = 10;
	private static final int DEFAULT_LAST_MESSAGE_MAX_LENGTH = 100;
	private static final int DEFAULT_MAX_TOKENS = 2048;

	private static final int SESSION_TTL_DAYS = EnvConfig.getIntOrDefault("GRAMMAR_SESSION_TTL_DAYS", DEFAULT_SESSION_TTL_DAYS);
	private static final int MAX_HISTORY_MESSAGES = EnvConfig.getIntOrDefault("GRAMMAR_MAX_HISTORY_MESSAGES", DEFAULT_MAX_HISTORY_MESSAGES);
	private static final int LAST_MESSAGE_MAX_LENGTH = EnvConfig.getIntOrDefault("GRAMMAR_LAST_MESSAGE_MAX_LENGTH", DEFAULT_LAST_MESSAGE_MAX_LENGTH);
	private static final int MAX_TOKENS = EnvConfig.getIntOrDefault("GRAMMAR_MAX_TOKENS", DEFAULT_MAX_TOKENS);

	private GrammarConfig() {
	}

	public static int sessionTtlDays() {
		return SESSION_TTL_DAYS;
	}

	public static int maxHistoryMessages() {
		return MAX_HISTORY_MESSAGES;
	}

	public static int lastMessageMaxLength() {
		return LAST_MESSAGE_MAX_LENGTH;
	}

	public static int maxTokens() {
		return MAX_TOKENS;
	}
}
