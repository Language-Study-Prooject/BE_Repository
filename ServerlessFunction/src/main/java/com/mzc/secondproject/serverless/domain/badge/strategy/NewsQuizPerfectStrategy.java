package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 뉴스 퀴즈 만점 뱃지 조건 전략
 */
public class NewsQuizPerfectStrategy implements BadgeConditionStrategy {

	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return stats.getNewsQuizPerfect() != null && stats.getNewsQuizPerfect() >= type.getThreshold();
	}

	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return stats.getNewsQuizPerfect() != null ? stats.getNewsQuizPerfect() : 0;
	}

	@Override
	public String getCategory() {
		return "NEWS_QUIZ_PERFECT";
	}
}
