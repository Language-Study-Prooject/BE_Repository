package com.mzc.secondproject.serverless.common.enums

import spock.lang.Specification
import spock.lang.Unroll

class StudyLevelSpec extends Specification {

    // ==================== isValid Tests ====================

    @Unroll
    def "isValid: '#value' -> #expected"() {
        expect: "유효성 검사 결과가 예상과 일치"
        StudyLevel.isValid(value) == expected

        where:
        value          | expected
        "BEGINNER"     | true
        "INTERMEDIATE" | true
        "ADVANCED"     | true
        "beginner"     | true
        "Beginner"     | true
        "INVALID"      | false
        ""             | false
        null           | false
    }

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        when: "문자열로부터 StudyLevel 변환"
        def result = StudyLevel.fromString(value)

        then: "올바른 StudyLevel 반환"
        result == expected

        where:
        value          | expected
        "BEGINNER"     | StudyLevel.BEGINNER
        "beginner"     | StudyLevel.BEGINNER
        "INTERMEDIATE" | StudyLevel.INTERMEDIATE
        "intermediate" | StudyLevel.INTERMEDIATE
        "ADVANCED"     | StudyLevel.ADVANCED
        "advanced"     | StudyLevel.ADVANCED
    }

    def "fromString: null 입력 시 IllegalArgumentException 발생"() {
        when: "null로 변환 시도"
        StudyLevel.fromString(null)

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    def "fromString: 잘못된 값 입력 시 IllegalArgumentException 발생"() {
        when: "잘못된 값으로 변환 시도"
        StudyLevel.fromString("INVALID")

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    // ==================== fromStringOrDefault Tests ====================

    @Unroll
    def "fromStringOrDefault: '#value' with default #defaultValue -> #expected"() {
        expect: "기본값 처리 정상 동작"
        StudyLevel.fromStringOrDefault(value, defaultValue) == expected

        where:
        value      | defaultValue            | expected
        "BEGINNER" | StudyLevel.ADVANCED     | StudyLevel.BEGINNER
        null       | StudyLevel.INTERMEDIATE | StudyLevel.INTERMEDIATE
        "INVALID"  | StudyLevel.BEGINNER     | StudyLevel.BEGINNER
        ""         | StudyLevel.ADVANCED     | StudyLevel.ADVANCED
    }

    // ==================== Getter Tests ====================

    def "StudyLevel 속성 정상 반환"() {
        expect: "각 레벨의 code와 displayName 확인"
        StudyLevel.BEGINNER.getCode() == "beginner"
        StudyLevel.BEGINNER.getDisplayName() == "초급"

        StudyLevel.INTERMEDIATE.getCode() == "intermediate"
        StudyLevel.INTERMEDIATE.getDisplayName() == "중급"

        StudyLevel.ADVANCED.getCode() == "advanced"
        StudyLevel.ADVANCED.getDisplayName() == "고급"
    }

    def "모든 StudyLevel 값 존재 확인"() {
        expect: "3개의 레벨 존재"
        StudyLevel.values().length == 3
    }
}
