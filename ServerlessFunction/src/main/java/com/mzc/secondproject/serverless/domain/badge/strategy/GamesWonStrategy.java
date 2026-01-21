package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 게임 승리 횟수 뱃지 조건 전략
 */
public class GamesWonStrategy implements BadgeConditionStrategy {

	@Override
	public boolean checkCondition(BadgeType type, UserStats stats) {
		return stats.getGamesWon() != null && stats.getGamesWon() >= type.getThreshold();
	}

	@Override
	public int calculateProgress(BadgeType type, UserStats stats) {
		return stats.getGamesWon() != null ? stats.getGamesWon() : 0;
	}

	@Override
	public String getCategory() {
		return "GAMES_WON";
	}
}
