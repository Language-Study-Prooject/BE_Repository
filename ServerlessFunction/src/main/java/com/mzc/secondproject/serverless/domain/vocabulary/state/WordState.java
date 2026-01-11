package com.mzc.secondproject.serverless.domain.vocabulary.state;

/**
 * 단어 학습 상태를 나타내는 State 패턴 인터페이스.
 * 각 상태는 정답/오답에 따른 상태 전이와 복습 간격을 결정한다.
 */
public interface WordState {

    /**
     * 정답 시 다음 상태로 전이
     * @param context Spaced Repetition 컨텍스트
     * @return 전이된 상태
     */
    WordState onCorrectAnswer(SpacedRepetitionContext context);

    /**
     * 오답 시 다음 상태로 전이
     * @param context Spaced Repetition 컨텍스트
     * @return 전이된 상태
     */
    WordState onWrongAnswer(SpacedRepetitionContext context);

    /**
     * 현재 상태의 복습 간격(일) 반환
     * @param context Spaced Repetition 컨텍스트
     * @return 복습 간격 (일 단위)
     */
    int getIntervalDays(SpacedRepetitionContext context);

    /**
     * 상태 이름 반환 (DynamoDB 저장용)
     * @return 상태 이름 (NEW, LEARNING, REVIEWING, MASTERED)
     */
    String getStateName();
}
