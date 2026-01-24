package com.mzc.secondproject.serverless.domain.grammar.enums

import spock.lang.Specification
import spock.lang.Unroll

class GrammarLevelSpec extends Specification {

    // ==================== isValid Tests ====================

    @Unroll
    def "isValid: '#value' -> #expected"() {
        expect:
        GrammarLevel.isValid(value) == expected

        where:
        value          | expected
        "BEGINNER"     | true
        "INTERMEDIATE" | true
        "ADVANCED"     | true
        "beginner"     | true
        "intermediate" | true
        "advanced"     | true
        "INVALID"      | false
        ""             | false
        null           | false
    }

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        when:
        def result = GrammarLevel.fromString(value)

        then:
        result == expected

        where:
        value          | expected
        "BEGINNER"     | GrammarLevel.BEGINNER
        "beginner"     | GrammarLevel.BEGINNER
        "INTERMEDIATE" | GrammarLevel.INTERMEDIATE
        "intermediate" | GrammarLevel.INTERMEDIATE
        "ADVANCED"     | GrammarLevel.ADVANCED
        "advanced"     | GrammarLevel.ADVANCED
    }

    def "fromString: null 입력 시 IllegalArgumentException 발생"() {
        when:
        GrammarLevel.fromString(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "fromString: 잘못된 값 입력 시 IllegalArgumentException 발생"() {
        when:
        GrammarLevel.fromString("INVALID")

        then:
        thrown(IllegalArgumentException)
    }

    // ==================== fromStringOrDefault Tests ====================

    @Unroll
    def "fromStringOrDefault: '#value' with default #defaultValue -> #expected"() {
        expect:
        GrammarLevel.fromStringOrDefault(value, defaultValue) == expected

        where:
        value      | defaultValue              | expected
        "BEGINNER" | GrammarLevel.ADVANCED     | GrammarLevel.BEGINNER
        null       | GrammarLevel.BEGINNER     | GrammarLevel.BEGINNER
        "INVALID"  | GrammarLevel.INTERMEDIATE | GrammarLevel.INTERMEDIATE
    }

    // ==================== Getter Tests ====================

    def "GrammarLevel 속성 정상 반환"() {
        expect:
        GrammarLevel.BEGINNER.getCode() == "beginner"
        GrammarLevel.BEGINNER.getDisplayName() == "초급"
        GrammarLevel.BEGINNER.getDescription() == "한국어 번역과 쉬운 설명 포함"

        GrammarLevel.INTERMEDIATE.getCode() == "intermediate"
        GrammarLevel.INTERMEDIATE.getDisplayName() == "중급"
        GrammarLevel.INTERMEDIATE.getDescription() == "영어 위주 설명"

        GrammarLevel.ADVANCED.getCode() == "advanced"
        GrammarLevel.ADVANCED.getDisplayName() == "고급"
        GrammarLevel.ADVANCED.getDescription() == "상세한 문법 규칙 설명"
    }

    def "모든 GrammarLevel 값 존재 확인"() {
        expect: "3개의 레벨 존재"
        GrammarLevel.values().length == 3
    }
}
