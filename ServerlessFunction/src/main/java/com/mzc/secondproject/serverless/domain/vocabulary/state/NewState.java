package com.mzc.secondproject.serverless.domain.vocabulary.state;

import com.mzc.secondproject.serverless.common.config.StudyConfig;
import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus;

/**
 * 새 단어 상태.
 * 첫 정답 시 LEARNING으로 전이.
 */
public class NewState implements WordState {

    private static final NewState INSTANCE = new NewState();

    private NewState() {}

    public static NewState getInstance() {
        return INSTANCE;
    }

    @Override
    public WordState onCorrectAnswer(SpacedRepetitionContext context) {
        context.incrementCorrectCount();
        context.incrementRepetitions();
        context.updateInterval(StudyConfig.INITIAL_INTERVAL_DAYS);
        return LearningState.getInstance();
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
        return StudyConfig.INITIAL_INTERVAL_DAYS;
    }

    @Override
    public String getStateName() {
        return WordStatus.NEW.name();
    }
}
