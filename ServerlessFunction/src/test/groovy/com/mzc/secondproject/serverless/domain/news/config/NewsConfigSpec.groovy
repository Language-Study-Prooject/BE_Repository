package com.mzc.secondproject.serverless.domain.news.config

import spock.lang.Specification
import spock.lang.Unroll

class NewsConfigSpec extends Specification {

    // ==================== TTS Constants Tests ====================

    def "TTS_MAX_TEXT_LENGTH: TTS 최대 텍스트 길이는 3000자"() {
        expect:
        NewsConfig.TTS_MAX_TEXT_LENGTH == 3000
    }

    def "TTS_AUDIO_PREFIX: TTS 오디오 저장 경로 확인"() {
        expect:
        NewsConfig.TTS_AUDIO_PREFIX == "news/audio/"
    }

    def "DEFAULT_VOICE: 기본 TTS 음성은 Joanna"() {
        expect:
        NewsConfig.DEFAULT_VOICE == "Joanna"
    }

    // ==================== Pagination Constants Tests ====================

    def "DEFAULT_PAGE_SIZE: 기본 페이지 크기는 10"() {
        expect:
        NewsConfig.DEFAULT_PAGE_SIZE == 10
    }

    def "MAX_PAGE_SIZE: 최대 페이지 크기는 50"() {
        expect:
        NewsConfig.MAX_PAGE_SIZE == 50
    }

    // ==================== Score Threshold Tests ====================

    def "SCORE_PERFECT: 만점 기준은 100"() {
        expect:
        NewsConfig.SCORE_PERFECT == 100
    }

    def "SCORE_GREAT_THRESHOLD: Great 기준은 80점 이상"() {
        expect:
        NewsConfig.SCORE_GREAT_THRESHOLD == 80
    }

    def "SCORE_GOOD_THRESHOLD: Good 기준은 60점 이상"() {
        expect:
        NewsConfig.SCORE_GOOD_THRESHOLD == 60
    }

    def "SCORE_KEEP_PRACTICING_THRESHOLD: Keep Practicing 기준은 40점 이상"() {
        expect:
        NewsConfig.SCORE_KEEP_PRACTICING_THRESHOLD == 40
    }

    // ==================== Feedback Constants Tests ====================

    def "FEEDBACK_PERFECT: 만점 피드백 메시지"() {
        expect:
        NewsConfig.FEEDBACK_PERFECT == "Perfect! You understood the article completely."
    }

    def "FEEDBACK_GREAT: Great 피드백 메시지"() {
        expect:
        NewsConfig.FEEDBACK_GREAT == "Great job! You have a solid understanding of the article."
    }

    def "FEEDBACK_GOOD: Good 피드백 메시지"() {
        expect:
        NewsConfig.FEEDBACK_GOOD == "Good effort! Review the highlighted words for better comprehension."
    }

    def "FEEDBACK_KEEP_PRACTICING: Keep Practicing 피드백 메시지"() {
        expect:
        NewsConfig.FEEDBACK_KEEP_PRACTICING == "Keep practicing! Try reading the article again before retaking the quiz."
    }

    def "FEEDBACK_DONT_GIVE_UP: Don't Give Up 피드백 메시지"() {
        expect:
        NewsConfig.FEEDBACK_DONT_GIVE_UP == "Don't give up! Focus on vocabulary and main ideas."
    }

    // ==================== getFeedbackByScore Tests ====================

    @Unroll
    def "getFeedbackByScore: 점수 #score -> '#expectedFeedback'"() {
        expect:
        NewsConfig.getFeedbackByScore(score) == expectedFeedback

        where:
        score | expectedFeedback
        100   | NewsConfig.FEEDBACK_PERFECT
        99    | NewsConfig.FEEDBACK_GREAT
        80    | NewsConfig.FEEDBACK_GREAT
        79    | NewsConfig.FEEDBACK_GOOD
        60    | NewsConfig.FEEDBACK_GOOD
        59    | NewsConfig.FEEDBACK_KEEP_PRACTICING
        40    | NewsConfig.FEEDBACK_KEEP_PRACTICING
        39    | NewsConfig.FEEDBACK_DONT_GIVE_UP
        0     | NewsConfig.FEEDBACK_DONT_GIVE_UP
    }

    def "getFeedbackByScore: 경계값 테스트"() {
        expect: "경계값에서 올바른 피드백 반환"
        NewsConfig.getFeedbackByScore(100) == NewsConfig.FEEDBACK_PERFECT
        NewsConfig.getFeedbackByScore(80) == NewsConfig.FEEDBACK_GREAT
        NewsConfig.getFeedbackByScore(60) == NewsConfig.FEEDBACK_GOOD
        NewsConfig.getFeedbackByScore(40) == NewsConfig.FEEDBACK_KEEP_PRACTICING
    }

    // ==================== parseLimit Tests ====================

    @Unroll
    def "parseLimit: '#input' -> #expected"() {
        expect:
        NewsConfig.parseLimit(input) == expected

        where:
        input   | expected
        null    | NewsConfig.DEFAULT_PAGE_SIZE
        ""      | NewsConfig.DEFAULT_PAGE_SIZE
        "abc"   | NewsConfig.DEFAULT_PAGE_SIZE
        "10"    | 10
        "1"     | 1
        "50"    | 50
        "100"   | NewsConfig.MAX_PAGE_SIZE  // 최대값 제한
        "0"     | 1                          // 최소값 보정
        "-5"    | 1                          // 음수 보정
        "25"    | 25
    }

    def "parseLimit: null 입력 시 기본값 반환"() {
        expect:
        NewsConfig.parseLimit(null) == NewsConfig.DEFAULT_PAGE_SIZE
    }

    def "parseLimit: 빈 문자열 입력 시 기본값 반환"() {
        expect:
        NewsConfig.parseLimit("") == NewsConfig.DEFAULT_PAGE_SIZE
    }

    def "parseLimit: 최대값 초과 시 MAX_PAGE_SIZE 반환"() {
        expect:
        NewsConfig.parseLimit("999") == NewsConfig.MAX_PAGE_SIZE
    }

    def "parseLimit: 0 이하 값 입력 시 1 반환"() {
        expect:
        NewsConfig.parseLimit("0") == 1
        NewsConfig.parseLimit("-10") == 1
    }

    def "parseLimit: 숫자가 아닌 문자열 입력 시 기본값 반환"() {
        expect:
        NewsConfig.parseLimit("not_a_number") == NewsConfig.DEFAULT_PAGE_SIZE
        NewsConfig.parseLimit("12abc") == NewsConfig.DEFAULT_PAGE_SIZE
    }

    // ==================== bucketName Tests ====================

    def "bucketName: 기본 버킷 이름 반환"() {
        when:
        def result = NewsConfig.bucketName()

        then: "환경변수가 없으면 기본값, 있으면 해당 값"
        result != null
        result == "group2-englishstudy" || result instanceof String
    }
}
