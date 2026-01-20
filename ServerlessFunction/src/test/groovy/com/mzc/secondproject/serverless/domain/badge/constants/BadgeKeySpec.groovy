package com.mzc.secondproject.serverless.domain.badge.constants

import spock.lang.Specification

class BadgeKeySpec extends Specification {

    // ==================== Key Builder Tests ====================

    def "userBadgePk: USER#userId#BADGE 형식"() {
        expect:
        BadgeKey.userBadgePk("user123") == "USER#user123#BADGE"
        BadgeKey.userBadgePk("testUser") == "USER#testUser#BADGE"
    }

    def "badgeSk: BADGE#badgeType 형식"() {
        expect:
        BadgeKey.badgeSk("FIRST_STEP") == "BADGE#FIRST_STEP"
        BadgeKey.badgeSk("STREAK_7") == "BADGE#STREAK_7"
        BadgeKey.badgeSk("WORDS_100") == "BADGE#WORDS_100"
    }

    def "earnedSk: EARNED#earnedAt 형식"() {
        expect:
        BadgeKey.earnedSk("2026-01-20T10:30:00Z") == "EARNED#2026-01-20T10:30:00Z"
    }

    // ==================== Constants Tests ====================

    def "BADGE_ALL 상수값 확인"() {
        expect:
        BadgeKey.BADGE_ALL == "BADGE#ALL"
    }

    // ==================== Consistency Tests ====================

    def "동일한 입력에 대해 동일한 키 생성"() {
        given:
        def userId = "consistentUser"

        when:
        def key1 = BadgeKey.userBadgePk(userId)
        def key2 = BadgeKey.userBadgePk(userId)

        then:
        key1 == key2
    }
}
