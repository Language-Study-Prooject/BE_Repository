package com.mzc.secondproject.serverless.domain.badge.enums

import spock.lang.Specification
import spock.lang.Unroll

class BadgeTypeSpec extends Specification {

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        expect: "문자열로부터 BadgeType 변환"
        BadgeType.fromString(value) == expected

        where:
        value        | expected
        "FIRST_STEP" | BadgeType.FIRST_STEP
        "first_step" | BadgeType.FIRST_STEP
        "First_Step" | BadgeType.FIRST_STEP
        "STREAK_3"   | BadgeType.STREAK_3
        "STREAK_7"   | BadgeType.STREAK_7
        "STREAK_30"  | BadgeType.STREAK_30
        "WORDS_100"  | BadgeType.WORDS_100
        "WORDS_500"  | BadgeType.WORDS_500
        "WORDS_1000" | BadgeType.WORDS_1000
        null         | null
        "INVALID"    | null
        ""           | null
    }

    // ==================== Category Tests ====================

    @Unroll
    def "Badge '#badge.name()' 카테고리 '#badge.getCategory()' 확인"() {
        expect: "카테고리별 뱃지 분류 확인"
        badge.getCategory() == expectedCategory

        where:
        badge                     | expectedCategory
        BadgeType.FIRST_STEP      | "FIRST_STUDY"
        BadgeType.STREAK_3        | "STREAK"
        BadgeType.STREAK_7        | "STREAK"
        BadgeType.STREAK_30       | "STREAK"
        BadgeType.WORDS_100       | "WORDS_LEARNED"
        BadgeType.WORDS_500       | "WORDS_LEARNED"
        BadgeType.WORDS_1000      | "WORDS_LEARNED"
        BadgeType.PERFECT_SCORE   | "PERFECT_TEST"
        BadgeType.TEST_10         | "TESTS_COMPLETED"
        BadgeType.ACCURACY_90     | "ACCURACY"
        BadgeType.GAME_FIRST_PLAY | "GAMES_PLAYED"
        BadgeType.GAME_10_WINS    | "GAMES_WON"
        BadgeType.QUICK_GUESSER   | "QUICK_GUESSES"
        BadgeType.PERFECT_DRAWER  | "PERFECT_DRAWS"
        BadgeType.MASTER          | "ALL_BADGES"
    }

    // ==================== Threshold Tests ====================

    @Unroll
    def "Badge '#badge.name()' threshold #badge.getThreshold() 확인"() {
        expect: "임계값 확인"
        badge.getThreshold() == expectedThreshold

        where:
        badge                  | expectedThreshold
        BadgeType.FIRST_STEP   | 1
        BadgeType.STREAK_3     | 3
        BadgeType.STREAK_7     | 7
        BadgeType.STREAK_30    | 30
        BadgeType.WORDS_100    | 100
        BadgeType.WORDS_500    | 500
        BadgeType.WORDS_1000   | 1000
        BadgeType.TEST_10      | 10
        BadgeType.ACCURACY_90  | 90
        BadgeType.GAME_10_WINS | 10
    }

    // ==================== Property Tests ====================

    def "FIRST_STEP 뱃지 속성 확인"() {
        given:
        def badge = BadgeType.FIRST_STEP

        expect:
        badge.getName() == "첫 걸음"
        badge.getDescription() == "첫 학습을 완료했습니다"
        badge.getImageFile() == "first_step.png"
        badge.getImageUrl().contains("first_step.png")
    }

    def "STREAK 뱃지 속성 확인"() {
        expect:
        BadgeType.STREAK_3.getName() == "3일 연속 학습"
        BadgeType.STREAK_7.getName() == "일주일 연속 학습"
        BadgeType.STREAK_30.getName() == "한 달 연속 학습"
    }

    def "WORDS 뱃지 속성 확인"() {
        expect:
        BadgeType.WORDS_100.getName() == "단어 수집가"
        BadgeType.WORDS_500.getName() == "단어 전문가"
        BadgeType.WORDS_1000.getName() == "단어 마스터"
    }

    def "게임 관련 뱃지 속성 확인"() {
        expect:
        BadgeType.GAME_FIRST_PLAY.getName() == "첫 게임"
        BadgeType.GAME_10_WINS.getName() == "게임 10승"
        BadgeType.QUICK_GUESSER.getName() == "번개 정답"
        BadgeType.PERFECT_DRAWER.getName() == "완벽한 출제자"
    }

    def "모든 BadgeType 개수 확인"() {
        expect: "29개의 뱃지 타입 존재 (기본 15 + 뉴스 14)"
        BadgeType.values().length == 29
    }

    def "모든 뱃지의 imageUrl이 S3 URL 형식"() {
        expect: "모든 뱃지 이미지 URL이 S3 경로 포함"
        BadgeType.values().every { badge ->
            badge.getImageUrl().contains("s3.ap-northeast-2.amazonaws.com")
        }
    }
}
