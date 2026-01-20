package com.mzc.secondproject.serverless.domain.vocabulary.exception

import spock.lang.Specification

class VocabularyExceptionSpec extends Specification {

    // ==================== Word 관련 예외 Tests ====================

    def "wordNotFound: 메시지에 wordId 포함"() {
        given:
        def wordId = "word123"

        when:
        def exception = VocabularyException.wordNotFound(wordId)

        then:
        exception.getMessage().contains(wordId)
        exception.getErrorCode() == VocabularyErrorCode.WORD_NOT_FOUND
    }

    def "wordAlreadyExists: 메시지에 영단어 포함"() {
        given:
        def english = "apple"

        when:
        def exception = VocabularyException.wordAlreadyExists(english)

        then:
        exception.getMessage().contains(english)
        exception.getErrorCode() == VocabularyErrorCode.WORD_ALREADY_EXISTS
    }

    def "invalidWordData: 사유 메시지 포함"() {
        given:
        def reason = "영단어는 필수입니다"

        when:
        def exception = VocabularyException.invalidWordData(reason)

        then:
        exception.getMessage() == reason
        exception.getErrorCode() == VocabularyErrorCode.INVALID_WORD_DATA
    }

    // ==================== UserWord 관련 예외 Tests ====================

    def "userWordNotFound: userId와 wordId 포함"() {
        given:
        def userId = "user123"
        def wordId = "word456"

        when:
        def exception = VocabularyException.userWordNotFound(userId, wordId)

        then:
        exception.getMessage().contains(userId)
        exception.getMessage().contains(wordId)
        exception.getErrorCode() == VocabularyErrorCode.USER_WORD_NOT_FOUND
    }

    def "invalidDifficulty: 잘못된 난이도 값 포함"() {
        given:
        def difficulty = "VERY_HARD"

        when:
        def exception = VocabularyException.invalidDifficulty(difficulty)

        then:
        exception.getMessage().contains(difficulty)
        exception.getMessage().contains("EASY")
        exception.getMessage().contains("NORMAL")
        exception.getMessage().contains("HARD")
        exception.getErrorCode() == VocabularyErrorCode.INVALID_DIFFICULTY
    }

    def "invalidWordStatus: 잘못된 상태 값 포함"() {
        given:
        def status = "INVALID_STATUS"

        when:
        def exception = VocabularyException.invalidWordStatus(status)

        then:
        exception.getMessage().contains(status)
        exception.getErrorCode() == VocabularyErrorCode.INVALID_WORD_STATUS
    }

    // ==================== Study 관련 예외 Tests ====================

    def "dailyStudyNotFound: userId와 date 포함"() {
        given:
        def userId = "user123"
        def date = "2026-01-20"

        when:
        def exception = VocabularyException.dailyStudyNotFound(userId, date)

        then:
        exception.getMessage().contains(userId)
        exception.getMessage().contains(date)
        exception.getErrorCode() == VocabularyErrorCode.DAILY_STUDY_NOT_FOUND
    }

    def "studyLimitExceeded: 한도 값 포함"() {
        given:
        def limit = 50

        when:
        def exception = VocabularyException.studyLimitExceeded(limit)

        then:
        exception.getMessage().contains("50")
        exception.getErrorCode() == VocabularyErrorCode.STUDY_LIMIT_EXCEEDED
    }

    def "invalidStudyLevel: 잘못된 레벨 값 포함"() {
        given:
        def level = "EXPERT"

        when:
        def exception = VocabularyException.invalidStudyLevel(level)

        then:
        exception.getMessage().contains(level)
        exception.getErrorCode() == VocabularyErrorCode.INVALID_STUDY_LEVEL
    }

    // ==================== Category/Level 관련 예외 Tests ====================

    def "invalidCategory: 잘못된 카테고리 값 포함"() {
        given:
        def category = "INVALID_CATEGORY"

        when:
        def exception = VocabularyException.invalidCategory(category)

        then:
        exception.getMessage().contains(category)
        exception.getErrorCode() == VocabularyErrorCode.INVALID_CATEGORY
    }

    def "invalidLevel: 잘못된 레벨 값 포함"() {
        given:
        def level = "UNKNOWN_LEVEL"

        when:
        def exception = VocabularyException.invalidLevel(level)

        then:
        exception.getMessage().contains(level)
        exception.getErrorCode() == VocabularyErrorCode.INVALID_LEVEL
    }

    // ==================== WordGroup 관련 예외 Tests ====================

    def "groupNotFound: groupId 포함"() {
        given:
        def groupId = "group123"

        when:
        def exception = VocabularyException.groupNotFound(groupId)

        then:
        exception.getMessage().contains(groupId)
        exception.getErrorCode() == VocabularyErrorCode.GROUP_NOT_FOUND
    }

    def "groupAlreadyExists: groupName 포함"() {
        given:
        def groupName = "My Vocabulary"

        when:
        def exception = VocabularyException.groupAlreadyExists(groupName)

        then:
        exception.getMessage().contains(groupName)
        exception.getErrorCode() == VocabularyErrorCode.GROUP_ALREADY_EXISTS
    }

    // ==================== Test 관련 예외 Tests ====================

    def "noWordsToTest: 적절한 메시지 포함"() {
        when:
        def exception = VocabularyException.noWordsToTest()

        then:
        exception.getMessage().contains("테스트할 단어가 없습니다")
        exception.getErrorCode() == VocabularyErrorCode.NO_WORDS_TO_TEST
    }
}
