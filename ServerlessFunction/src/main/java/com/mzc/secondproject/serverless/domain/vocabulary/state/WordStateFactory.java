package com.mzc.secondproject.serverless.domain.vocabulary.state;

import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus;

/**
 * WordState 인스턴스 생성 팩토리.
 * 상태 이름(String)으로부터 적절한 State 객체를 반환한다.
 */
public final class WordStateFactory {

    private WordStateFactory() {}

    /**
     * 상태 이름으로부터 WordState 인스턴스 반환
     * @param stateName 상태 이름 (NEW, LEARNING, REVIEWING, MASTERED)
     * @return 해당 상태의 WordState 인스턴스
     */
    public static WordState fromString(String stateName) {
        if (stateName == null) {
            return NewState.getInstance();
        }

        WordStatus status = WordStatus.fromStringOrDefault(stateName, WordStatus.NEW);
        return fromStatus(status);
    }

    /**
     * WordStatus enum으로부터 WordState 인스턴스 반환
     * @param status WordStatus enum
     * @return 해당 상태의 WordState 인스턴스
     */
    public static WordState fromStatus(WordStatus status) {
        return switch (status) {
            case NEW -> NewState.getInstance();
            case LEARNING -> LearningState.getInstance();
            case REVIEWING -> ReviewingState.getInstance();
            case MASTERED -> MasteredState.getInstance();
        };
    }

    /**
     * 기본 상태(NEW) 반환
     * @return NewState 인스턴스
     */
    public static WordState getInitialState() {
        return NewState.getInstance();
    }
}
