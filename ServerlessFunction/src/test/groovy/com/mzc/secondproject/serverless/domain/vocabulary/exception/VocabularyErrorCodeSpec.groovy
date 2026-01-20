package com.mzc.secondproject.serverless.domain.vocabulary.exception

import spock.lang.Specification
import spock.lang.Unroll

class VocabularyErrorCodeSpec extends Specification {

    def "모든 에러 코드의 도메인은 VOCABULARY"() {
        expect: "모든 에러 코드의 도메인이 VOCABULARY"
        VocabularyErrorCode.values().every { it.getDomain() == "VOCABULARY" }
    }

    @Unroll
    def "에러 코드 '#errorCode': code=#expectedCode, statusCode=#expectedStatusCode"() {
        expect:
        errorCode.getCode() == expectedCode
        errorCode.getStatusCode() == expectedStatusCode
        errorCode.getMessage() != null
        !errorCode.getMessage().isEmpty()

        where:
        errorCode                                    | expectedCode     | expectedStatusCode
        VocabularyErrorCode.WORD_NOT_FOUND           | "WORD_001"       | 404
        VocabularyErrorCode.WORD_ALREADY_EXISTS      | "WORD_002"       | 409
        VocabularyErrorCode.INVALID_WORD_DATA        | "WORD_003"       | 400
        VocabularyErrorCode.USER_WORD_NOT_FOUND      | "USER_WORD_001"  | 404
        VocabularyErrorCode.INVALID_DIFFICULTY       | "USER_WORD_002"  | 400
        VocabularyErrorCode.INVALID_WORD_STATUS      | "USER_WORD_003"  | 400
        VocabularyErrorCode.DAILY_STUDY_NOT_FOUND    | "STUDY_001"      | 404
        VocabularyErrorCode.STUDY_LIMIT_EXCEEDED     | "STUDY_002"      | 400
        VocabularyErrorCode.INVALID_STUDY_LEVEL      | "STUDY_003"      | 400
        VocabularyErrorCode.INVALID_CATEGORY         | "CATEGORY_001"   | 400
        VocabularyErrorCode.INVALID_LEVEL            | "LEVEL_001"      | 400
        VocabularyErrorCode.GROUP_NOT_FOUND          | "GROUP_001"      | 404
        VocabularyErrorCode.GROUP_ALREADY_EXISTS     | "GROUP_002"      | 409
        VocabularyErrorCode.TEST_NOT_FOUND           | "TEST_001"       | 404
        VocabularyErrorCode.NO_WORDS_TO_TEST         | "TEST_002"       | 400
    }

    def "404 에러 코드들 확인"() {
        expect: "404 상태 코드를 가진 에러들"
        VocabularyErrorCode.WORD_NOT_FOUND.getStatusCode() == 404
        VocabularyErrorCode.USER_WORD_NOT_FOUND.getStatusCode() == 404
        VocabularyErrorCode.DAILY_STUDY_NOT_FOUND.getStatusCode() == 404
        VocabularyErrorCode.GROUP_NOT_FOUND.getStatusCode() == 404
        VocabularyErrorCode.TEST_NOT_FOUND.getStatusCode() == 404
    }

    def "409 에러 코드들 확인"() {
        expect: "409 상태 코드를 가진 에러들 (Conflict)"
        VocabularyErrorCode.WORD_ALREADY_EXISTS.getStatusCode() == 409
        VocabularyErrorCode.GROUP_ALREADY_EXISTS.getStatusCode() == 409
    }

    def "모든 에러 코드 개수 확인"() {
        expect: "15개의 에러 코드 존재"
        VocabularyErrorCode.values().length == 15
    }
}
