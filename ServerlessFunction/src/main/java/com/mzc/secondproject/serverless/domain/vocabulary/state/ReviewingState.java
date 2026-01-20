package com.mzc.secondproject.serverless.domain.vocabulary.state;

import com.mzc.secondproject.serverless.domain.vocabulary.config.VocabularyConfig;
import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus;

/**
 * 복습 중 상태.
 * repetitions >= 5 시 MASTERED로 전이.
 */
public class ReviewingState implements WordState {
	
	private static final ReviewingState INSTANCE = new ReviewingState();
	
	private ReviewingState() {
	}
	
	public static ReviewingState getInstance() {
		return INSTANCE;
	}
	
	@Override
	public WordState onCorrectAnswer(SpacedRepetitionContext context) {
		context.incrementCorrectCount();
		context.incrementRepetitions();
		context.updateInterval(context.calculateNextInterval());
		
		if (context.getRepetitions() >= VocabularyConfig.transitionToMasteredThreshold()) {
			return MasteredState.getInstance();
		}
		return this;
	}
	
	@Override
	public WordState onWrongAnswer(SpacedRepetitionContext context) {
		context.incrementIncorrectCount();
		context.resetRepetitions();
		context.resetInterval();
		context.decreaseEaseFactor();
		return LearningState.getInstance();
	}
	
	@Override
	public int getIntervalDays(SpacedRepetitionContext context) {
		return context.getInterval();
	}
	
	@Override
	public String getStateName() {
		return WordStatus.REVIEWING.name();
	}
}
