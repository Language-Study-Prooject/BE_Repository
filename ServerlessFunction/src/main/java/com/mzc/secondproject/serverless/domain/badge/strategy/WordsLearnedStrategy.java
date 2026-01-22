package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 단어 학습량 뱃지 조건 전략
 */
public class WordsLearnedStrategy implements BadgeConditionStrategy {
	
	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		int total = getTotalWordsLearned(stats);
		return total >= type.getThreshold();
	}
	
	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return getTotalWordsLearned(stats);
	}
	
	@Override
	public String getCategory() {
		return "WORDS_LEARNED";
	}
	
	private int getTotalWordsLearned(UserStats stats) {
		int newWords = stats.getNewWordsLearned() != null ? stats.getNewWordsLearned() : 0;
		int reviewedWords = stats.getWordsReviewed() != null ? stats.getWordsReviewed() : 0;
		return newWords + reviewedWords;
	}
}
