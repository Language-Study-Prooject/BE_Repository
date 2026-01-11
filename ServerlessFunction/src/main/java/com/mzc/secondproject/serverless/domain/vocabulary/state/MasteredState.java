package com.mzc.secondproject.serverless.domain.vocabulary.state;

import com.mzc.secondproject.serverless.common.config.StudyConfig;
import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus;

/**
 * 마스터 상태.
 * 정답 시 상태 유지, 오답 시 REVIEWING으로 강등.
 */
public class MasteredState implements WordState {

    private static final MasteredState INSTANCE = new MasteredState();

    private MasteredState() {}

    public static MasteredState getInstance() {
        return INSTANCE;
    }

    @Override
    public WordState onCorrectAnswer(SpacedRepetitionContext context) {
        context.incrementCorrectCount();
        context.incrementRepetitions();
        context.updateInterval(context.calculateNextInterval());
        return this;
    }

    @Override
    public WordState onWrongAnswer(SpacedRepetitionContext context) {
        context.incrementIncorrectCount();
        context.resetRepetitions();
        context.resetInterval();
        context.decreaseEaseFactor();
        return ReviewingState.getInstance();
    }

    @Override
    public int getIntervalDays(SpacedRepetitionContext context) {
        return context.getInterval();
    }

    @Override
    public String getStateName() {
        return WordStatus.MASTERED.name();
    }
}
