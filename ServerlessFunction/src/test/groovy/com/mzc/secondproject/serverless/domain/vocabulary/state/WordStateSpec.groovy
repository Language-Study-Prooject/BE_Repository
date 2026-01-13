package com.mzc.secondproject.serverless.domain.vocabulary.state

import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WordStateSpec extends Specification {

    @Subject
    SpacedRepetitionContext context

    def setup() {
        context = new SpacedRepetitionContext()
    }

    // ==================== NewState Tests ====================

    def "NewState: 정답 시 LEARNING으로 전이"() {
        given: "새 단어 상태"
        def state = NewState.getInstance()

        when: "정답 처리"
        def nextState = state.onCorrectAnswer(context)

        then: "LEARNING 상태로 전이"
        nextState instanceof LearningState
        nextState.getStateName() == WordStatus.LEARNING.name()

        and: "정답 카운트 증가"
        context.getCorrectCount() == 1
        context.getRepetitions() == 1
    }

    def "NewState: 오답 시 LEARNING으로 전이하고 easeFactor 감소"() {
        given: "새 단어 상태"
        def state = NewState.getInstance()
        def initialEaseFactor = context.getEaseFactor()

        when: "오답 처리"
        def nextState = state.onWrongAnswer(context)

        then: "LEARNING 상태로 전이"
        nextState instanceof LearningState

                and : "오답 카운트 증가, easeFactor 감소"
        context.getIncorrectCount() == 1
        context.getEaseFactor() < initialEaseFactor
    }

    // ==================== LearningState Tests ====================

    def "LearningState: 첫 정답 시 상태 유지"() {
        given: "학습 중 상태 (repetitions=0)"
        def state = LearningState.getInstance()

        when: "정답 처리"
        def nextState = state.onCorrectAnswer(context)

        then: "LEARNING 상태 유지"
        nextState instanceof LearningState
        context.getRepetitions() == 1
    }

    def "LearningState: 2회 연속 정답 시 REVIEWING으로 전이"() {
        given: "학습 중 상태 (repetitions=1)"
        context = new SpacedRepetitionContext(1, 1, 2.5, 1, 0)
        def state = LearningState.getInstance()

        when: "정답 처리"
        def nextState = state.onCorrectAnswer(context)

        then: "REVIEWING 상태로 전이"
        nextState instanceof ReviewingState
        context.getRepetitions() == 2
        context.getInterval() == 6
    }

    def "LearningState: 오답 시 repetitions 리셋"() {
        given: "학습 중 상태 (repetitions=1)"
        context = new SpacedRepetitionContext(1, 6, 2.5, 1, 0)
        def state = LearningState.getInstance()

        when: "오답 처리"
        def nextState = state.onWrongAnswer(context)

        then: "LEARNING 상태 유지, repetitions 리셋"
        nextState instanceof LearningState
        context.getRepetitions() == 0
        context.getInterval() == 1
    }

    // ==================== ReviewingState Tests ====================

    def "ReviewingState: 5회 연속 정답 시 MASTERED로 전이"() {
        given: "복습 중 상태 (repetitions=4)"
        context = new SpacedRepetitionContext(4, 14, 2.5, 4, 0)
        def state = ReviewingState.getInstance()

        when: "정답 처리"
        def nextState = state.onCorrectAnswer(context)

        then: "MASTERED 상태로 전이"
        nextState instanceof MasteredState
        context.getRepetitions() == 5
    }

    def "ReviewingState: 4회 정답 시 상태 유지"() {
        given: "복습 중 상태 (repetitions=3)"
        context = new SpacedRepetitionContext(3, 7, 2.5, 3, 0)
        def state = ReviewingState.getInstance()

        when: "정답 처리"
        def nextState = state.onCorrectAnswer(context)

        then: "REVIEWING 상태 유지"
        nextState instanceof ReviewingState
        context.getRepetitions() == 4
    }

    def "ReviewingState: 오답 시 LEARNING으로 강등"() {
        given: "복습 중 상태"
        context = new SpacedRepetitionContext(3, 7, 2.5, 3, 0)
        def state = ReviewingState.getInstance()

        when: "오답 처리"
        def nextState = state.onWrongAnswer(context)

        then: "LEARNING 상태로 강등"
        nextState instanceof LearningState
        context.getRepetitions() == 0
    }

    // ==================== MasteredState Tests ====================

    def "MasteredState: 정답 시 상태 유지"() {
        given: "마스터 상태"
        context = new SpacedRepetitionContext(5, 30, 2.5, 5, 0)
        def state = MasteredState.getInstance()

        when: "정답 처리"
        def nextState = state.onCorrectAnswer(context)

        then: "MASTERED 상태 유지"
        nextState instanceof MasteredState
        context.getRepetitions() == 6
    }

    def "MasteredState: 오답 시 REVIEWING으로 강등"() {
        given: "마스터 상태"
        context = new SpacedRepetitionContext(5, 30, 2.5, 5, 0)
        def state = MasteredState.getInstance()

        when: "오답 처리"
        def nextState = state.onWrongAnswer(context)

        then: "REVIEWING 상태로 강등"
        nextState instanceof ReviewingState
        context.getRepetitions() == 0
    }

    // ==================== WordStateFactory Tests ====================

    @Unroll
    def "WordStateFactory: '#stateName' -> #expectedType"() {
        when: "상태 이름으로 State 객체 생성"
        def state = WordStateFactory.fromString(stateName)

        then: "올바른 타입 반환"
        state.class == expectedType

        where:
        stateName   | expectedType
        "NEW"       | NewState
        "LEARNING"  | LearningState
        "REVIEWING" | ReviewingState
        "MASTERED"  | MasteredState
        "new"       | NewState
        "learning"  | LearningState
        null        | NewState
        "INVALID"   | NewState
    }

    // ==================== Interval Calculation Tests ====================

    def "interval 계산: easeFactor 적용"() {
        given: "특정 interval과 easeFactor"
        context = new SpacedRepetitionContext(2, 6, 2.5, 2, 0)
        def state = ReviewingState.getInstance()

        when: "정답 처리 (3번째)"
        state.onCorrectAnswer(context)

        then: "interval = 6 * 2.5 = 15"
        context.getInterval() == 15
    }

    def "easeFactor 최소값 보장"() {
        given: "낮은 easeFactor (연속 오답 시뮬레이션)"
        context = new SpacedRepetitionContext(0, 1, 1.4, 0, 5)
        def state = LearningState.getInstance()

        when: "오답 처리"
        state.onWrongAnswer(context)

        then: "easeFactor >= 1.3 유지"
        context.getEaseFactor() >= 1.3
    }
}
