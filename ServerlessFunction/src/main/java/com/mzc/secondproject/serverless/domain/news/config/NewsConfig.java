package com.mzc.secondproject.serverless.domain.news.config;

import com.mzc.secondproject.serverless.common.config.EnvConfig;

/**
 * 뉴스 도메인 설정
 * 상수 및 환경변수 관리
 */
public final class NewsConfig {

	private NewsConfig() {
	}

	// ========== Environment Variables ==========
	private static final String BUCKET_NAME = EnvConfig.getOrDefault("NEWS_BUCKET_NAME", "group2-englishstudy");

	// ========== TTS 설정 ==========
	/** TTS 텍스트 최대 길이 */
	public static final int TTS_MAX_TEXT_LENGTH = 3000;

	/** TTS 오디오 저장 경로 */
	public static final String TTS_AUDIO_PREFIX = "news/audio/";

	/** 기본 TTS 음성 */
	public static final String DEFAULT_VOICE = "Joanna";

	// ========== 페이지네이션 ==========
	/** 기본 페이지 크기 */
	public static final int DEFAULT_PAGE_SIZE = 10;

	/** 최대 페이지 크기 */
	public static final int MAX_PAGE_SIZE = 50;

	// ========== 퀴즈 피드백 ==========
	public static final String FEEDBACK_PERFECT = "Perfect! You understood the article completely.";
	public static final String FEEDBACK_GREAT = "Great job! You have a solid understanding of the article.";
	public static final String FEEDBACK_GOOD = "Good effort! Review the highlighted words for better comprehension.";
	public static final String FEEDBACK_KEEP_PRACTICING = "Keep practicing! Try reading the article again before retaking the quiz.";
	public static final String FEEDBACK_DONT_GIVE_UP = "Don't give up! Focus on vocabulary and main ideas.";

	// ========== Score 기준 ==========
	public static final int SCORE_PERFECT = 100;
	public static final int SCORE_GREAT_THRESHOLD = 80;
	public static final int SCORE_GOOD_THRESHOLD = 60;
	public static final int SCORE_KEEP_PRACTICING_THRESHOLD = 40;

	// ========== Getter Methods ==========
	public static String bucketName() {
		return BUCKET_NAME;
	}

	/**
	 * 점수에 따른 피드백 생성
	 */
	public static String getFeedbackByScore(int score) {
		if (score == SCORE_PERFECT) {
			return FEEDBACK_PERFECT;
		} else if (score >= SCORE_GREAT_THRESHOLD) {
			return FEEDBACK_GREAT;
		} else if (score >= SCORE_GOOD_THRESHOLD) {
			return FEEDBACK_GOOD;
		} else if (score >= SCORE_KEEP_PRACTICING_THRESHOLD) {
			return FEEDBACK_KEEP_PRACTICING;
		} else {
			return FEEDBACK_DONT_GIVE_UP;
		}
	}

	/**
	 * limit 값 파싱 및 유효성 검증
	 */
	public static int parseLimit(String limitStr) {
		if (limitStr == null) {
			return DEFAULT_PAGE_SIZE;
		}
		try {
			int limit = Integer.parseInt(limitStr);
			return Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
		} catch (NumberFormatException e) {
			return DEFAULT_PAGE_SIZE;
		}
	}
}
