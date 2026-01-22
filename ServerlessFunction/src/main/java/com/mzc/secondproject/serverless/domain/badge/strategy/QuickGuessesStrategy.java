package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 빠른 정답 뱃지 조건 전략
 */
public class QuickGuessesStrategy implements BadgeConditionStrategy {
	
	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return stats.getQuickGuesses() != null && stats.getQuickGuesses() >= type.getThreshold();
	}
	
	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return stats.getQuickGuesses() != null ? stats.getQuickGuesses() : 0;
	}
	
	@Override
	public String getCategory() {
		return "QUICK_GUESSES";
	}
}
