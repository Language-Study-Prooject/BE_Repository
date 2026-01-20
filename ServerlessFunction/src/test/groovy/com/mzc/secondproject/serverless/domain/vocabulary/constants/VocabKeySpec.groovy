package com.mzc.secondproject.serverless.domain.vocabulary.constants

import spock.lang.Specification

class VocabKeySpec extends Specification {

    // ==================== Word Key Tests ====================

    def "wordPk: WORD# prefix 적용"() {
        expect:
        VocabKey.wordPk("word123") == "WORD#word123"
    }

    def "wordSk: WORD# prefix 적용"() {
        expect:
        VocabKey.wordSk("word456") == "WORD#word456"
    }

    // ==================== Daily Study Key Tests ====================

    def "dailyPk: DAILY# prefix 적용"() {
        expect:
        VocabKey.dailyPk("user123") == "DAILY#user123"
    }

    def "dateSk: DATE# prefix 적용"() {
        expect:
        VocabKey.dateSk("2026-01-20") == "DATE#2026-01-20"
    }

    // ==================== Level/Category Key Tests ====================

    def "levelPk: LEVEL# prefix 적용"() {
        expect:
        VocabKey.levelPk("BEGINNER") == "LEVEL#BEGINNER"
        VocabKey.levelPk("INTERMEDIATE") == "LEVEL#INTERMEDIATE"
        VocabKey.levelPk("ADVANCED") == "LEVEL#ADVANCED"
    }

    def "categoryPk: CATEGORY# prefix 적용"() {
        expect:
        VocabKey.categoryPk("TOEIC") == "CATEGORY#TOEIC"
    }

    def "statusSk: STATUS# prefix 적용"() {
        expect:
        VocabKey.statusSk("NEW") == "STATUS#NEW"
        VocabKey.statusSk("LEARNING") == "STATUS#LEARNING"
    }

    // ==================== User Key Tests ====================

    def "userReviewPk: USER#userId#REVIEW 형식"() {
        expect:
        VocabKey.userReviewPk("user123") == "USER#user123#REVIEW"
    }

    def "userStatusPk: USER#userId#STATUS 형식"() {
        expect:
        VocabKey.userStatusPk("user456") == "USER#user456#STATUS"
    }

    def "userGroupPk: USER#userId#GROUP 형식"() {
        expect:
        VocabKey.userGroupPk("user789") == "USER#user789#GROUP"
    }

    def "userBookmarkedPk: USER#userId#BOOKMARKED 형식"() {
        expect:
        VocabKey.userBookmarkedPk("user000") == "USER#user000#BOOKMARKED"
    }

    // ==================== Test Key Tests ====================

    def "testPk: TEST# prefix 적용"() {
        expect:
        VocabKey.testPk("test123") == "TEST#test123"
    }

    // ==================== Constants Tests ====================

    def "상수값 확인"() {
        expect:
        VocabKey.WORD == "WORD#"
        VocabKey.DAILY == "DAILY#"
        VocabKey.LEVEL == "LEVEL#"
        VocabKey.CATEGORY == "CATEGORY#"
        VocabKey.TEST == "TEST#"
        VocabKey.DATE == "DATE#"
        VocabKey.STATUS_PREFIX == "STATUS#"
        VocabKey.SUFFIX_REVIEW == "#REVIEW"
        VocabKey.SUFFIX_STATUS == "#STATUS"
        VocabKey.SUFFIX_GROUP == "#GROUP"
        VocabKey.SUFFIX_BOOKMARKED == "#BOOKMARKED"
        VocabKey.DAILY_ALL == "DAILY#ALL"
    }
}
