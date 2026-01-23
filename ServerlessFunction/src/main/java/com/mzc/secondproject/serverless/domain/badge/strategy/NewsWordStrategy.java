package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 뉴스 단어 수집 뱃지 조건 전략
 */
public class NewsWordStrategy implements BadgeConditionStrategy {

	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return stats.getNewsWordsCollected() != null && stats.getNewsWordsCollected() >= type.getThreshold();
	}

	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return stats.getNewsWordsCollected() != null ? stats.getNewsWordsCollected() : 0;
	}

	@Override
	public String getCategory() {
		return "NEWS_WORD";
	}
}
