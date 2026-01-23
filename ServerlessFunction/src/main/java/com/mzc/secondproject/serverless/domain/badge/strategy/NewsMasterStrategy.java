package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 뉴스 마스터 뱃지 조건 전략
 * 읽기 100개 + 퀴즈 50회 + 단어 100개 달성 시 획득
 */
public class NewsMasterStrategy implements BadgeConditionStrategy {

	private static final int NEWS_READ_REQUIRED = 100;
	private static final int NEWS_QUIZ_REQUIRED = 50;
	private static final int NEWS_WORD_REQUIRED = 100;

	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		int newsRead = stats.getNewsRead() != null ? stats.getNewsRead() : 0;
		int newsQuiz = stats.getNewsQuizCompleted() != null ? stats.getNewsQuizCompleted() : 0;
		int newsWord = stats.getNewsWordsCollected() != null ? stats.getNewsWordsCollected() : 0;

		return newsRead >= NEWS_READ_REQUIRED
				&& newsQuiz >= NEWS_QUIZ_REQUIRED
				&& newsWord >= NEWS_WORD_REQUIRED;
	}

	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		int newsRead = stats.getNewsRead() != null ? stats.getNewsRead() : 0;
		int newsQuiz = stats.getNewsQuizCompleted() != null ? stats.getNewsQuizCompleted() : 0;
		int newsWord = stats.getNewsWordsCollected() != null ? stats.getNewsWordsCollected() : 0;

		// 3가지 조건의 평균 진행률 (각각 100%, 100%, 100% 기준)
		int readProgress = Math.min(newsRead * 100 / NEWS_READ_REQUIRED, 100);
		int quizProgress = Math.min(newsQuiz * 100 / NEWS_QUIZ_REQUIRED, 100);
		int wordProgress = Math.min(newsWord * 100 / NEWS_WORD_REQUIRED, 100);

		return (readProgress + quizProgress + wordProgress) / 3;
	}

	@Override
	public String getCategory() {
		return "NEWS_MASTER";
	}
}
