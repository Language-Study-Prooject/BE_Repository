package com.mzc.secondproject.serverless.domain.badge.strategy;

import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;

/**
 * 뱃지 조건 확인 전략 인터페이스
 */
public interface BadgeConditionStrategy {
	
	/**
	 * 뱃지 획득 조건 확인
	 */
	boolean checkCondition(BadgeType type, UserStats stats);
	
	/**
	 * 현재 진행도 계산
	 */
	int calculateProgress(BadgeType type, UserStats stats);
	
	/**
	 * 지원하는 카테고리
	 */
	String getCategory();
}
