package com.mzc.secondproject.serverless.common.config;

public final class StudyConfig {
	
	// Spaced Repetition 기본값
	public static final int INITIAL_INTERVAL_DAYS = 1;
	public static final double DEFAULT_EASE_FACTOR = 2.5;
	public static final double MIN_EASE_FACTOR = 1.3;
	public static final int INITIAL_REPETITIONS = 0;
	// 오답 관련
	public static final int MAX_WRONG_COUNT = 3;
	// 테스트 관련
	public static final int DEFAULT_WORD_COUNT = 20;
	public static final int DAILY_TEST_WORD_COUNT = 10;
	// 복습 간격 (일 단위)
	public static final int[] REVIEW_INTERVALS = {1, 3, 7, 14, 30};
	// 상태 기본값
	public static final String DEFAULT_WORD_STATUS = "NEW";
	public static final String DEFAULT_DIFFICULTY = "NORMAL";
	// 정답/오답 카운트 초기값
	public static final int INITIAL_CORRECT_COUNT = 0;
	public static final int INITIAL_INCORRECT_COUNT = 0;
	
	private StudyConfig() {
	}
}
