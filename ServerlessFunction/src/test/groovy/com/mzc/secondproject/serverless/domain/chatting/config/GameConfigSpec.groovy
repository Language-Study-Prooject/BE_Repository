package com.mzc.secondproject.serverless.domain.chatting.config

import spock.lang.Specification

class GameConfigSpec extends Specification {

    def "totalRounds 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GameConfig.totalRounds() > 0
    }

    def "roundTimeLimit 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GameConfig.roundTimeLimit() > 0
    }

    def "quickGuessThresholdMs 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GameConfig.quickGuessThresholdMs() > 0
    }

    // ==================== Business Logic Tests ====================

    def "모든 설정값이 양수"() {
        expect:
        GameConfig.totalRounds() > 0
        GameConfig.roundTimeLimit() > 0
        GameConfig.quickGuessThresholdMs() > 0
    }

    def "quickGuessThresholdMs가 roundTimeLimit보다 작음"() {
        expect: "빠른 추측 임계값(ms)이 라운드 시간 제한(초)을 ms로 변환한 값보다 작아야 함"
        GameConfig.quickGuessThresholdMs() < GameConfig.roundTimeLimit() * 1000L
    }

    def "totalRounds가 합리적인 범위"() {
        expect: "라운드 수는 1~20 범위"
        GameConfig.totalRounds() >= 1
        GameConfig.totalRounds() <= 20
    }

    def "roundTimeLimit가 합리적인 범위"() {
        expect: "라운드 시간 제한은 10~300초 범위"
        GameConfig.roundTimeLimit() >= 10
        GameConfig.roundTimeLimit() <= 300
    }
}
