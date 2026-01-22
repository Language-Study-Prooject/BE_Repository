package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 정확도 뱃지 조건 전략
 */
public class AccuracyStrategy implements BadgeConditionStrategy {
	
	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		double accuracy = calculateAccuracy(stats);
		return accuracy >= type.getThreshold();
	}
	
	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return (int) calculateAccuracy(stats);
	}
	
	@Override
	public String getCategory() {
		return "ACCURACY";
	}
	
	private double calculateAccuracy(UserStats stats) {
		if (stats.getQuestionsAnswered() == null || stats.getQuestionsAnswered() == 0) {
			return 0.0;
		}
		int correct = stats.getCorrectAnswers() != null ? stats.getCorrectAnswers() : 0;
		return (correct * 100.0) / stats.getQuestionsAnswered();
	}
}
