package com.mzc.secondproject.serverless.common.enums

import spock.lang.Specification
import spock.lang.Unroll

class DifficultySpec extends Specification {

    // ==================== isValid Tests ====================

    @Unroll
    def "isValid: '#value' -> #expected"() {
        expect: "유효성 검사 결과가 예상과 일치"
        Difficulty.isValid(value) == expected

        where:
        value     | expected
        "EASY"    | true
        "NORMAL"  | true
        "HARD"    | true
        "easy"    | true
        "normal"  | true
        "hard"    | true
        "Easy"    | true
        "Normal"  | true
        "Hard"    | true
        "INVALID" | false
        ""        | false
        null      | false
    }

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        when: "문자열로부터 Difficulty 변환"
        def result = Difficulty.fromString(value)

        then: "올바른 Difficulty 반환"
        result == expected

        where:
        value    | expected
        "EASY"   | Difficulty.EASY
        "easy"   | Difficulty.EASY
        "NORMAL" | Difficulty.NORMAL
        "normal" | Difficulty.NORMAL
        "HARD"   | Difficulty.HARD
        "hard"   | Difficulty.HARD
    }

    def "fromString: null 입력 시 IllegalArgumentException 발생"() {
        when: "null로 변환 시도"
        Difficulty.fromString(null)

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    def "fromString: 잘못된 값 입력 시 IllegalArgumentException 발생"() {
        when: "잘못된 값으로 변환 시도"
        Difficulty.fromString("VERY_HARD")

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    // ==================== fromStringOrDefault Tests ====================

    @Unroll
    def "fromStringOrDefault: '#value' with default #defaultValue -> #expected"() {
        expect: "기본값 처리 정상 동작"
        Difficulty.fromStringOrDefault(value, defaultValue) == expected

        where:
        value     | defaultValue      | expected
        "EASY"    | Difficulty.HARD   | Difficulty.EASY
        null      | Difficulty.NORMAL | Difficulty.NORMAL
        "INVALID" | Difficulty.EASY   | Difficulty.EASY
        ""        | Difficulty.HARD   | Difficulty.HARD
    }

    // ==================== Getter Tests ====================

    def "Difficulty 속성 정상 반환"() {
        expect: "각 난이도의 code와 displayName 확인"
        Difficulty.EASY.getCode() == "easy"
        Difficulty.EASY.getDisplayName() == "쉬움"

        Difficulty.NORMAL.getCode() == "normal"
        Difficulty.NORMAL.getDisplayName() == "보통"

        Difficulty.HARD.getCode() == "hard"
        Difficulty.HARD.getDisplayName() == "어려움"
    }

    def "모든 Difficulty 값 존재 확인"() {
        expect: "3개의 난이도 존재"
        Difficulty.values().length == 3
    }
}
