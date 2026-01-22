package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 별도 로직이 필요한 뱃지용 No-Op 전략
 * PERFECT_TEST, ALL_BADGES 등은 별도 로직에서 처리
 */
public class NoOpStrategy implements BadgeConditionStrategy {

	private final String category;

	public NoOpStrategy(String category) {
		this.category = category;
	}

	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return false;
	}

	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return 0;
	}

	@Override
	public String getCategory() {
		return category;
	}
}
