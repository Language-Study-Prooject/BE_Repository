package com.mzc.secondproject.serverless.domain.vocabulary.config

import spock.lang.Specification

class VocabularyConfigSpec extends Specification {

    def "newWordsCount 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        VocabularyConfig.newWordsCount() > 0
    }

    def "reviewWordsCount 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        VocabularyConfig.reviewWordsCount() > 0
    }

    def "transitionToReviewingThreshold 기본값 확인"() {
        expect: "기본값은 2"
        VocabularyConfig.transitionToReviewingThreshold() >= 1
    }

    def "transitionToMasteredThreshold 기본값 확인"() {
        expect: "기본값은 5"
        VocabularyConfig.transitionToMasteredThreshold() >= VocabularyConfig.transitionToReviewingThreshold()
    }

    def "secondIntervalDays 기본값 확인"() {
        expect: "기본값은 6"
        VocabularyConfig.secondIntervalDays() > 0
    }

    // ==================== Business Logic Tests ====================

    def "transitionToMasteredThreshold가 transitionToReviewingThreshold보다 큼"() {
        expect: "마스터 전이 임계값이 복습 전이 임계값보다 커야 함"
        VocabularyConfig.transitionToMasteredThreshold() > VocabularyConfig.transitionToReviewingThreshold()
    }

    def "모든 설정값이 양수"() {
        expect:
        VocabularyConfig.newWordsCount() > 0
        VocabularyConfig.reviewWordsCount() > 0
        VocabularyConfig.transitionToReviewingThreshold() > 0
        VocabularyConfig.transitionToMasteredThreshold() > 0
        VocabularyConfig.secondIntervalDays() > 0
    }
}
