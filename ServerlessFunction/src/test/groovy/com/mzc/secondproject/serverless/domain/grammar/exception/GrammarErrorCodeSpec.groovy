package com.mzc.secondproject.serverless.domain.grammar.exception

import spock.lang.Specification
import spock.lang.Unroll

class GrammarErrorCodeSpec extends Specification {

    def "모든 에러 코드의 도메인은 GRAMMAR"() {
        expect:
        GrammarErrorCode.values().every { it.getDomain() == "GRAMMAR" }
    }

    @Unroll
    def "에러 코드 '#errorCode': code=#expectedCode, statusCode=#expectedStatusCode"() {
        expect:
        errorCode.getCode() == expectedCode
        errorCode.getStatusCode() == expectedStatusCode
        errorCode.getMessage() != null
        !errorCode.getMessage().isEmpty()

        where:
        errorCode                                        | expectedCode     | expectedStatusCode
        GrammarErrorCode.INVALID_REQUEST                 | "GRAMMAR_000"    | 400
        GrammarErrorCode.INVALID_SENTENCE                | "GRAMMAR_001"    | 400
        GrammarErrorCode.GRAMMAR_CHECK_FAILED            | "GRAMMAR_002"    | 500
        GrammarErrorCode.INVALID_LEVEL                   | "GRAMMAR_003"    | 400
        GrammarErrorCode.BEDROCK_API_ERROR               | "GRAMMAR_004"    | 502
        GrammarErrorCode.BEDROCK_RESPONSE_PARSE_ERROR    | "GRAMMAR_005"    | 500
        GrammarErrorCode.SESSION_NOT_FOUND               | "GRAMMAR_006"    | 404
        GrammarErrorCode.SESSION_EXPIRED                 | "GRAMMAR_007"    | 410
    }

    def "모든 에러 코드 개수 확인"() {
        expect: "8개의 에러 코드 존재"
        GrammarErrorCode.values().length == 8
    }

    def "클라이언트 에러 확인 (4xx)"() {
        expect:
        GrammarErrorCode.INVALID_REQUEST.isClientError()
        GrammarErrorCode.INVALID_SENTENCE.isClientError()
        GrammarErrorCode.INVALID_LEVEL.isClientError()
        GrammarErrorCode.SESSION_NOT_FOUND.isClientError()
        GrammarErrorCode.SESSION_EXPIRED.isClientError()
    }

    def "서버 에러 확인 (5xx)"() {
        expect:
        GrammarErrorCode.GRAMMAR_CHECK_FAILED.isServerError()
        GrammarErrorCode.BEDROCK_API_ERROR.isServerError()
        GrammarErrorCode.BEDROCK_RESPONSE_PARSE_ERROR.isServerError()
    }
}
