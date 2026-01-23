package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 뉴스 연속 읽기 뱃지 조건 전략
 */
public class NewsStreakStrategy implements BadgeConditionStrategy {

	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return stats.getNewsStreak() != null && stats.getNewsStreak() >= type.getThreshold();
	}

	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return stats.getNewsStreak() != null ? stats.getNewsStreak() : 0;
	}

	@Override
	public String getCategory() {
		return "NEWS_STREAK";
	}
}
