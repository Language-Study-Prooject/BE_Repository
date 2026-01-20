package com.mzc.secondproject.serverless.common.config

import spock.lang.Specification

class StudyConfigSpec extends Specification {

    // ==================== Spaced Repetition Config Tests ====================

    def "Spaced Repetition 기본값 확인"() {
        expect:
        StudyConfig.INITIAL_INTERVAL_DAYS == 1
        StudyConfig.DEFAULT_EASE_FACTOR == 2.5d
        StudyConfig.MIN_EASE_FACTOR == 1.3d
        StudyConfig.INITIAL_REPETITIONS == 0
    }

    def "오답 관련 기본값 확인"() {
        expect:
        StudyConfig.MAX_WRONG_COUNT == 3
    }

    def "테스트 관련 기본값 확인"() {
        expect:
        StudyConfig.DEFAULT_WORD_COUNT == 20
        StudyConfig.DAILY_TEST_WORD_COUNT == 10
    }

    def "복습 간격 배열 확인"() {
        expect:
        StudyConfig.REVIEW_INTERVALS == [1, 3, 7, 14, 30] as int[]
        StudyConfig.REVIEW_INTERVALS.length == 5
    }

    def "상태 기본값 확인"() {
        expect:
        StudyConfig.DEFAULT_WORD_STATUS == "NEW"
        StudyConfig.DEFAULT_DIFFICULTY == "NORMAL"
    }

    def "카운트 초기값 확인"() {
        expect:
        StudyConfig.INITIAL_CORRECT_COUNT == 0
        StudyConfig.INITIAL_INCORRECT_COUNT == 0
    }

    // ==================== Business Logic Tests ====================

    def "MIN_EASE_FACTOR가 DEFAULT_EASE_FACTOR보다 작음"() {
        expect:
        StudyConfig.MIN_EASE_FACTOR < StudyConfig.DEFAULT_EASE_FACTOR
    }

    def "REVIEW_INTERVALS가 오름차순 정렬"() {
        given:
        def intervals = StudyConfig.REVIEW_INTERVALS

        expect: "배열이 오름차순 정렬되어 있음"
        (0..<intervals.length - 1).every { i ->
            intervals[i] < intervals[i + 1]
        }
    }

    def "DAILY_TEST_WORD_COUNT가 DEFAULT_WORD_COUNT보다 작음"() {
        expect:
        StudyConfig.DAILY_TEST_WORD_COUNT <= StudyConfig.DEFAULT_WORD_COUNT
    }
}
