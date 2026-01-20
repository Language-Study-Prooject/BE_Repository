package com.mzc.secondproject.serverless.domain.vocabulary.enums

import spock.lang.Specification
import spock.lang.Unroll

class TestTypeSpec extends Specification {

    // ==================== isValid Tests ====================

    @Unroll
    def "isValid: '#value' -> #expected"() {
        expect: "유효성 검사 결과가 예상과 일치"
        TestType.isValid(value) == expected

        where:
        value     | expected
        "DAILY"   | true
        "WEEKLY"  | true
        "CUSTOM"  | true
        "daily"   | true
        "weekly"  | true
        "custom"  | true
        "Daily"   | true
        "INVALID" | false
        ""        | false
        null      | false
    }

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        when: "문자열로부터 TestType 변환"
        def result = TestType.fromString(value)

        then: "올바른 TestType 반환"
        result == expected

        where:
        value    | expected
        "DAILY"  | TestType.DAILY
        "daily"  | TestType.DAILY
        "WEEKLY" | TestType.WEEKLY
        "weekly" | TestType.WEEKLY
        "CUSTOM" | TestType.CUSTOM
        "custom" | TestType.CUSTOM
    }

    def "fromString: null 입력 시 IllegalArgumentException 발생"() {
        when: "null로 변환 시도"
        TestType.fromString(null)

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    def "fromString: 잘못된 값 입력 시 IllegalArgumentException 발생"() {
        when: "잘못된 값으로 변환 시도"
        TestType.fromString("INVALID")

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    // ==================== fromStringOrDefault Tests ====================

    @Unroll
    def "fromStringOrDefault: '#value' with default #defaultValue -> #expected"() {
        expect: "기본값 처리 정상 동작"
        TestType.fromStringOrDefault(value, defaultValue) == expected

        where:
        value     | defaultValue    | expected
        "DAILY"   | TestType.WEEKLY | TestType.DAILY
        null      | TestType.DAILY  | TestType.DAILY
        "INVALID" | TestType.CUSTOM | TestType.CUSTOM
        ""        | TestType.WEEKLY | TestType.WEEKLY
    }

    // ==================== Getter Tests ====================

    def "TestType 속성 정상 반환"() {
        expect: "각 테스트 타입의 code와 displayName 확인"
        TestType.DAILY.getCode() == "daily"
        TestType.DAILY.getDisplayName() == "일일 테스트"

        TestType.WEEKLY.getCode() == "weekly"
        TestType.WEEKLY.getDisplayName() == "주간 테스트"

        TestType.CUSTOM.getCode() == "custom"
        TestType.CUSTOM.getDisplayName() == "사용자 지정 테스트"
    }

    def "모든 TestType 값 존재 확인"() {
        expect: "3개의 테스트 타입 존재"
        TestType.values().length == 3
    }
}
