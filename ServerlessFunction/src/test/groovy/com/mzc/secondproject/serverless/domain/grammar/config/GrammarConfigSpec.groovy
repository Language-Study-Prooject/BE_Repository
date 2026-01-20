package com.mzc.secondproject.serverless.domain.grammar.config

import spock.lang.Specification

class GrammarConfigSpec extends Specification {

    def "sessionTtlDays 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GrammarConfig.sessionTtlDays() > 0
    }

    def "maxHistoryMessages 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GrammarConfig.maxHistoryMessages() > 0
    }

    def "lastMessageMaxLength 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GrammarConfig.lastMessageMaxLength() > 0
    }

    def "maxTokens 기본값 확인"() {
        expect: "환경 변수 미설정 시 기본값 반환"
        GrammarConfig.maxTokens() > 0
    }

    // ==================== Business Logic Tests ====================

    def "모든 설정값이 양수"() {
        expect:
        GrammarConfig.sessionTtlDays() > 0
        GrammarConfig.maxHistoryMessages() > 0
        GrammarConfig.lastMessageMaxLength() > 0
        GrammarConfig.maxTokens() > 0
    }

    def "maxTokens가 합리적인 범위"() {
        expect: "토큰 수는 1000 이상이어야 함"
        GrammarConfig.maxTokens() >= 1000
    }

    def "maxHistoryMessages가 합리적인 범위"() {
        expect: "히스토리 메시지 수는 1~100 범위"
        GrammarConfig.maxHistoryMessages() >= 1
        GrammarConfig.maxHistoryMessages() <= 100
    }
}
