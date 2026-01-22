package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 테스트 완료 횟수 뱃지 조건 전략
 */
public class TestsCompletedStrategy implements BadgeConditionStrategy {
	
	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return stats.getTestsCompleted() != null && stats.getTestsCompleted() >= type.getThreshold();
	}
	
	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return stats.getTestsCompleted() != null ? stats.getTestsCompleted() : 0;
	}
	
	@Override
	public String getCategory() {
		return "TESTS_COMPLETED";
	}
}
