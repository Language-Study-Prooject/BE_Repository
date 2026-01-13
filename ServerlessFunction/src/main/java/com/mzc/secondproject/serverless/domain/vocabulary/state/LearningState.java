package com.mzc.secondproject.serverless.domain.vocabulary.state;

import com.mzc.secondproject.serverless.common.config.StudyConfig;
import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus;

/**
 * 학습 중 상태.
 * repetitions >= 2 시 REVIEWING으로 전이.
 */
public class LearningState implements WordState {
	
	private static final LearningState INSTANCE = new LearningState();
	private static final int TRANSITION_TO_REVIEWING_THRESHOLD = 2;
	private static final int SECOND_INTERVAL_DAYS = 6;
	
	private LearningState() {
	}
	
	public static LearningState getInstance() {
		return INSTANCE;
	}
	
	@Override
	public WordState onCorrectAnswer(SpacedRepetitionContext context) {
		context.incrementCorrectCount();
		context.incrementRepetitions();
		
		int repetitions = context.getRepetitions();
		if (repetitions == 1) {
			context.updateInterval(StudyConfig.INITIAL_INTERVAL_DAYS);
		} else if (repetitions == 2) {
			context.updateInterval(SECOND_INTERVAL_DAYS);
		} else {
			context.updateInterval(context.calculateNextInterval());
		}
		
		if (repetitions >= TRANSITION_TO_REVIEWING_THRESHOLD) {
			return ReviewingState.getInstance();
		}
		return this;
	}
	
	@Override
	public WordState onWrongAnswer(SpacedRepetitionContext context) {
		context.incrementIncorrectCount();
		context.resetRepetitions();
		context.resetInterval();
		context.decreaseEaseFactor();
		return this;
	}
	
	@Override
	public int getIntervalDays(SpacedRepetitionContext context) {
		return context.getInterval();
	}
	
	@Override
	public String getStateName() {
		return WordStatus.LEARNING.name();
	}
}
