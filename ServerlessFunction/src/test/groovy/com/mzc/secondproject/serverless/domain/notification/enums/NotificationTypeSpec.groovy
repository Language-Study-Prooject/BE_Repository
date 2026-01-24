package com.mzc.secondproject.serverless.domain.notification.enums

import spock.lang.Specification
import spock.lang.Unroll

class NotificationTypeSpec extends Specification {

    // ==================== Category Tests ====================

    @Unroll
    def "NotificationType '#type.name()' 카테고리: '#type.getCategory()'"() {
        expect: "카테고리별 알림 타입 분류 확인"
        type.getCategory() == expectedCategory

        where:
        type                               | expectedCategory
        NotificationType.BADGE_EARNED      | "badge"
        NotificationType.DAILY_COMPLETE    | "daily"
        NotificationType.STREAK_REMINDER   | "streak"
        NotificationType.TEST_COMPLETE     | "test"
        NotificationType.NEWS_QUIZ_COMPLETE| "quiz"
        NotificationType.GAME_END          | "game"
        NotificationType.GAME_STREAK       | "game"
        NotificationType.OPIC_COMPLETE     | "opic"
    }

    // ==================== Description Tests ====================

    @Unroll
    def "NotificationType '#type.name()' 설명: '#type.getDescription()'"() {
        expect: "알림 타입별 설명 확인"
        type.getDescription() == expectedDescription

        where:
        type                               | expectedDescription
        NotificationType.BADGE_EARNED      | "배지 획득"
        NotificationType.DAILY_COMPLETE    | "일일 학습 완료"
        NotificationType.STREAK_REMINDER   | "연속 학습 리마인더"
        NotificationType.TEST_COMPLETE     | "테스트 완료"
        NotificationType.NEWS_QUIZ_COMPLETE| "뉴스 퀴즈 완료"
        NotificationType.GAME_END          | "게임 종료"
        NotificationType.GAME_STREAK       | "게임 연속 정답"
        NotificationType.OPIC_COMPLETE     | "OPIc 세션 완료"
    }

    // ==================== All Types Tests ====================

    def "모든 NotificationType 개수 확인"() {
        expect: "8개의 알림 타입 존재"
        NotificationType.values().length == 8
    }

    def "모든 알림 타입은 description을 가짐"() {
        expect: "모든 타입의 description이 null이 아님"
        NotificationType.values().every { type ->
            type.getDescription() != null && !type.getDescription().isEmpty()
        }
    }

    def "모든 알림 타입은 category를 가짐"() {
        expect: "모든 타입의 category가 null이 아님"
        NotificationType.values().every { type ->
            type.getCategory() != null && !type.getCategory().isEmpty()
        }
    }

    // ==================== Category Grouping Tests ====================

    def "badge 카테고리 알림 타입 확인"() {
        expect:
        NotificationType.values().findAll { it.getCategory() == "badge" }.size() == 1
    }

    def "game 카테고리 알림 타입 확인"() {
        expect:
        NotificationType.values().findAll { it.getCategory() == "game" }.size() == 2
    }

    def "학습 관련 카테고리 (daily, streak) 확인"() {
        given:
        def learningCategories = ["daily", "streak"]

        expect:
        NotificationType.values().findAll { learningCategories.contains(it.getCategory()) }.size() == 2
    }

    def "테스트/퀴즈 관련 카테고리 (test, quiz) 확인"() {
        given:
        def testCategories = ["test", "quiz"]

        expect:
        NotificationType.values().findAll { testCategories.contains(it.getCategory()) }.size() == 2
    }

    // ==================== Enum Behavior Tests ====================

    def "valueOf: 유효한 이름으로 enum 조회"() {
        expect:
        NotificationType.valueOf("BADGE_EARNED") == NotificationType.BADGE_EARNED
        NotificationType.valueOf("STREAK_REMINDER") == NotificationType.STREAK_REMINDER
    }

    def "valueOf: 잘못된 이름으로 IllegalArgumentException 발생"() {
        when:
        NotificationType.valueOf("INVALID_TYPE")

        then:
        thrown(IllegalArgumentException)
    }
}
