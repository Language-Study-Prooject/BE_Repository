package com.mzc.secondproject.serverless.domain.vocabulary.config;

import com.mzc.secondproject.serverless.common.config.EnvConfig;

/**
 * Vocabulary 도메인 설정값
 * 환경 변수로 오버라이드 가능
 */
public final class VocabularyConfig {

	// 일일 학습 관련
	private static final int DEFAULT_NEW_WORDS_COUNT = 50;
	private static final int DEFAULT_REVIEW_WORDS_COUNT = 5;

	// 단어 상태 전이 관련
	private static final int DEFAULT_TRANSITION_TO_REVIEWING_THRESHOLD = 2;
	private static final int DEFAULT_TRANSITION_TO_MASTERED_THRESHOLD = 5;
	private static final int DEFAULT_SECOND_INTERVAL_DAYS = 6;

	private static final int NEW_WORDS_COUNT = EnvConfig.getIntOrDefault("VOCAB_NEW_WORDS_COUNT", DEFAULT_NEW_WORDS_COUNT);
	private static final int REVIEW_WORDS_COUNT = EnvConfig.getIntOrDefault("VOCAB_REVIEW_WORDS_COUNT", DEFAULT_REVIEW_WORDS_COUNT);
	private static final int TRANSITION_TO_REVIEWING_THRESHOLD = EnvConfig.getIntOrDefault("VOCAB_TRANSITION_TO_REVIEWING", DEFAULT_TRANSITION_TO_REVIEWING_THRESHOLD);
	private static final int TRANSITION_TO_MASTERED_THRESHOLD = EnvConfig.getIntOrDefault("VOCAB_TRANSITION_TO_MASTERED", DEFAULT_TRANSITION_TO_MASTERED_THRESHOLD);
	private static final int SECOND_INTERVAL_DAYS = EnvConfig.getIntOrDefault("VOCAB_SECOND_INTERVAL_DAYS", DEFAULT_SECOND_INTERVAL_DAYS);

	private VocabularyConfig() {
	}

	public static int newWordsCount() {
		return NEW_WORDS_COUNT;
	}

	public static int reviewWordsCount() {
		return REVIEW_WORDS_COUNT;
	}

	public static int transitionToReviewingThreshold() {
		return TRANSITION_TO_REVIEWING_THRESHOLD;
	}

	public static int transitionToMasteredThreshold() {
		return TRANSITION_TO_MASTERED_THRESHOLD;
	}

	public static int secondIntervalDays() {
		return SECOND_INTERVAL_DAYS;
	}
}
