package com.mzc.secondproject.serverless.domain.vocabulary.enums

import spock.lang.Specification
import spock.lang.Unroll

class WordStatusSpec extends Specification {

    // ==================== isValid Tests ====================

    @Unroll
    def "isValid: '#value' -> #expected"() {
        expect: "유효성 검사 결과가 예상과 일치"
        WordStatus.isValid(value) == expected

        where:
        value       | expected
        "NEW"       | true
        "LEARNING"  | true
        "REVIEWING" | true
        "MASTERED"  | true
        "UNKNOWN"   | true
        "new"       | true
        "learning"  | true
        "New"       | true
        "INVALID"   | false
        ""          | false
        null        | false
    }

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        when: "문자열로부터 WordStatus 변환"
        def result = WordStatus.fromString(value)

        then: "올바른 WordStatus 반환"
        result == expected

        where:
        value       | expected
        "NEW"       | WordStatus.NEW
        "new"       | WordStatus.NEW
        "LEARNING"  | WordStatus.LEARNING
        "learning"  | WordStatus.LEARNING
        "REVIEWING" | WordStatus.REVIEWING
        "reviewing" | WordStatus.REVIEWING
        "MASTERED"  | WordStatus.MASTERED
        "mastered"  | WordStatus.MASTERED
        "UNKNOWN"   | WordStatus.UNKNOWN
        "unknown"   | WordStatus.UNKNOWN
    }

    def "fromString: null 입력 시 IllegalArgumentException 발생"() {
        when: "null로 변환 시도"
        WordStatus.fromString(null)

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    def "fromString: 잘못된 값 입력 시 IllegalArgumentException 발생"() {
        when: "잘못된 값으로 변환 시도"
        WordStatus.fromString("INVALID")

        then: "예외 발생"
        thrown(IllegalArgumentException)
    }

    // ==================== fromStringOrDefault Tests ====================

    @Unroll
    def "fromStringOrDefault: '#value' with default #defaultValue -> #expected"() {
        expect: "기본값 처리 정상 동작"
        WordStatus.fromStringOrDefault(value, defaultValue) == expected

        where:
        value     | defaultValue         | expected
        "NEW"     | WordStatus.UNKNOWN   | WordStatus.NEW
        null      | WordStatus.NEW       | WordStatus.NEW
        "INVALID" | WordStatus.LEARNING  | WordStatus.LEARNING
        ""        | WordStatus.REVIEWING | WordStatus.REVIEWING
    }

    // ==================== Getter Tests ====================

    def "WordStatus 속성 정상 반환"() {
        expect: "각 상태의 code와 displayName 확인"
        WordStatus.NEW.getCode() == "new"
        WordStatus.NEW.getDisplayName() == "새 단어"

        WordStatus.LEARNING.getCode() == "learning"
        WordStatus.LEARNING.getDisplayName() == "학습 중"

        WordStatus.REVIEWING.getCode() == "reviewing"
        WordStatus.REVIEWING.getDisplayName() == "복습 중"

        WordStatus.MASTERED.getCode() == "mastered"
        WordStatus.MASTERED.getDisplayName() == "완료"

        WordStatus.UNKNOWN.getCode() == "unknown"
        WordStatus.UNKNOWN.getDisplayName() == "모르겠음"
    }

    def "모든 WordStatus 값 존재 확인"() {
        expect: "5개의 상태 존재"
        WordStatus.values().length == 5
    }
}
