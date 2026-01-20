package com.mzc.secondproject.serverless.domain.grammar.exception

import spock.lang.Specification

class GrammarExceptionSpec extends Specification {

    // ==================== 요청 검증 관련 예외 Tests ====================

    def "invalidRequest: 필드와 사유 포함"() {
        when:
        def exception = GrammarException.invalidRequest("sentence", "문장이 비어있습니다")

        then:
        exception.getMessage().contains("잘못된 요청")
        exception.getErrorCode() == GrammarErrorCode.INVALID_REQUEST
        exception.getStatusCode() == 400
    }

    // ==================== 문법 체크 관련 예외 Tests ====================

    def "invalidSentence: 문장 포함"() {
        given:
        def sentence = "This is invalid."

        when:
        def exception = GrammarException.invalidSentence(sentence)

        then:
        exception.getMessage().contains("유효하지 않은 문장")
        exception.getErrorCode() == GrammarErrorCode.INVALID_SENTENCE
    }

    def "grammarCheckFailed: 사유 포함"() {
        given:
        def reason = "AI 서비스 연결 실패"

        when:
        def exception = GrammarException.grammarCheckFailed(reason)

        then:
        exception.getMessage().contains(reason)
        exception.getErrorCode() == GrammarErrorCode.GRAMMAR_CHECK_FAILED
    }

    // ==================== 레벨 관련 예외 Tests ====================

    def "invalidLevel: 레벨과 허용값 포함"() {
        given:
        def level = "EXPERT"

        when:
        def exception = GrammarException.invalidLevel(level)

        then:
        exception.getMessage().contains(level)
        exception.getMessage().contains("BEGINNER")
        exception.getMessage().contains("INTERMEDIATE")
        exception.getMessage().contains("ADVANCED")
        exception.getErrorCode() == GrammarErrorCode.INVALID_LEVEL
    }

    // ==================== Bedrock API 관련 예외 Tests ====================

    def "bedrockApiError: cause 포함"() {
        given:
        def cause = new RuntimeException("Connection refused")

        when:
        def exception = GrammarException.bedrockApiError(cause)

        then:
        exception.getCause() == cause
        exception.getErrorCode() == GrammarErrorCode.BEDROCK_API_ERROR
        exception.getStatusCode() == 502
    }

    def "bedrockResponseParseError: 응답 포함"() {
        given:
        def response = "{ invalid json }"

        when:
        def exception = GrammarException.bedrockResponseParseError(response)

        then:
        exception.getMessage().contains("파싱")
        exception.getErrorCode() == GrammarErrorCode.BEDROCK_RESPONSE_PARSE_ERROR
    }

    // ==================== 세션 관련 예외 Tests ====================

    def "sessionNotFound: sessionId 포함"() {
        given:
        def sessionId = "session123"

        when:
        def exception = GrammarException.sessionNotFound(sessionId)

        then:
        exception.getMessage().contains(sessionId)
        exception.getErrorCode() == GrammarErrorCode.SESSION_NOT_FOUND
        exception.getStatusCode() == 404
    }

    def "sessionExpired: sessionId 포함"() {
        given:
        def sessionId = "expiredSession456"

        when:
        def exception = GrammarException.sessionExpired(sessionId)

        then:
        exception.getMessage().contains(sessionId)
        exception.getMessage().contains("만료")
        exception.getErrorCode() == GrammarErrorCode.SESSION_EXPIRED
    }
}
