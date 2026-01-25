package com.mzc.secondproject.serverless.domain.vocabulary.state

import com.mzc.secondproject.serverless.common.config.StudyConfig
import spock.lang.Specification

class SpacedRepetitionContextSpec extends Specification {

    // ==================== 생성자 Tests ====================

    def "기본 생성자: 초기값 설정 확인"() {
        when:
        def context = new SpacedRepetitionContext()

        then:
        context.getRepetitions() == StudyConfig.INITIAL_REPETITIONS
        context.getInterval() == StudyConfig.INITIAL_INTERVAL_DAYS
        context.getEaseFactor() == StudyConfig.DEFAULT_EASE_FACTOR
        context.getCorrectCount() == StudyConfig.INITIAL_CORRECT_COUNT
        context.getIncorrectCount() == StudyConfig.INITIAL_INCORRECT_COUNT
    }

    def "매개변수 생성자: 지정값 설정 확인"() {
        given:
        int repetitions = 3
        int interval = 7
        double easeFactor = 2.0
        int correctCount = 5
        int incorrectCount = 2

        when:
        def context = new SpacedRepetitionContext(repetitions, interval, easeFactor, correctCount, incorrectCount)

        then:
        context.getRepetitions() == 3
        context.getInterval() == 7
        context.getEaseFactor() == 2.0
        context.getCorrectCount() == 5
        context.getIncorrectCount() == 2
    }

    // ==================== Repetition Tests ====================

    def "incrementRepetitions: 반복 횟수 증가"() {
        given:
        def context = new SpacedRepetitionContext()
        def initial = context.getRepetitions()

        when:
        context.incrementRepetitions()

        then:
        context.getRepetitions() == initial + 1
    }

    def "resetRepetitions: 반복 횟수 초기화"() {
        given:
        def context = new SpacedRepetitionContext(5, 10, 2.5, 5, 0)

        when:
        context.resetRepetitions()

        then:
        context.getRepetitions() == StudyConfig.INITIAL_REPETITIONS
    }

    // ==================== Count Tests ====================

    def "incrementCorrectCount: 정답 카운트 증가"() {
        given:
        def context = new SpacedRepetitionContext()

        when:
        context.incrementCorrectCount()
        context.incrementCorrectCount()

        then:
        context.getCorrectCount() == 2
    }

    def "incrementIncorrectCount: 오답 카운트 증가"() {
        given:
        def context = new SpacedRepetitionContext()

        when:
        context.incrementIncorrectCount()

        then:
        context.getIncorrectCount() == 1
    }

    // ==================== Interval Tests ====================

    def "updateInterval: 간격 업데이트"() {
        given:
        def context = new SpacedRepetitionContext()

        when:
        context.updateInterval(14)

        then:
        context.getInterval() == 14
    }

    def "resetInterval: 간격 초기화"() {
        given:
        def context = new SpacedRepetitionContext(3, 30, 2.5, 3, 0)

        when:
        context.resetInterval()

        then:
        context.getInterval() == StudyConfig.INITIAL_INTERVAL_DAYS
    }

    // ==================== EaseFactor Tests ====================

    def "decreaseEaseFactor: easeFactor 감소"() {
        given:
        def context = new SpacedRepetitionContext()
        def initial = context.getEaseFactor()

        when:
        context.decreaseEaseFactor()

        then:
        context.getEaseFactor() == initial - 0.2
    }

    def "decreaseEaseFactor: 최소값 보장"() {
        given: "easeFactor가 최소값에 가까운 컨텍스트"
        def context = new SpacedRepetitionContext(0, 1, 1.4, 0, 0)

        when: "easeFactor 감소"
        context.decreaseEaseFactor()

        then: "최소값(1.3) 이하로 내려가지 않음"
        context.getEaseFactor() >= StudyConfig.MIN_EASE_FACTOR
    }

    def "decreaseEaseFactor: 연속 감소 시 최소값 유지"() {
        given:
        def context = new SpacedRepetitionContext()

        when: "여러 번 감소"
        10.times { context.decreaseEaseFactor() }

        then: "최소값 유지"
        context.getEaseFactor() == StudyConfig.MIN_EASE_FACTOR
    }

    // ==================== calculateNextInterval Tests ====================

    def "calculateNextInterval: interval * easeFactor 계산"() {
        given:
        def context = new SpacedRepetitionContext(2, 6, 2.5, 2, 0)

        when:
        def nextInterval = context.calculateNextInterval()

        then: "6 * 2.5 = 15"
        nextInterval == 15
    }

    def "calculateNextInterval: 소수점 반올림"() {
        given:
        def context = new SpacedRepetitionContext(3, 7, 2.5, 3, 0)

        when:
        def nextInterval = context.calculateNextInterval()

        then: "7 * 2.5 = 17.5 -> 18 (반올림)"
        nextInterval == 18
    }

    def "calculateNextInterval: 초기 상태"() {
        given:
        def context = new SpacedRepetitionContext()

        when:
        def nextInterval = context.calculateNextInterval()

        then: "1 * 2.5 = 2.5 -> 3 (반올림)"
        nextInterval == 3
    }

    // ==================== 복합 시나리오 Tests ====================

    def "학습 시나리오: 연속 정답 후 interval 증가"() {
        given:
        def context = new SpacedRepetitionContext()

        when: "3번 연속 정답"
        context.incrementCorrectCount()
        context.incrementRepetitions()
        context.updateInterval(1)

        context.incrementCorrectCount()
        context.incrementRepetitions()
        context.updateInterval(6)

        context.incrementCorrectCount()
        context.incrementRepetitions()
        context.updateInterval(context.calculateNextInterval())

        then:
        context.getRepetitions() == 3
        context.getCorrectCount() == 3
        context.getInterval() == 15  // 6 * 2.5
    }

    def "학습 시나리오: 오답 후 리셋"() {
        given: "진행 중인 학습 컨텍스트"
        def context = new SpacedRepetitionContext(3, 14, 2.5, 3, 0)

        when: "오답 처리"
        context.incrementIncorrectCount()
        context.resetRepetitions()
        context.resetInterval()
        context.decreaseEaseFactor()

        then:
        context.getRepetitions() == 0
        context.getInterval() == 1
        context.getIncorrectCount() == 1
        context.getEaseFactor() == 2.3
    }
}
