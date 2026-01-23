package com.mzc.secondproject.serverless.domain.badge.strategy;

import java.util.HashMap;
import java.util.Map;

/**
 * 뱃지 조건 전략 팩토리
 * 카테고리별 전략 인스턴스를 관리하고 제공
 */
public class BadgeConditionStrategyFactory {
	
	private static final Map<String, BadgeConditionStrategy> STRATEGIES = new HashMap<>();
	private static final BadgeConditionStrategy DEFAULT_STRATEGY = new NoOpStrategy("DEFAULT");
	
	static {
		register(new FirstStudyStrategy());
		register(new StreakStrategy());
		register(new WordsLearnedStrategy());
		register(new TestsCompletedStrategy());
		register(new AccuracyStrategy());
		register(new GamesPlayedStrategy());
		register(new GamesWonStrategy());
		register(new QuickGuessesStrategy());
		register(new PerfectDrawsStrategy());
		// 뉴스 관련 전략
		register(new NewsReadStrategy());
		register(new NewsQuizStrategy());
		register(new NewsQuizPerfectStrategy());
		register(new NewsWordStrategy());
		register(new NewsStreakStrategy());
		register(new NewsMasterStrategy());
		// 별도 로직이 필요한 카테고리
		register(new NoOpStrategy("PERFECT_TEST"));
		register(new NoOpStrategy("ALL_BADGES"));
	}
	
	private static void register(BadgeConditionStrategy strategy) {
		STRATEGIES.put(strategy.getCategory(), strategy);
	}
	
	/**
	 * 카테고리에 해당하는 전략 반환
	 */
	public static BadgeConditionStrategy getStrategy(String category) {
		return STRATEGIES.getOrDefault(category, DEFAULT_STRATEGY);
	}
}
